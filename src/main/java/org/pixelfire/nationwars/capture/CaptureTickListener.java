package org.pixelfire.nationwars.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.network.CitySyncHelper;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.network.SyncCheckpointStatePacket;
import org.pixelfire.nationwars.state.CaptureProgress;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.CheckpointStatus;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.war.WarScore;
import org.pixelfire.nationwars.war.WarTermination;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The main-thread capture tick. Attacker/defender classification is always relative
 * to a checkpoint's <em>current</em> {@code holderNationId}, not fixed original war sides — this is what
 * makes post-occupation-lock role reversal (the defender retaking their own checkpoints) fall out of the
 * same logic instead of needing a special case.
 */
public final class CaptureTickListener
{
    private final CaptureZoneTracker zoneTracker = new CaptureZoneTracker();
    private final CheckpointLockout lockout = new CheckpointLockout();
    private final CheckpointCosmeticEffect cosmeticEffect = new CheckpointCosmeticEffect();
    private final CheckpointBossBarTracker bossBarTracker = new CheckpointBossBarTracker();
    private int tickCounter;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        final MinecraftServer server = event.getServer();
        cosmeticEffect.tick(server);

        final int captureTickInterval = NationWarsConfig.CAPTURE_TICK_INTERVAL.get();
        if (++tickCounter < captureTickInterval)
        {
            return;
        }
        tickCounter = 0;

        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();
        final long currentTick = server.overworld().getGameTime();
        final double dtSeconds = captureTickInterval / 20.0;

        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            if (war.phase() != WarPhase.ACTIVE)
            {
                continue;
            }
            for (final UUID cityId : war.targetCityIds())
            {
                evaluateCity(server, registry, war, cityId, now, currentTick, dtSeconds);
            }
            checkTotalVictory(registry, war, now);
        }

        updateSiegeState(registry);
    }

    private void evaluateCity(final MinecraftServer server, final NationRegistry registry, final War war, final UUID cityId,
            final long now, final long currentTick, final double dtSeconds)
    {
        final City city = registry.cities().get(cityId);
        if (city == null || city.state() == CityState.DORMANT)
        {
            return;
        }
        if (city.state() == CityState.OCCUPIED && now < city.occupationLockUntil())
        {
            return;
        }

        for (final UUID checkpointId : city.checkpointIds())
        {
            evaluateCheckpoint(server, registry, war, checkpointId, now, currentTick, dtSeconds);
        }

        evaluateOccupation(server, registry, war, cityId, now);
    }

    /**
     * All target cities occupied → {@code ATTACKER_TOTAL_VICTORY}, moving the war to {@code SETTLEMENT}.
     * Re-fetches the war record since {@link #evaluateCity} may have just updated
     * {@code occupiedCityIds} this same pass.
     */
    private void checkTotalVictory(final NationRegistry registry, final War war, final long now)
    {
        final War current = registry.wars().get(war.warId());
        if (current == null || current.targetCityIds().isEmpty() || !current.occupiedCityIds().containsAll(current.targetCityIds()))
        {
            return;
        }
        WarTermination.conclude(registry, current, WarOutcome.ATTACKER_TOTAL_VICTORY, now);
    }

    private void evaluateCheckpoint(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID checkpointId, final long now, final long currentTick, final double dtSeconds)
    {
        final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
        if (checkpoint == null || checkpoint.status() == CheckpointStatus.SEALED || checkpoint.status() == CheckpointStatus.FROZEN)
        {
            if (checkpoint != null)
            {
                bossBarTracker.clear(checkpointId);
            }
            return;
        }
        final ServerLevel level = server.getLevel(checkpoint.dimension());
        if (level == null || !level.hasChunk(checkpoint.pos().getX() >> 4, checkpoint.pos().getZ() >> 4))
        {
            return;
        }

        if (applyUnloadedDecayCatchUp(registry, checkpoint, now, dtSeconds))
        {
            return;
        }

        final int attackers = countPresent(server, level, checkpoint, war, currentTick, true);
        final int defenders = countPresent(server, level, checkpoint, war, currentTick, false);

        final float newProgress = CaptureProgress.step(checkpoint.captureProgress(), attackers, defenders, dtSeconds,
                NationWarsConfig.BASE_CAPTURE_RATE.get(), NationWarsConfig.DEFENDER_RECOVERY_RATE.get(),
                NationWarsConfig.DECAY_RATE.get(), NationWarsConfig.ATTACKER_STACK_BONUS.get(), NationWarsConfig.ATTACKER_STACK_CAP.get());
        final CheckpointStatus newStatus = CaptureProgress.statusFor(attackers, defenders);

        if (newProgress >= 1.0f)
        {
            bossBarTracker.clear(checkpointId);
            flipCheckpoint(server, registry, war, checkpoint, currentTick);
        }
        else
        {
            registry.checkpoints().put(checkpointId, new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                    checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), newProgress, checkpoint.capturingNationId(),
                    newStatus, checkpoint.claimedChunks(), now, checkpoint.placedBy(), checkpoint.placedAt()));
            updateBossBar(registry, level, checkpoint, newStatus, newProgress);
            sendCheckpointStateSync(server, checkpoint, newProgress, newStatus);
        }
    }

    private void updateBossBar(final NationRegistry registry, final ServerLevel level, final Checkpoint checkpoint,
            final CheckpointStatus status, final float progress)
    {
        if (status == CheckpointStatus.HELD)
        {
            bossBarTracker.clear(checkpoint.checkpointId());
            return;
        }
        final City city = registry.cities().get(checkpoint.cityId());
        final double radius = NationWarsConfig.CAPTURE_RADIUS.get();
        final double height = NationWarsConfig.CAPTURE_ZONE_HEIGHT.get();
        final BlockPos pos = checkpoint.pos();
        final var box = new AABB(pos).inflate(radius, height, radius);
        final Set<ServerPlayer> playersInZone = new HashSet<>(level.getEntitiesOfClass(ServerPlayer.class, box));
        bossBarTracker.update(checkpoint.checkpointId(), city != null ? city.name() : "checkpoint", progress, playersInZone);
    }

    /**
     * Every 10 ticks while contested, to clients within 128 blocks, per the packet's own cadence in the
     * spec — a plain distance check against each online player rather than a spatial index, since this
     * only runs for checkpoints already known to be contested (a small fraction of all checkpoints).
     */
    private void sendCheckpointStateSync(final MinecraftServer server, final Checkpoint checkpoint, final float progress,
            final CheckpointStatus status)
    {
        if (status == CheckpointStatus.HELD)
        {
            return;
        }
        final var packet = SyncCheckpointStatePacket.of(new Checkpoint(checkpoint.checkpointId(),
                checkpoint.cityId(), checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), progress,
                checkpoint.capturingNationId(), status, checkpoint.claimedChunks(), checkpoint.lastEvaluatedTime(),
                checkpoint.placedBy(), checkpoint.placedAt()));
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (player.level().dimension().equals(checkpoint.dimension())
                    && player.blockPosition().distSqr(checkpoint.pos()) <= 128.0 * 128.0)
            {
                NationWarsNetwork.sendTo(player, packet);
            }
        }
    }

    /**
     * While a checkpoint's chunk was unloaded, nobody could possibly have been present in its
     * zone, so the whole elapsed gap decays in one step here rather than being silently skipped —
     * exactly equivalent to having ticked throughout, since decay is linear and monotonic toward zero.
     * A gap under twice the normal tick interval is treated as ordinary per-tick evaluation instead,
     * since the chunk was plausibly loaded continuously and this is just its regular cadence.
     *
     * @return true if a catch-up step was applied (the caller should skip normal evaluation this pass)
     */
    private boolean applyUnloadedDecayCatchUp(final NationRegistry registry, final Checkpoint checkpoint, final long now,
            final double normalDtSeconds)
    {
        final long elapsedMs = now - checkpoint.lastEvaluatedTime();
        final long normalIntervalMs = (long) (normalDtSeconds * 1000.0);
        if (elapsedMs <= normalIntervalMs * 2)
        {
            return false;
        }

        final float caughtUpProgress = CaptureProgress.step(checkpoint.captureProgress(), 0, 0, elapsedMs / 1000.0,
                NationWarsConfig.BASE_CAPTURE_RATE.get(), NationWarsConfig.DEFENDER_RECOVERY_RATE.get(),
                NationWarsConfig.DECAY_RATE.get(), NationWarsConfig.ATTACKER_STACK_BONUS.get(), NationWarsConfig.ATTACKER_STACK_CAP.get());
        registry.checkpoints().put(checkpoint.checkpointId(), new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), caughtUpProgress, checkpoint.capturingNationId(),
                caughtUpProgress <= 0f ? CheckpointStatus.HELD : checkpoint.status(), checkpoint.claimedChunks(), now,
                checkpoint.placedBy(), checkpoint.placedAt()));
        return true;
    }

    private int countPresent(final MinecraftServer server, final ServerLevel level, final Checkpoint checkpoint, final War war,
            final long currentTick, final boolean wantAttackers)
    {
        final double radius = NationWarsConfig.CAPTURE_RADIUS.get();
        final double height = NationWarsConfig.CAPTURE_ZONE_HEIGHT.get();
        final BlockPos pos = checkpoint.pos();
        final var box = new AABB(
                pos.getX() - radius, pos.getY() - height, pos.getZ() - radius,
                pos.getX() + radius, pos.getY() + height, pos.getZ() + radius);

        final Set<UUID> stillPresent = new HashSet<>();
        int count = 0;
        for (final ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box))
        {
            final double dx = player.getX() - pos.getX();
            final double dz = player.getZ() - pos.getZ();
            if (dx * dx + dz * dz > radius * radius)
            {
                continue;
            }
            if (player.isSpectator() || player.isDeadOrDying() || (player.isCreative() && !NationWarsConfig.CREATIVE_CAN_CAPTURE.get()))
            {
                continue;
            }
            stillPresent.add(player.getUUID());
            if (zoneTracker.recordPresence(checkpoint.checkpointId(), player.getUUID(), currentTick) < 20)
            {
                continue;
            }
            final var nation = OpacNations.nationOf(server, player);
            if (nation == null)
            {
                continue;
            }
            final boolean isAttackerRelativeToHolder = isOpposingCoalition(war, nation.nationId(), checkpoint.holderNationId());
            if (isAttackerRelativeToHolder == wantAttackers && belongsToEitherCoalition(war, nation.nationId()))
            {
                count++;
            }
        }
        zoneTracker.retainOnly(checkpoint.checkpointId(), stillPresent);
        return count;
    }

    private boolean belongsToEitherCoalition(final War war, final UUID nationId)
    {
        return war.attackers().members().contains(nationId) || war.defenders().members().contains(nationId);
    }

    private boolean isOpposingCoalition(final War war, final UUID nationId, final UUID relativeToNationId)
    {
        final boolean relativeIsAttacker = war.attackers().members().contains(relativeToNationId);
        final boolean nationIsAttacker = war.attackers().members().contains(nationId);
        final boolean nationIsDefender = war.defenders().members().contains(nationId);
        if (relativeIsAttacker)
        {
            return nationIsDefender;
        }
        return nationIsAttacker;
    }

    private void flipCheckpoint(final MinecraftServer server, final NationRegistry registry, final War war,
            final Checkpoint checkpoint, final long currentTick)
    {
        final UUID newHolder = strongestOpposingNation(server, checkpoint, war);
        if (newHolder == null)
        {
            return;
        }
        final UUID previousHolder = checkpoint.holderNationId();

        registry.checkpoints().put(checkpoint.checkpointId(), new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                checkpoint.dimension(), checkpoint.pos(), newHolder, 0f, null, CheckpointStatus.HELD,
                checkpoint.claimedChunks(), System.currentTimeMillis(), checkpoint.placedBy(), checkpoint.placedAt()));

        lockout.lock(checkpoint.checkpointId(), previousHolder, currentTick,
                NationWarsConfig.CHECKPOINT_LOCKOUT_SECONDS.get() * 20L);

        final ServerLevel level = server.getLevel(checkpoint.dimension());
        if (level != null)
        {
            cosmeticEffect.shatter(level, checkpoint.pos(), checkpoint.checkpointId(), checkpoint.cityId());
        }

        final City city = registry.cities().get(checkpoint.cityId());
        if (city != null)
        {
            final boolean retakenInDefence = war.defenders().members().contains(city.ownerNationId())
                    ? war.defenders().members().contains(newHolder)
                    : war.attackers().members().contains(newHolder);
            WarScore.award(registry, war.warId(), newHolder,
                    retakenInDefence ? NationWarsConfig.SCORE_CHECKPOINT_DEFENDED.get() : NationWarsConfig.SCORE_CHECKPOINT_CAPTURE.get());
        }

        final CompoundTag after = new CompoundTag();
        after.putUUID("newHolderNationId", newHolder);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", newHolder, ActorRole.SYSTEM, AuditSource.AUTO,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "checkpoint_captured"),
                List.of(checkpoint.checkpointId(), checkpoint.cityId()), new CompoundTag(), after, false));

        if (city != null)
        {
            CitySyncHelper.broadcast(server, registry, registry.cities().getOrDefault(city.cityId(), city));
        }
    }

    /**
     * Which opposing nation gets credit for the flip when multiple attacker nations are present: the one
     * with the most Ready-eligible players currently in the zone. Not specified by name in the spec.
     */
    private UUID strongestOpposingNation(final MinecraftServer server, final Checkpoint checkpoint, final War war)
    {
        final var counts = new HashMap<UUID, Integer>();
        final ServerLevel level = server.getLevel(checkpoint.dimension());
        if (level == null)
        {
            return null;
        }
        final double radius = NationWarsConfig.CAPTURE_RADIUS.get();
        final BlockPos pos = checkpoint.pos();
        final var box = new AABB(pos).inflate(radius, NationWarsConfig.CAPTURE_ZONE_HEIGHT.get(), radius);
        for (final ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box))
        {
            final var nation = OpacNations.nationOf(server, player);
            if (nation != null && isOpposingCoalition(war, nation.nationId(), checkpoint.holderNationId())
                    && belongsToEitherCoalition(war, nation.nationId()))
            {
                counts.merge(nation.nationId(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private void evaluateOccupation(final MinecraftServer server, final NationRegistry registry, final War war, final UUID cityId,
            final long now)
    {
        final City city = registry.cities().get(cityId);
        if (city == null)
        {
            return;
        }
        if (city.checkpointIds().isEmpty())
        {
            return;
        }

        // Roles are relative to which coalition actually owns this city, not fixed attacker/defender
        // labels — a counter-offensive can put an attacker-owned city under siege too.
        final boolean ownerIsDefender = war.defenders().members().contains(city.ownerNationId());
        boolean allHeldByHostiles = true;
        boolean allHeldByOwnerSide = true;
        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint == null || checkpoint.status() == CheckpointStatus.SEALED)
            {
                continue;
            }
            final boolean heldByAttacker = war.attackers().members().contains(checkpoint.holderNationId());
            final boolean heldByDefender = war.defenders().members().contains(checkpoint.holderNationId());
            final boolean heldByHostile = ownerIsDefender ? heldByAttacker : heldByDefender;
            final boolean heldByOwnerSide = ownerIsDefender ? heldByDefender : heldByAttacker;
            allHeldByHostiles &= heldByHostile;
            allHeldByOwnerSide &= heldByOwnerSide;
        }

        if (allHeldByHostiles && city.state() != CityState.OCCUPIED)
        {
            final UUID occupierPrimary = ownerIsDefender ? war.attackers().primaryNationId() : war.defenders().primaryNationId();
            occupyCity(server, registry, war, city, occupierPrimary, now);
        }
        else if (allHeldByOwnerSide && city.state() == CityState.OCCUPIED && now >= city.occupationLockUntil())
        {
            releaseOccupation(server, registry, war, city);
        }
    }

    private void occupyCity(final MinecraftServer server, final NationRegistry registry, final War war, final City city,
            final UUID occupierPrimary, final long now)
    {
        final boolean firstTimeThisWar = !war.occupiedCityIds().contains(city.cityId());
        final long lockUntil = now + NationWarsConfig.OCCUPATION_LOCK_DURATION_SECONDS.get() * 1000L;
        registry.stripedLocks().withLocks(() ->
        {
            registry.cities().put(city.cityId(), withOccupation(city, CityState.OCCUPIED, occupierPrimary,
                    now, lockUntil, city.dormantSince()));
            final War current = registry.wars().get(war.warId());
            if (current != null)
            {
                final Set<UUID> occupied = new HashSet<>(current.occupiedCityIds());
                occupied.add(city.cityId());
                registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                        current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                        Set.copyOf(occupied), current.warScore(), current.suspendedSince(), current.contestedTimeMs(),
                        current.settlementDeadline(), current.outcome(), current.memberTargetableAt()));
            }
        }, city.cityId(), war.warId());

        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint != null)
            {
                registry.checkpoints().put(checkpointId, new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                        checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), 0f, null, CheckpointStatus.FROZEN,
                        checkpoint.claimedChunks(), now, checkpoint.placedBy(), checkpoint.placedAt()));
            }
        }

        if (firstTimeThisWar)
        {
            WarScore.award(registry, war.warId(), occupierPrimary, NationWarsConfig.SCORE_CITY_OCCUPIED.get());
        }

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", occupierPrimary, ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_occupied"), List.of(city.cityId()),
                new CompoundTag(), new CompoundTag(), false));

        CitySyncHelper.broadcast(server, registry, registry.cities().get(city.cityId()));
    }

    private void releaseOccupation(final MinecraftServer server, final NationRegistry registry, final War war, final City city)
    {
        registry.stripedLocks().withLocks(() ->
        {
            registry.cities().put(city.cityId(), withOccupation(city, CityState.UNDER_SIEGE, null, 0L, 0L, city.dormantSince()));
            final War current = registry.wars().get(war.warId());
            if (current != null)
            {
                final Set<UUID> occupied = new HashSet<>(current.occupiedCityIds());
                occupied.remove(city.cityId());
                registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                        current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                        Set.copyOf(occupied), current.warScore(), current.suspendedSince(), current.contestedTimeMs(),
                        current.settlementDeadline(), current.outcome(), current.memberTargetableAt()));
            }
        }, city.cityId(), war.warId());

        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint != null && checkpoint.status() == CheckpointStatus.FROZEN)
            {
                registry.checkpoints().put(checkpointId, new Checkpoint(checkpoint.checkpointId(), checkpoint.cityId(),
                        checkpoint.dimension(), checkpoint.pos(), checkpoint.holderNationId(), 0f, null, CheckpointStatus.HELD,
                        checkpoint.claimedChunks(), System.currentTimeMillis(), checkpoint.placedBy(), checkpoint.placedAt()));
            }
        }

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_occupation_released"), List.of(city.cityId()),
                new CompoundTag(), new CompoundTag(), false));

        CitySyncHelper.broadcast(server, registry, registry.cities().get(city.cityId()));
    }

    /**
     * Keeps a city's {@code state} synced to whether an ACTIVE war currently targets it: {@code ACTIVE}
     * to {@code UNDER_SIEGE} and back, leaving {@code OCCUPIED}/{@code DORMANT} untouched.
     */
    private void updateSiegeState(final NationRegistry registry)
    {
        final Set<UUID> besieged = new HashSet<>();
        for (final War war : registry.wars().values())
        {
            if (war.phase() == WarPhase.ACTIVE)
            {
                besieged.addAll(war.targetCityIds());
            }
        }
        for (final City city : new ArrayList<>(registry.cities().values()))
        {
            if (city.state() == CityState.ACTIVE && besieged.contains(city.cityId()))
            {
                registry.stripedLocks().withLocks(() -> registry.cities().put(city.cityId(),
                        withOccupation(city, CityState.UNDER_SIEGE, null, 0L, 0L, city.dormantSince())), city.cityId());
            }
            else if (city.state() == CityState.UNDER_SIEGE && !besieged.contains(city.cityId()))
            {
                registry.stripedLocks().withLocks(() -> registry.cities().put(city.cityId(),
                        withOccupation(city, CityState.ACTIVE, null, 0L, 0L, city.dormantSince())), city.cityId());
            }
        }
    }

    private static City withOccupation(final City city, final CityState state, final UUID occupiedByNationId,
            final long occupiedSince, final long occupationLockUntil, final long dormantSince)
    {
        return new City(city.cityId(), city.name(), city.ownerNationId(), city.founderNationId(), city.dimension(),
                city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), state, occupiedByNationId,
                occupiedSince, occupationLockUntil, city.foundedAt(), city.lastTransferAt(), city.transferCount(),
                city.pendingDisbandAt(), dormantSince);
    }

    /**
     * Called by {@link org.pixelfire.nationwars.world.CheckpointBreakListener} so a manual swing at a
     * checkpoint during {@code UNDER_SIEGE}/{@code OCCUPIED} gets the same cosmetic effect a capture flip
     * does, instead of a real break.
     */
    public void triggerCosmeticBreak(final ServerLevel level, final Checkpoint checkpoint)
    {
        cosmeticEffect.shatter(level, checkpoint.pos(), checkpoint.checkpointId(), checkpoint.cityId());
    }
}

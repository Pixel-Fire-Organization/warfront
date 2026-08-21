package org.pixelfire.nationwars.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.compute.TickTimer;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.config.TierDefinition;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.Checkpoint;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityInvariants;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The periodic validation sweep: party existence, leader-vs-claim reassertion, invariants, sky columns of
 * loaded cities, and evasion-tracker orphan cleanup. Runs on the same {@code nationValidationInterval}
 * cadence as {@link CityDormancyListener}, which already owns the tier-minimum-breach half of the
 * {@code DORMANT} lifecycle — this sweep only adds the checks that half doesn't cover, rather than
 * duplicating it.
 *
 * <p>Every repair here is a live-registry mutation, so unlike {@link
 * org.pixelfire.nationwars.state.CityInvariants} (the pure comparisons this sweep is built on) none of
 * this runs off-thread; the spec's "planning runs off-thread" is satisfied in spirit at this scale
 * (a handful of cities, once every few minutes) without needing a worker-pool round trip for a check
 * this cheap.
 */
public final class ValidationSweepListener
{
    private final TickTimer perfTimer = new TickTimer(64);
    private int tickCounter;

    public TickTimer perfTimer()
    {
        return perfTimer;
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        final int intervalTicks = NationWarsConfig.NATION_VALIDATION_INTERVAL_SECONDS.get() * 20;
        if (++tickCounter < intervalTicks)
        {
            return;
        }
        tickCounter = 0;

        final long startNanos = System.nanoTime();
        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();

        for (final City city : new ArrayList<>(registry.cities().values()))
        {
            sweepCity(server, registry, city);
        }
        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            pruneDisbandedCoalitionMembers(server, registry, war);
        }
        pruneOrphanedEvasionTrackers(registry);
        perfTimer.record(System.nanoTime() - startNanos);
    }

    private void sweepCity(final MinecraftServer server, final NationRegistry registry, final City city)
    {
        if (city.state() == CityState.DORMANT)
        {
            return;
        }

        if (!OpacNations.nationExists(server, city.ownerNationId()))
        {
            markDormant(registry, city);
            return;
        }

        repairCheckpointSet(server, registry, city);
        reportTierBoundsIfViolated(registry, city);
        reportMinCoreDistanceIfViolated(registry, city);
        repairOccupationConsistencyIfViolated(registry, city);
        reportSkyColumnIfObstructed(server, city);
        reassertClaimsUnderCurrentLeader(server, registry, city);
    }

    private void markDormant(final NationRegistry registry, final City city)
    {
        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null && current.state() != CityState.DORMANT)
            {
                registry.cities().put(city.cityId(), new City(current.cityId(), current.name(), current.ownerNationId(),
                        current.founderNationId(), current.dimension(), current.corePos(), current.tier(), current.bankedPayment(),
                        current.checkpointIds(), CityState.DORMANT, current.occupiedByNationId(), current.occupiedSince(),
                        current.occupationLockUntil(), current.foundedAt(), current.lastTransferAt(), current.transferCount(),
                        current.pendingDisbandAt(), System.currentTimeMillis()));
            }
        }, city.cityId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_dormant_owner_disbanded"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void repairCheckpointSet(final MinecraftServer server, final NationRegistry registry, final City city)
    {
        final Set<UUID> actual = new HashSet<>();
        for (final Checkpoint checkpoint : registry.checkpoints().values())
        {
            if (checkpoint.cityId().equals(city.cityId()))
            {
                actual.add(checkpoint.checkpointId());
            }
        }
        if (CityInvariants.checkpointSetMatches(city.checkpointIds(), actual))
        {
            return;
        }

        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null)
            {
                registry.cities().put(city.cityId(), new City(current.cityId(), current.name(), current.ownerNationId(),
                        current.founderNationId(), current.dimension(), current.corePos(), current.tier(), current.bankedPayment(),
                        Set.copyOf(actual), current.state(), current.occupiedByNationId(), current.occupiedSince(),
                        current.occupationLockUntil(), current.foundedAt(), current.lastTransferAt(), current.transferCount(),
                        current.pendingDisbandAt(), current.dormantSince()));
            }
        }, city.cityId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_repair_checkpoint_set"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void reportTierBoundsIfViolated(final NationRegistry registry, final City city)
    {
        final long graceEndsAt = city.foundedAt() + NationWarsConfig.FOUNDING_GRACE_PERIOD_SECONDS.get() * 1000L;
        if (System.currentTimeMillis() < graceEndsAt)
        {
            return;
        }
        final TierDefinition tier = NationWarsConfig.tiers.get(city.tier());
        if (CityInvariants.withinTierBounds(city.checkpointIds().size(), tier.minCheckpoints(), tier.maxCheckpoints()))
        {
            return;
        }
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_violation_tier_bounds"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void reportMinCoreDistanceIfViolated(final NationRegistry registry, final City city)
    {
        double nearest = Double.MAX_VALUE;
        for (final City other : registry.cities().values())
        {
            if (other.cityId().equals(city.cityId()) || !other.dimension().equals(city.dimension()))
            {
                continue;
            }
            final var delta = city.corePos().subtract(other.corePos());
            nearest = Math.min(nearest, Math.sqrt(delta.getX() * (double) delta.getX() + delta.getZ() * (double) delta.getZ()));
        }
        if (nearest == Double.MAX_VALUE || CityInvariants.minCoreDistanceSatisfied(nearest, NationWarsConfig.effectiveMinCoreDistance))
        {
            return;
        }
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_violation_min_core_distance"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void repairOccupationConsistencyIfViolated(final NationRegistry registry, final City city)
    {
        final boolean warReferencesOccupation = registry.wars().values().stream()
                .anyMatch(war -> war.occupiedCityIds().contains(city.cityId()));
        if (CityInvariants.occupationConsistent(city.state(), city.occupiedByNationId(), warReferencesOccupation))
        {
            return;
        }

        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null && current.state() == CityState.OCCUPIED)
            {
                registry.cities().put(city.cityId(), new City(current.cityId(), current.name(), current.ownerNationId(),
                        current.founderNationId(), current.dimension(), current.corePos(), current.tier(), current.bankedPayment(),
                        current.checkpointIds(), CityState.UNDER_SIEGE, null, 0L, 0L, current.foundedAt(), current.lastTransferAt(),
                        current.transferCount(), current.pendingDisbandAt(), current.dormantSince()));
            }
        }, city.cityId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_repair_occupation_consistency"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    private void reportSkyColumnIfObstructed(final MinecraftServer server, final City city)
    {
        final ServerLevel level = server.getLevel(city.dimension());
        if (level == null || !level.isLoaded(city.corePos()) || SkyColumnScanner.isColumnClear(level, city.corePos()))
        {
            return;
        }
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", city.ownerNationId(), ActorRole.SYSTEM,
                AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "invariant_violation_sky_column_obstructed"),
                List.of(city.cityId()), new CompoundTag(), new CompoundTag(), false));
    }

    /**
     * Rather than tracking "did the leader change since last time" (which would need a new
     * field threaded through every {@code City} constructor call in the codebase), this just
     * re-asserts the claim under whoever the current leader is, every sweep. Harmless when nothing
     * changed — OPAC's claim call is idempotent — and self-correcting the moment it has changed.
     */
    private void reassertClaimsUnderCurrentLeader(final MinecraftServer server, final NationRegistry registry, final City city)
    {
        final UUID leaderUuid = OpacNations.leaderUuidOf(server, city.ownerNationId());
        if (leaderUuid == null)
        {
            return;
        }
        final ServerLevel level = server.getLevel(city.dimension());
        if (level == null)
        {
            return;
        }
        final Set<ChunkPos> chunks = new HashSet<>();
        final var coreShape = ClaimShape.parse(NationWarsConfig.CITY_CORE_CLAIM_SHAPE.get(), ClaimShape.PLUS);
        chunks.addAll(ClaimSetComputation.chunksFor(coreShape, new ChunkPos(city.corePos())));
        for (final UUID checkpointId : city.checkpointIds())
        {
            final Checkpoint checkpoint = registry.checkpoints().get(checkpointId);
            if (checkpoint != null)
            {
                chunks.addAll(checkpoint.claimedChunks());
            }
        }
        OpacNations.claimChunks(server, city.dimension().location(), leaderUuid, chunks);
    }

    /**
     * A nation whose party no longer exists is removed from any coalition it's still listed in.
     * The primary is never pruned here — if the primary itself is gone, {@link WarLifecycleListener}'s
     * existing {@code coalitionExists} check already ends the war {@code VOID} next pass, which is the
     * correct outcome for that case rather than a member-removal.
     */
    private void pruneDisbandedCoalitionMembers(final MinecraftServer server, final NationRegistry registry, final War war)
    {
        final Coalition prunedAttackers = pruneCoalition(server, war.attackers());
        final Coalition prunedDefenders = pruneCoalition(server, war.defenders());
        if (prunedAttackers == war.attackers() && prunedDefenders == war.defenders())
        {
            return;
        }
        registry.stripedLocks().withLocks(() -> registry.wars().put(war.warId(), new War(war.warId(), prunedAttackers, prunedDefenders,
                war.phase(), war.declaredAt(), war.activeAt(), war.warExpiresAt(), war.targetCityIds(), war.occupiedCityIds(),
                war.warScore(), war.suspendedSince(), war.contestedTimeMs(), war.settlementDeadline(), war.outcome(),
                war.memberTargetableAt())), war.warId());
    }

    private Coalition pruneCoalition(final MinecraftServer server, final Coalition coalition)
    {
        final Set<UUID> survivors = new HashSet<>();
        boolean changed = false;
        for (final UUID nationId : coalition.members())
        {
            if (nationId.equals(coalition.primaryNationId()) || OpacNations.nationExists(server, nationId))
            {
                survivors.add(nationId);
            }
            else
            {
                changed = true;
            }
        }
        return changed ? new Coalition(Set.copyOf(survivors), coalition.pendingMembers(), coalition.primaryNationId()) : coalition;
    }

    private void pruneOrphanedEvasionTrackers(final NationRegistry registry)
    {
        registry.evasionTrackers().keySet().removeIf(key -> !registry.wars().containsKey(key.warId()));
    }
}

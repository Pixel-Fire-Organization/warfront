package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.PlayerActivityState;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PendingEntry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.world.OpacNations;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the phase state machine off timers and readiness alone (no capture exists yet, so entering
 * {@code ACTIVE}/{@code SUSPENDED} is readiness-only): {@code PREPARATION} ends at {@code warPrepDuration}
 * into either state depending on readiness at that instant; {@code ACTIVE} degrades to {@code SUSPENDED}
 * after {@code presenceGraceDuration} of either side having no Ready player; {@code SUSPENDED} resumes to
 * {@code ACTIVE} the instant both sides are ready again, with no grace on the way back up.
 * {@code warExpiresAt} is checked every pass regardless of phase, since it never pauses.
 *
 * <p>Also drives cascaded-ally pending entry: a pending nation enters the moment any of its online
 * members has cleared the login shield, starting its own private {@code warPrepDuration} before its
 * cities become targetable, and is dropped unpaid if it never logs in within {@code pendingEntryExpiry}.
 */
public final class WarLifecycleListener
{
    private final Map<UUID, Long> readinessFailingSince = new ConcurrentHashMap<>();
    private int tickCounter;
    private int oneSecondCounter;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;
        oneSecondCounter++;

        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();

        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            if (war.phase() == WarPhase.ENDED || war.phase() == WarPhase.SETTLEMENT)
            {
                continue;
            }
            evaluatePendingEntries(server, registry, war, now);
            evaluateTargetability(registry, war, now);
            evaluatePhase(server, registry, war, now);

            if (oneSecondCounter % 30 == 0)
            {
                WarStateSync.broadcastWarAndCoalitions(server, registry.wars().getOrDefault(war.warId(), war));
            }
            if (oneSecondCounter % 60 == 0)
            {
                WarStateSync.sendWarScores(server, registry.wars().getOrDefault(war.warId(), war));
            }
        }
    }

    private void evaluatePhase(final MinecraftServer server, final NationRegistry registry, final War war, final long now)
    {
        // Re-fetch: pending-entry/targetability passes above may have already replaced this war's record.
        final War current = registry.wars().get(war.warId());
        if (current == null || current.phase() == WarPhase.ENDED || current.phase() == WarPhase.SETTLEMENT)
        {
            return;
        }
        if (!coalitionExists(server, current.attackers()) || !coalitionExists(server, current.defenders()))
        {
            readinessFailingSince.remove(current.warId());
            WarTermination.conclude(registry, current, WarOutcome.VOID, now);
            return;
        }
        if (now >= current.warExpiresAt())
        {
            readinessFailingSince.remove(current.warId());
            WarTermination.conclude(registry, current, WarOutcome.TIMEOUT, now);
            return;
        }

        final boolean bothReady = coalitionReady(server, current.attackers(), now) && coalitionReady(server, current.defenders(), now);

        if (current.phase() == WarPhase.PREPARATION)
        {
            if (now >= current.declaredAt() + NationWarsConfig.WAR_PREP_DURATION_SECONDS.get() * 1000L)
            {
                setPhase(server, registry, current, bothReady ? WarPhase.ACTIVE : WarPhase.SUSPENDED, now);
            }
            return;
        }

        if (current.phase() == WarPhase.ACTIVE)
        {
            if (bothReady)
            {
                readinessFailingSince.remove(current.warId());
                return;
            }
            final long failingSince = readinessFailingSince.computeIfAbsent(current.warId(), id -> now);
            if (now - failingSince >= NationWarsConfig.PRESENCE_GRACE_DURATION_SECONDS.get() * 1000L)
            {
                readinessFailingSince.remove(current.warId());
                setPhase(server, registry, current, WarPhase.SUSPENDED, now);
            }
            return;
        }

        if (current.phase() == WarPhase.SUSPENDED && bothReady)
        {
            setPhase(server, registry, current, WarPhase.ACTIVE, now);
        }
    }

    private boolean coalitionExists(final MinecraftServer server, final Coalition coalition)
    {
        return coalition.members().stream().anyMatch(nationId -> OpacNations.nationExists(server, nationId));
    }

    private boolean coalitionReady(final MinecraftServer server, final Coalition coalition, final long now)
    {
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        return coalition.members().stream()
                .anyMatch(nationId -> Readiness.isNationReady(server, nationId, tracker, currentTick, afkThresholdTicks));
    }

    private static void setPhase(final MinecraftServer server, final NationRegistry registry, final War war, final WarPhase phase,
            final long now)
    {
        final long activeAt = phase == WarPhase.ACTIVE && war.activeAt() == 0L ? now : war.activeAt();
        final long suspendedSince = phase == WarPhase.SUSPENDED ? now : 0L;
        registry.stripedLocks().withLocks(() -> registry.wars().put(war.warId(), new War(war.warId(), war.attackers(),
                war.defenders(), phase, war.declaredAt(), activeAt, war.warExpiresAt(), war.targetCityIds(),
                war.occupiedCityIds(), war.warScore(), suspendedSince, war.contestedTimeMs(), war.settlementDeadline(),
                war.outcome(), war.memberTargetableAt())), war.warId());
        WarStateSync.broadcastWarAndCoalitions(server, registry.wars().get(war.warId()));
    }

    private void evaluatePendingEntries(final MinecraftServer server, final NationRegistry registry, final War war, final long now)
    {
        final Coalition newAttackers = resolvePendingEntries(server, registry, war, war.attackers(), now);
        final Coalition newDefenders = resolvePendingEntries(server, registry, war, war.defenders(), now);
        if (newAttackers == war.attackers() && newDefenders == war.defenders())
        {
            return;
        }
        registry.stripedLocks().withLocks(() -> registry.wars().put(war.warId(), new War(war.warId(), newAttackers,
                newDefenders, war.phase(), war.declaredAt(), war.activeAt(), war.warExpiresAt(), war.targetCityIds(),
                war.occupiedCityIds(), war.warScore(), war.suspendedSince(), war.contestedTimeMs(), war.settlementDeadline(),
                war.outcome(), war.memberTargetableAt())), war.warId());
    }

    private Coalition resolvePendingEntries(final MinecraftServer server, final NationRegistry registry, final War war,
            final Coalition coalition, final long now)
    {
        if (coalition.pendingMembers().isEmpty())
        {
            return coalition;
        }
        final long pendingExpiryMillis = (NationWarsConfig.PENDING_ENTRY_EXPIRY_SECONDS.get() > 0
                ? NationWarsConfig.PENDING_ENTRY_EXPIRY_SECONDS.get() : NationWarsConfig.WAR_DURATION_SECONDS.get()) * 1000L;

        final Set<UUID> members = new HashSet<>(coalition.members());
        final Map<UUID, PendingEntry> pendingMembers = new HashMap<>(coalition.pendingMembers());
        boolean changed = false;

        for (final PendingEntry pending : List.copyOf(coalition.pendingMembers().values()))
        {
            if (now - pending.scheduledAt() >= pendingExpiryMillis)
            {
                pendingMembers.remove(pending.nationId());
                changed = true;
                continue;
            }
            if (hasClearedShield(server, pending.nationId(), now))
            {
                pendingMembers.remove(pending.nationId());
                members.add(pending.nationId());
                changed = true;
                notifyEntry(server, war, pending.nationId());
                markPrepWindow(registry, war, pending.nationId(), now);
            }
        }

        return changed ? new Coalition(Set.copyOf(members), Map.copyOf(pendingMembers), coalition.primaryNationId()) : coalition;
    }

    private boolean hasClearedShield(final MinecraftServer server, final UUID nationId, final long now)
    {
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        if (!OpacNations.nationExists(server, nationId))
        {
            return false;
        }
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId);
        return party != null && party.getOnlineMemberStream()
                .anyMatch(player -> tracker.stateOf(player.getUUID(), currentTick, afkThresholdTicks) != PlayerActivityState.SHIELDED);
    }

    private void notifyEntry(final MinecraftServer server, final War war, final UUID nationId)
    {
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId);
        if (party == null)
        {
            return;
        }
        final UUID broughtInBy = war.defenders().members().contains(nationId)
                ? war.defenders().primaryNationId() : war.attackers().primaryNationId();
        party.getOnlineMemberStream().forEach((ServerPlayer player) -> player.sendSystemMessage(Component.literal(
                "Your nation has been brought into a war (allied with " + broughtInBy + "). You have your own preparation window.")));

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", nationId, ActorRole.SYSTEM, AuditSource.AUTO,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "war_pending_entry"), List.of(war.warId(), nationId),
                new CompoundTag(), new CompoundTag(), false));
    }

    private void markPrepWindow(final NationRegistry registry, final War war, final UUID nationId, final long now)
    {
        final long targetableAt = now + NationWarsConfig.WAR_PREP_DURATION_SECONDS.get() * 1000L;
        registry.stripedLocks().withLocks(() ->
        {
            final War current = registry.wars().get(war.warId());
            if (current == null)
            {
                return;
            }
            final Map<UUID, Long> memberTargetableAt = new HashMap<>(current.memberTargetableAt());
            memberTargetableAt.put(nationId, targetableAt);
            registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                    current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                    current.occupiedCityIds(), current.warScore(), current.suspendedSince(), current.contestedTimeMs(),
                    current.settlementDeadline(), current.outcome(), Map.copyOf(memberTargetableAt)));
        }, war.warId());
    }

    private void evaluateTargetability(final NationRegistry registry, final War war, final long now)
    {
        if (war.memberTargetableAt().isEmpty())
        {
            return;
        }
        final Map<UUID, Long> remaining = new HashMap<>(war.memberTargetableAt());
        final Set<UUID> newlyTargetable = new HashSet<>();
        for (final Map.Entry<UUID, Long> entry : war.memberTargetableAt().entrySet())
        {
            if (now >= entry.getValue())
            {
                remaining.remove(entry.getKey());
                newlyTargetable.add(entry.getKey());
            }
        }
        if (newlyTargetable.isEmpty())
        {
            return;
        }

        final Set<UUID> targetCityIds = new HashSet<>(war.targetCityIds());
        for (final UUID nationId : newlyTargetable)
        {
            for (final City city : registry.cities().values())
            {
                if (city.ownerNationId().equals(nationId) && city.state() != CityState.DORMANT
                        && now >= city.occupationLockUntil())
                {
                    targetCityIds.add(city.cityId());
                }
            }
        }

        registry.stripedLocks().withLocks(() -> registry.wars().put(war.warId(), new War(war.warId(), war.attackers(),
                war.defenders(), war.phase(), war.declaredAt(), war.activeAt(), war.warExpiresAt(), Set.copyOf(targetCityIds),
                war.occupiedCityIds(), war.warScore(), war.suspendedSince(), war.contestedTimeMs(), war.settlementDeadline(),
                war.outcome(), Map.copyOf(remaining))), war.warId());
    }
}

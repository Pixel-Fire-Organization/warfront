package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.CounterOffensiveContext;
import org.pixelfire.nationwars.state.CounterOffensiveFailureReason;
import org.pixelfire.nationwars.state.CounterOffensivePreconditions;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /war counteroffensive}: turns a war two-front by putting every attacker-coalition nation's
 * eligible cities under {@code counterOffensivePrep} before they become targetable, reusing the exact
 * {@code War.memberTargetableAt} mechanism Stage 14 built for cascaded-ally private prep windows — the
 * deferred-targetability logic in {@link WarLifecycleListener} doesn't care why an entry is there.
 */
public final class CounterOffensiveService
{
    private CounterOffensiveService()
    {
    }

    public static Optional<CounterOffensiveFailureReason> declare(final MinecraftServer server, final War war)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();

        // Checked via memberTargetableAt rather than targetCityIds alone: right after commit(), attacker
        // cities sit in the prep window and haven't been added to targetCityIds yet, so relying only on
        // targetCityIds would let a second call reset the prep timer mid-window.
        final boolean alreadyCounterOffensive = war.attackers().members().stream()
                .anyMatch(nationId -> war.memberTargetableAt().containsKey(nationId) || hasAnyCityIn(registry, nationId, war.targetCityIds()));

        final long defenderScore = WarScore.totalFor(war, war.defenders().members());
        final long attackerScore = WarScore.totalFor(war, war.attackers().members());

        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        final boolean defenderReady = war.defenders().members().stream()
                .anyMatch(nationId -> Readiness.isNationReady(server, nationId, tracker, currentTick, afkThresholdTicks));

        final CounterOffensiveContext context = new CounterOffensiveContext(
                alreadyCounterOffensive, war.phase() == WarPhase.ACTIVE, war.occupiedCityIds().isEmpty(),
                defenderScore, attackerScore, NationWarsConfig.COUNTER_OFFENSIVE_SCORE_RATIO.get(),
                war.activeAt(), now, NationWarsConfig.COUNTER_OFFENSIVE_MIN_DURATION_SECONDS.get() * 1000L, defenderReady);

        final Optional<CounterOffensiveFailureReason> failure = CounterOffensivePreconditions.check(context);
        if (failure.isPresent())
        {
            return failure;
        }

        commit(registry, war, now);
        return Optional.empty();
    }

    private static boolean hasAnyCityIn(final NationRegistry registry, final UUID nationId, final Set<UUID> targetCityIds)
    {
        return registry.cities().values().stream()
                .anyMatch(city -> city.ownerNationId().equals(nationId) && targetCityIds.contains(city.cityId()));
    }

    private static void commit(final NationRegistry registry, final War war, final long now)
    {
        final long targetableAt = now + NationWarsConfig.COUNTER_OFFENSIVE_PREP_SECONDS.get() * 1000L;

        registry.stripedLocks().withLocks(() ->
        {
            final War current = registry.wars().get(war.warId());
            if (current == null)
            {
                return;
            }
            final Map<UUID, Long> memberTargetableAt = new HashMap<>(current.memberTargetableAt());
            for (final UUID attackerId : current.attackers().members())
            {
                memberTargetableAt.put(attackerId, targetableAt);
            }
            registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                    current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                    current.occupiedCityIds(), current.warScore(), current.suspendedSince(), current.contestedTimeMs(),
                    current.settlementDeadline(), current.outcome(), Map.copyOf(memberTargetableAt)));
        }, war.warId());

        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", war.defenders().primaryNationId(),
                ActorRole.LEADER, AuditSource.COMMAND, ResourceLocation.tryBuild(NationWarsMod.MODID, "war_counteroffensive"),
                List.of(war.warId()), new CompoundTag(), new CompoundTag(), false));
    }
}

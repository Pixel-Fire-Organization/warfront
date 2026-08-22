package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.EvasionKey;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared conclusion logic for every termination trigger that doesn't need the settlement pipeline
 * (timeout, withdrawal, disbandment-void, staff cancel). The white-peace rule — a war reaching
 * settlement with nothing occupied closes immediately, no lock, no negotiation — is exactly the "every
 * targeted city was successfully held" case, so that's also where the war-score bonus for holding a city
 * to the war's end is awarded. Any war ending with an occupation still standing locks into
 * {@code SETTLEMENT} instead, resolved by {@link org.pixelfire.nationwars.settlement.SettlementApplier}.
 */
public final class WarTermination
{
    private WarTermination()
    {
    }

    public static void conclude(final NationRegistry registry, final War war, final WarOutcome outcome, final long now)
    {
        final boolean whitePeace = war.occupiedCityIds().isEmpty();
        final WarPhase targetPhase = whitePeace ? WarPhase.ENDED : WarPhase.SETTLEMENT;
        final Set<UUID> allMembers = Stream.concat(war.attackers().members().stream(), war.defenders().members().stream())
                .collect(Collectors.toSet());

        registry.stripedLocks().withLocks(() ->
        {
            War scored = war;
            if (whitePeace)
            {
                // occupiedCityIds is empty, i.e. every targeted city was held the whole war.
                for (final UUID cityId : war.targetCityIds())
                {
                    final City city = registry.cities().get(cityId);
                    if (city != null)
                    {
                        scored = WarScore.applyAward(scored, city.ownerNationId(), NationWarsConfig.SCORE_CITY_HELD.get());
                    }
                }
            }

            // settlementWindow = 0 makes the lock indefinite, per spec — leave settlementDeadline at 0 then.
            final long settlementWindowSeconds = NationWarsConfig.SETTLEMENT_WINDOW_SECONDS.get();
            final long settlementDeadline = whitePeace || settlementWindowSeconds <= 0 ? 0L : now + settlementWindowSeconds * 1000L;

            registry.wars().put(war.warId(), new War(scored.warId(), scored.attackers(), scored.defenders(), targetPhase,
                    scored.declaredAt(), scored.activeAt(), scored.warExpiresAt(), scored.targetCityIds(), scored.occupiedCityIds(),
                    scored.warScore(), scored.suspendedSince(), scored.contestedTimeMs(), settlementDeadline, outcome,
                    scored.memberTargetableAt()));

            if (whitePeace)
            {
                closeOut(registry, war, outcome, now);
            }
            else
            {
                for (final UUID nationId : allMembers)
                {
                    final NationState current = registry.nationStates().getOrDefault(nationId, NationState.empty(nationId));
                    registry.nationStates().put(nationId, new NationState(nationId, current.cityIds(), current.capitalCityId(),
                            current.activeWarIds(), current.warCooldowns(), current.lastCityFoundedAt(), war.warId()));
                }
            }
        }, allMembers.toArray(UUID[]::new));

        for (final UUID nationId : allMembers)
        {
            registry.evasionTrackers().remove(new EvasionKey(war.warId(), nationId));
        }

        final CompoundTag after = new CompoundTag();
        after.putString("outcome", outcome.name());
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", war.attackers().primaryNationId(),
                ActorRole.SYSTEM, AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "war_concluded"),
                List.of(war.warId()), new CompoundTag(), after, false));
        NationWarsMod.get().forceSave();
    }

    private static void closeOut(final NationRegistry registry, final War war, final WarOutcome outcome, final long now)
    {
        final long cooldownHours = NationWarsConfig.DEFAULT_POST_WAR_COOLDOWN_HOURS.get()
                * (outcome == WarOutcome.ATTACKER_WITHDRAWAL ? 2 : 1);
        final long cooldownExpiresAt = now + cooldownHours * 3_600_000L;

        for (final UUID attackerId : war.attackers().members())
        {
            releaseNation(registry, attackerId, war.warId(), war.defenders().primaryNationId(), cooldownExpiresAt);
        }
        for (final UUID defenderId : war.defenders().members())
        {
            releaseNation(registry, defenderId, war.warId(), war.attackers().primaryNationId(), cooldownExpiresAt);
        }
    }

    /**
     * Clears one nation's {@code activeWarIds}/{@code lockedByWarId} entry for this war and sets its
     * cooldown against the opposing primary. Package-visible so {@link EvasionSurrenderService} can
     * reuse it for a single nation exiting its coalition without the rest of the war concluding.
     */
    static void releaseNation(final NationRegistry registry, final UUID nationId, final UUID warId,
            final UUID opponentPrimaryId, final long cooldownExpiresAt)
    {
        final NationState current = registry.nationStates().getOrDefault(nationId, NationState.empty(nationId));
        final Set<UUID> activeWarIds = new HashSet<>(current.activeWarIds());
        activeWarIds.remove(warId);
        final var warCooldowns = new HashMap<>(current.warCooldowns());
        warCooldowns.put(opponentPrimaryId, cooldownExpiresAt);
        final UUID lockedByWarId = warId.equals(current.lockedByWarId()) ? null : current.lockedByWarId();
        registry.nationStates().put(nationId, new NationState(nationId, current.cityIds(), current.capitalCityId(),
                Set.copyOf(activeWarIds), Map.copyOf(warCooldowns), current.lastCityFoundedAt(), lockedByWarId));
    }
}

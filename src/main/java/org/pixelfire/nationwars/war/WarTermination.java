package org.pixelfire.nationwars.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
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
 * settlement with nothing occupied closes immediately, no lock, no negotiation — is what every one of
 * these currently hits, since no capture exists yet to ever populate {@code occupiedCityIds}. The
 * {@code SETTLEMENT} branch is real code, just not yet reachable in practice.
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
            registry.wars().put(war.warId(), new War(war.warId(), war.attackers(), war.defenders(), targetPhase,
                    war.declaredAt(), war.activeAt(), war.warExpiresAt(), war.targetCityIds(), war.occupiedCityIds(),
                    war.warScore(), war.suspendedSince(), war.contestedTimeMs(), war.settlementDeadline(), outcome,
                    war.memberTargetableAt()));

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

        final CompoundTag after = new CompoundTag();
        after.putString("outcome", outcome.name());
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", war.attackers().primaryNationId(),
                ActorRole.SYSTEM, AuditSource.AUTO, ResourceLocation.tryBuild(NationWarsMod.MODID, "war_concluded"),
                List.of(war.warId()), new CompoundTag(), after, false));
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

    private static void releaseNation(final NationRegistry registry, final UUID nationId, final UUID warId,
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

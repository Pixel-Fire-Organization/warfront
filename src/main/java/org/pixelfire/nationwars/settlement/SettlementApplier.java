package org.pixelfire.nationwars.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.NationState;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Atomic settlement application. Every clause validates against live state before any of
 * them apply; a single failure aborts the whole settlement, naming the failing clause. There's no
 * negotiation window to hold a war open yet (Stage 19) — every settlement this stage can produce
 * (surrender, and later staff imposition) concludes the war immediately once its clauses commit.
 */
public final class SettlementApplier
{
    private SettlementApplier()
    {
    }

    public static Optional<String> apply(final MinecraftServer server, final NationRegistry registry, final War war,
            final List<StagedClause> clauses, final WarOutcome outcome, final boolean staffImposed)
    {
        final List<PeaceClause> resolved = new ArrayList<>();
        for (final StagedClause staged : clauses)
        {
            final PeaceClause clause = NationWarsMod.PEACE_CLAUSE_REGISTRY.get().getValue(staged.clauseTypeId());
            if (clause == null)
            {
                return Optional.of("unknown clause type " + staged.clauseTypeId());
            }
            final Optional<String> failure = clause.validate(registry, war, staged.params(), staffImposed);
            if (failure.isPresent())
            {
                return Optional.of(staged.clauseTypeId() + ": " + failure.get());
            }
            resolved.add(clause);
        }

        final ListTag clauseLog = new ListTag();
        final Set<UUID> affectedCityIds = new HashSet<>();

        registry.globalWriteLock().lock();
        try
        {
            for (int i = 0; i < clauses.size(); i++)
            {
                final StagedClause staged = clauses.get(i);
                clauseLog.add(logEntryFor(registry, staged, affectedCityIds));
                resolved.get(i).apply(registry, server, war, staged.params(), staffImposed);
            }
            releaseUncoveredOccupations(registry, server, war, clauses);
            finalizeWar(registry, war, outcome);
        }
        finally
        {
            registry.globalWriteLock().unlock();
        }

        final CompoundTag after = new CompoundTag();
        after.putString("outcome", outcome.name());
        after.putBoolean("staffImposed", staffImposed);
        after.put("clauses", clauseLog);
        final List<UUID> targets = new ArrayList<>();
        targets.add(war.warId());
        targets.addAll(affectedCityIds);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(null, "SYSTEM", war.attackers().primaryNationId(),
                ActorRole.SYSTEM, AuditSource.COMMAND, ResourceLocation.tryBuild(NationWarsMod.MODID, "settlement_applied"),
                targets, new CompoundTag(), after, true));
        return Optional.empty();
    }

    /**
     * One clause's replay record: its type, its own params, and — for {@code TransferCity} only — the
     * city's owner immediately before this clause applied, which a revert needs and nothing else in
     * {@code params} captures.
     */
    private static CompoundTag logEntryFor(final NationRegistry registry, final StagedClause staged, final Set<UUID> affectedCityIds)
    {
        final CompoundTag entry = new CompoundTag();
        entry.putString("clauseTypeId", staged.clauseTypeId().toString());
        entry.put("params", staged.params().copy());
        if (staged.clauseTypeId().equals(TransferCityClause.ID))
        {
            final UUID cityId = staged.params().getUUID("cityId");
            affectedCityIds.add(cityId);
            final City city = registry.cities().get(cityId);
            if (city != null)
            {
                entry.putUUID("previousOwnerNationId", city.ownerNationId());
            }
        }
        return entry;
    }

    /**
     * Any occupation the clause list doesn't address (no {@code TransferCity} or
     * {@code ReleaseOccupation} for it) is released rather than left dangling once the war ends.
     */
    private static void releaseUncoveredOccupations(final NationRegistry registry, final MinecraftServer server, final War war,
            final List<StagedClause> clauses)
    {
        final Set<UUID> addressed = new HashSet<>();
        for (final StagedClause staged : clauses)
        {
            if (staged.clauseTypeId().equals(TransferCityClause.ID) || staged.clauseTypeId().equals(ReleaseOccupationClause.ID))
            {
                addressed.add(staged.params().getUUID("cityId"));
            }
        }
        final War current = registry.wars().getOrDefault(war.warId(), war);
        final ReleaseOccupationClause fallbackRelease = new ReleaseOccupationClause();
        for (final UUID cityId : Set.copyOf(current.occupiedCityIds()))
        {
            if (!addressed.contains(cityId))
            {
                final CompoundTag params = new CompoundTag();
                params.putUUID("cityId", cityId);
                fallbackRelease.apply(registry, server, registry.wars().getOrDefault(war.warId(), current), params, false);
            }
        }
    }

    private static void finalizeWar(final NationRegistry registry, final War war, final WarOutcome outcome)
    {
        final War current = registry.wars().getOrDefault(war.warId(), war);
        final Set<UUID> allMembers = Stream.concat(current.attackers().members().stream(), current.defenders().members().stream())
                .collect(Collectors.toSet());
        for (final UUID nationId : allMembers)
        {
            final NationState state = registry.nationStates().getOrDefault(nationId, NationState.empty(nationId));
            final Set<UUID> activeWarIds = new HashSet<>(state.activeWarIds());
            activeWarIds.remove(war.warId());
            final UUID lockedByWarId = war.warId().equals(state.lockedByWarId()) ? null : state.lockedByWarId();
            registry.nationStates().put(nationId, new NationState(nationId, state.cityIds(), state.capitalCityId(),
                    Set.copyOf(activeWarIds), state.warCooldowns(), state.lastCityFoundedAt(), lockedByWarId));
        }

        registry.wars().put(war.warId(), new War(current.warId(), current.attackers(), current.defenders(), WarPhase.ENDED,
                current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(), current.occupiedCityIds(),
                current.warScore(), current.suspendedSince(), current.contestedTimeMs(), current.settlementDeadline(), outcome,
                current.memberTargetableAt()));
    }
}

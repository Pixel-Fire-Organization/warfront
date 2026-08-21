package org.pixelfire.nationwars.settlement;

import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceSettlement;
import org.pixelfire.nationwars.state.RatificationState;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The command-fallback negotiation flow: asynchronous, incrementally ratified. There is no
 * dedicated-packet GUI screen here — that's client rendering work (Stage 21); this is the server-
 * authoritative half the fallback commands already need to be complete on their own, so nothing here is
 * blocked on the screen existing.
 */
public final class NegotiationService
{
    private NegotiationService()
    {
    }

    public static City findCityByName(final NationRegistry registry, final String name)
    {
        return registry.cities().values().stream()
                .filter(city -> city.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /**
     * Every nation that must sign for a deal to apply: both coalition primaries, plus the leader of any
     * non-primary member whose city a {@code TransferCity} clause names — a primary negotiates for the
     * coalition but cannot sign away an ally's property.
     */
    public static Set<UUID> requiredSignatories(final NationRegistry registry, final War war, final List<StagedClause> clauses)
    {
        final Set<UUID> signatories = new HashSet<>();
        signatories.add(war.attackers().primaryNationId());
        signatories.add(war.defenders().primaryNationId());
        for (final StagedClause staged : clauses)
        {
            if (staged.clauseTypeId().equals(TransferCityClause.ID))
            {
                final City city = registry.cities().get(staged.params().getUUID("cityId"));
                if (city != null)
                {
                    signatories.add(city.ownerNationId());
                }
            }
        }
        return signatories;
    }

    public static Optional<String> send(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID proposingNationId, final NegotiationDraftTracker drafts)
    {
        final Optional<String> failure = send(server, registry, war, proposingNationId, drafts.get(war.warId(), proposingNationId));
        if (failure.isEmpty())
        {
            drafts.clear(war.warId(), proposingNationId);
        }
        return failure;
    }

    /**
     * The packet-native entry point ({@link org.pixelfire.nationwars.network.ProposeSettlementPacket}):
     * the client already holds its whole draft, so there's no tracker to read from or clear here — the
     * command fallback's {@link #send(MinecraftServer, NationRegistry, War, UUID, NegotiationDraftTracker)}
     * overload delegates to this one.
     */
    public static Optional<String> send(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID proposingNationId, final List<StagedClause> clauses)
    {
        if (clauses.isEmpty())
        {
            return Optional.of("Your draft is empty — use /war negotiate offer|demand|ceasefire first.");
        }
        final Set<UUID> signatories = requiredSignatories(registry, war, clauses);
        if (!signatories.contains(proposingNationId))
        {
            return Optional.of("Your nation isn't a required signatory for this offer.");
        }

        final PeaceSettlement previous = registry.settlements().get(war.warId());
        final int carriedRejectionCount = previous != null ? previous.rejectionCount() : 0;

        final Map<UUID, RatificationState> ratifications = new HashMap<>();
        for (final UUID nationId : signatories)
        {
            ratifications.put(nationId, nationId.equals(proposingNationId) ? RatificationState.SIGNED : RatificationState.PENDING);
        }

        final long now = System.currentTimeMillis();
        final PeaceSettlement settlement = new PeaceSettlement(UUID.randomUUID(), war.warId(), proposingNationId,
                List.copyOf(clauses), now, now + NationWarsConfig.OFFER_EXPIRY_SECONDS.get() * 1000L,
                Map.copyOf(ratifications), carriedRejectionCount);

        registry.settlements().put(war.warId(), settlement);
        SettlementSync.sendOpenDeal(server, settlement);
        return Optional.empty();
    }

    /**
     * @return empty on success (including full ratification and apply), or a failure message —
     *         including a validation failure surfaced from {@link SettlementApplier} rather than
     *         applying silently, if live state changed since the offer was drafted.
     */
    public static Optional<String> accept(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID nationId)
    {
        final PeaceSettlement settlement = registry.settlements().get(war.warId());
        if (settlement == null || settlement.anyRejected())
        {
            return Optional.of("There is no active offer for this war.");
        }
        if (now() >= settlement.expiresAt())
        {
            registry.settlements().remove(war.warId());
            return Optional.of("That offer has expired.");
        }
        if (!settlement.ratifications().containsKey(nationId))
        {
            return Optional.of("Your nation isn't a signatory on this offer.");
        }

        final Map<UUID, RatificationState> updated = new HashMap<>(settlement.ratifications());
        updated.put(nationId, RatificationState.SIGNED);
        final PeaceSettlement signed = new PeaceSettlement(settlement.settlementId(), settlement.warId(),
                settlement.proposedByNationId(), settlement.clauses(), settlement.createdAt(), settlement.expiresAt(),
                Map.copyOf(updated), settlement.rejectionCount());

        if (!signed.fullyRatified())
        {
            registry.settlements().put(war.warId(), signed);
            SettlementSync.sendProgress(server, signed);
            return Optional.empty();
        }

        final WarOutcome outcome = war.outcome() != null ? war.outcome() : WarOutcome.NEGOTIATED_PEACE;
        final Optional<String> failure = SettlementApplier.apply(server, registry, war, signed.clauses(), outcome, false);
        if (failure.isPresent())
        {
            return Optional.of("The offer no longer applies against live state and was voided: " + failure.get());
        }
        registry.settlements().remove(war.warId());
        return Optional.empty();
    }

    /**
     * Voids the current offer by marking it rejected rather than removing it outright — {@link #send}
     * still needs to read its {@code rejectionCount} to carry the deadlock-tracking count forward into
     * whatever offer replaces it.
     */
    public static Optional<String> reject(final MinecraftServer server, final NationRegistry registry, final War war,
            final UUID nationId)
    {
        final PeaceSettlement settlement = registry.settlements().get(war.warId());
        if (settlement == null || settlement.anyRejected())
        {
            return Optional.of("There is no active offer for this war.");
        }
        if (!settlement.ratifications().containsKey(nationId))
        {
            return Optional.of("Your nation isn't a signatory on this offer.");
        }
        final Map<UUID, RatificationState> updated = new HashMap<>(settlement.ratifications());
        updated.put(nationId, RatificationState.REJECTED);
        final PeaceSettlement rejected = new PeaceSettlement(settlement.settlementId(), settlement.warId(),
                settlement.proposedByNationId(), settlement.clauses(), settlement.createdAt(), settlement.expiresAt(),
                Map.copyOf(updated), settlement.rejectionCount() + 1);
        registry.settlements().put(war.warId(), rejected);
        SettlementSync.sendProgress(server, rejected);
        return Optional.empty();
    }

    private static long now()
    {
        return System.currentTimeMillis();
    }
}

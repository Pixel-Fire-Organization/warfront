package org.pixelfire.nationwars.war;

import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Awards war score to a nation for a single war. Only the events that
 * overlap capture/occupation are wired here — checkpoint captured/retaken and first-time city
 * occupation. "City held to war's end" and per-participation score need war-end and periodic Ready-time
 * tracking respectively, neither of which exists yet.
 */
public final class WarScore
{
    private WarScore()
    {
    }

    public static void award(final NationRegistry registry, final UUID warId, final UUID nationId, final long amount)
    {
        registry.stripedLocks().withLocks(() ->
        {
            final War current = registry.wars().get(warId);
            if (current == null)
            {
                return;
            }
            final Map<UUID, Long> scores = new HashMap<>(current.warScore());
            scores.merge(nationId, amount, Long::sum);
            registry.wars().put(warId, new War(current.warId(), current.attackers(), current.defenders(), current.phase(),
                    current.declaredAt(), current.activeAt(), current.warExpiresAt(), current.targetCityIds(),
                    current.occupiedCityIds(), Map.copyOf(scores), current.suspendedSince(), current.contestedTimeMs(),
                    current.settlementDeadline(), current.outcome(), current.memberTargetableAt()));
        }, warId);
    }

    public static long totalFor(final War war, final Iterable<UUID> nationIds)
    {
        long total = 0L;
        for (final UUID nationId : nationIds)
        {
            total += war.warScore().getOrDefault(nationId, 0L);
        }
        return total;
    }
}

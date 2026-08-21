package org.pixelfire.nationwars.war;

import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Awards war score to a nation for a single war. Only the events that overlap capture/occupation and war
 * conclusion are wired here — checkpoint captured/retaken, first-time city occupation, and holding a
 * targeted city to a white-peace war's end. Per-participation score needs periodic Ready-time tracking
 * that doesn't exist yet.
 */
public final class WarScore
{
    private WarScore()
    {
    }

    /**
     * Locks {@code warId}'s own stripe and applies the award. Not for use inside a block that's already
     * holding stripes from a {@link org.pixelfire.nationwars.state.NationRegistry#stripedLocks()} call —
     * re-entering with a fresh, differently-ordered stripe set from the same thread risks deadlocking
     * against a concurrent caller locking the same two stripes in the opposite order. Use
     * {@link #applyAward} directly from inside such a block instead.
     */
    public static void award(final NationRegistry registry, final UUID warId, final UUID nationId, final long amount)
    {
        registry.stripedLocks().withLocks(() ->
        {
            final War current = registry.wars().get(warId);
            if (current != null)
            {
                registry.wars().put(warId, applyAward(current, nationId, amount));
            }
        }, warId);
    }

    /**
     * The pure mutation {@link #award} wraps with locking — safe to call directly on a {@link War}
     * already held under the caller's own lock scope, and to feed straight into
     * {@code registry.wars().put(...)}.
     */
    public static War applyAward(final War war, final UUID nationId, final long amount)
    {
        final Map<UUID, Long> scores = new HashMap<>(war.warScore());
        scores.merge(nationId, amount, Long::sum);
        return new War(war.warId(), war.attackers(), war.defenders(), war.phase(), war.declaredAt(), war.activeAt(),
                war.warExpiresAt(), war.targetCityIds(), war.occupiedCityIds(), Map.copyOf(scores), war.suspendedSince(),
                war.contestedTimeMs(), war.settlementDeadline(), war.outcome(), war.memberTargetableAt());
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

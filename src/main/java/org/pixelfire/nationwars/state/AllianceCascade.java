package org.pixelfire.nationwars.state;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Breadth-first expansion from a target nation out to {@code allianceCascadeDepth} hops of mutual
 * alliance, never including the origin itself. Pure over an injected "mutual allies of" lookup so the
 * traversal bound is testable without OPAC.
 */
public final class AllianceCascade
{
    private AllianceCascade()
    {
    }

    public static Set<UUID> expand(final UUID origin, final int depth, final Function<UUID, Set<UUID>> mutualAlliesOf)
    {
        final Set<UUID> visited = new LinkedHashSet<>();
        visited.add(origin);
        Set<UUID> frontier = Set.of(origin);

        final Set<UUID> result = new LinkedHashSet<>();
        for (int hop = 0; hop < depth && !frontier.isEmpty(); hop++)
        {
            final Set<UUID> nextFrontier = new HashSet<>();
            for (final UUID nation : frontier)
            {
                for (final UUID ally : mutualAlliesOf.apply(nation))
                {
                    if (visited.add(ally))
                    {
                        nextFrontier.add(ally);
                        result.add(ally);
                    }
                }
            }
            frontier = nextFrontier;
        }
        return result;
    }
}

package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceCascadeTest
{
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    @Test
    void depthOneOnlyPullsDirectAllies()
    {
        final Map<UUID, Set<UUID>> graph = Map.of(A, Set.of(B, C), B, Set.of(D));

        final Set<UUID> result = AllianceCascade.expand(A, 1, id -> graph.getOrDefault(id, Set.of()));

        assertEquals(Set.of(B, C), result);
    }

    @Test
    void depthTwoPullsAlliesOfAllies()
    {
        final Map<UUID, Set<UUID>> graph = Map.of(A, Set.of(B), B, Set.of(D));

        final Set<UUID> result = AllianceCascade.expand(A, 2, id -> graph.getOrDefault(id, Set.of()));

        assertEquals(Set.of(B, D), result);
    }

    @Test
    void originIsNeverIncludedEvenIfCyclesExist()
    {
        final Map<UUID, Set<UUID>> graph = Map.of(A, Set.of(B), B, Set.of(A));

        final Set<UUID> result = AllianceCascade.expand(A, 5, id -> graph.getOrDefault(id, Set.of()));

        assertEquals(Set.of(B), result);
    }

    @Test
    void zeroDepthPullsNobody()
    {
        final Map<UUID, Set<UUID>> graph = Map.of(A, Set.of(B, C));

        assertTrue(AllianceCascade.expand(A, 0, id -> graph.getOrDefault(id, Set.of())).isEmpty());
    }

    @Test
    void noAlliesPullsNobody()
    {
        assertTrue(AllianceCascade.expand(A, 3, id -> Set.of()).isEmpty());
    }
}

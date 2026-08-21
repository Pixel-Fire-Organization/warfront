package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityInvariantsTest
{
    @Test
    void checkpointSetMatchesOnlyWhenIdentical()
    {
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        assertTrue(CityInvariants.checkpointSetMatches(Set.of(a, b), Set.of(a, b)));
        assertFalse(CityInvariants.checkpointSetMatches(Set.of(a, b), Set.of(a)));
        assertFalse(CityInvariants.checkpointSetMatches(Set.of(a), Set.of(a, b)));
    }

    @Test
    void tierBoundsAreInclusive()
    {
        assertTrue(CityInvariants.withinTierBounds(2, 2, 5));
        assertTrue(CityInvariants.withinTierBounds(5, 2, 5));
        assertFalse(CityInvariants.withinTierBounds(1, 2, 5));
        assertFalse(CityInvariants.withinTierBounds(6, 2, 5));
    }

    @Test
    void minCoreDistanceIsInclusive()
    {
        assertTrue(CityInvariants.minCoreDistanceSatisfied(192.0, 192.0));
        assertFalse(CityInvariants.minCoreDistanceSatisfied(191.9, 192.0));
    }

    @Test
    void occupationConsistentRequiresOccupierExactlyWhenOccupiedAndWarReference()
    {
        assertTrue(CityInvariants.occupationConsistent(CityState.ACTIVE, null, false));
        assertTrue(CityInvariants.occupationConsistent(CityState.OCCUPIED, UUID.randomUUID(), true));
        assertFalse(CityInvariants.occupationConsistent(CityState.OCCUPIED, null, true));
        assertFalse(CityInvariants.occupationConsistent(CityState.ACTIVE, UUID.randomUUID(), false));
        assertFalse(CityInvariants.occupationConsistent(CityState.OCCUPIED, UUID.randomUUID(), false));
    }
}

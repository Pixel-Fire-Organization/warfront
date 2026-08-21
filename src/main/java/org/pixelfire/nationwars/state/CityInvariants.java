package org.pixelfire.nationwars.state;

import java.util.Set;
import java.util.UUID;

/**
 * The invariant checks that reduce to a pure comparison over already-gathered facts. Each one
 * answers "is this specific invariant currently satisfied" — repairing a violation (which needs live
 * registry access) is the caller's job, in {@code ValidationSweepListener}.
 */
public final class CityInvariants
{
    private CityInvariants()
    {
    }

    public static boolean checkpointSetMatches(final Set<UUID> recorded, final Set<UUID> actual)
    {
        return recorded.equals(actual);
    }

    public static boolean withinTierBounds(final int checkpointCount, final int minCheckpoints, final int maxCheckpoints)
    {
        return checkpointCount >= minCheckpoints && checkpointCount <= maxCheckpoints;
    }

    public static boolean minCoreDistanceSatisfied(final double nearestOtherCoreDistance, final double minCoreDistance)
    {
        return nearestOtherCoreDistance >= minCoreDistance;
    }

    /**
     * {@code OCCUPIED ⟺ occupiedByNationId != null} and, only when occupied, an unsettled war actually
     * references the city as occupied.
     */
    public static boolean occupationConsistent(final CityState state, final UUID occupiedByNationId, final boolean warReferencesOccupation)
    {
        final boolean isOccupied = state == CityState.OCCUPIED;
        final boolean hasOccupier = occupiedByNationId != null;
        if (isOccupied != hasOccupier)
        {
            return false;
        }
        return !isOccupied || warReferencesOccupation;
    }
}

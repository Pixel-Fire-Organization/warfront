package org.pixelfire.nationwars.state;

public record CounterOffensiveContext(
        boolean alreadyCounterOffensive,
        boolean warActive,
        boolean defenderHasZeroOccupied,
        long defenderWarScore,
        long attackerWarScore,
        double counterOffensiveScoreRatio,
        long activeAt,
        long now,
        long counterOffensiveMinDurationMillis,
        boolean defenderWarReady)
{
}

package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundingPreconditionsTest
{
    private static final FoundingContext VALID = new FoundingContext(
            true, 0, 0, true, true, true,
            Double.MAX_VALUE, 192,
            0, 5, 1, 2,
            10_000L, 1800L,
            false, false, false, false);

    @Test
    void allPreconditionsMetFounds()
    {
        assertEquals(Optional.empty(), FoundingPreconditions.check(VALID));
    }

    @Test
    void notInANationFailsFirst()
    {
        final FoundingContext ctx = new FoundingContext(
                false, 0, 5, false, false, false,
                0.0, 192, 99, 0, 0, 0, 0L, 999L, true, true, true, false);

        assertEquals(Optional.of(FoundingFailureReason.NOT_IN_A_NATION), FoundingPreconditions.check(ctx));
    }

    @Test
    void rankTooLowIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 1, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.RANK_TOO_LOW), FoundingPreconditions.check(ctx));
    }

    @Test
    void dimensionIneligibleIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, false, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.DIMENSION_INELIGIBLE), FoundingPreconditions.check(ctx));
    }

    @Test
    void obstructedSkyColumnIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, false, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.SKY_COLUMN_OBSTRUCTED), FoundingPreconditions.check(ctx));
    }

    @Test
    void surfaceRequirementUnmetIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, false,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.SURFACE_REQUIREMENT_NOT_MET), FoundingPreconditions.check(ctx));
    }

    @Test
    void tooCloseToAnotherCoreIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                100.0, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.TOO_CLOSE_TO_ANOTHER_CORE), FoundingPreconditions.check(ctx));
    }

    @Test
    void exactlyAtMinCoreDistanceIsAllowed()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                192.0, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertTrue(FoundingPreconditions.check(ctx).isEmpty());
    }

    @Test
    void cityLimitPerNationIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 5, 5, 10, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.CITY_LIMIT_REACHED), FoundingPreconditions.check(ctx));
    }

    @Test
    void cityLimitPerMemberIsRejectedEvenBelowNationCap()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 2, 5, 1, 2, 10_000L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.CITY_LIMIT_REACHED), FoundingPreconditions.check(ctx));
    }

    @Test
    void cooldownStillActiveIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 100L, 1800L, false, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.FOUNDING_COOLDOWN_ACTIVE), FoundingPreconditions.check(ctx));
    }

    @Test
    void chunkClaimedByOtherNationIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, true, false, false, false);

        assertEquals(Optional.of(FoundingFailureReason.CHUNK_ALREADY_CLAIMED), FoundingPreconditions.check(ctx));
    }

    @Test
    void lockedNationIsRejected()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, true, false, false);

        assertEquals(Optional.of(FoundingFailureReason.NATION_LOCKED), FoundingPreconditions.check(ctx));
    }

    @Test
    void unsettledWarIsRejectedWhenNotAllowed()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, true, false);

        assertEquals(Optional.of(FoundingFailureReason.NATION_AT_WAR), FoundingPreconditions.check(ctx));
    }

    @Test
    void unsettledWarIsAllowedWhenConfigPermitsIt()
    {
        final FoundingContext ctx = new FoundingContext(
                true, 0, 0, true, true, true,
                Double.MAX_VALUE, 192, 0, 5, 1, 2, 10_000L, 1800L, false, false, true, true);

        assertTrue(FoundingPreconditions.check(ctx).isEmpty());
    }
}

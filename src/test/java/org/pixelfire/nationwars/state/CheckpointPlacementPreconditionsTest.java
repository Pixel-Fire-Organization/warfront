package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointPlacementPreconditionsTest
{
    private static final CheckpointPlacementContext VALID = new CheckpointPlacementContext(
            1, true, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

    @Test
    void allPreconditionsMetPlaces()
    {
        assertTrue(CheckpointPlacementPreconditions.check(VALID).isEmpty());
    }

    @Test
    void zeroMatchingCoresIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                0, false, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.NOT_WITHIN_A_CITYS_RADIUS), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void ambiguousOverlappingCoresIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                2, true, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.NOT_WITHIN_A_CITYS_RADIUS), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void notACitizenOrAllyIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, false, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.NOT_A_CITIZEN_OR_ALLY), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void rankTooLowIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 1, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.RANK_TOO_LOW), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void cityNotActiveIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, false, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.CITY_NOT_ACTIVE), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void obstructedSkyColumnIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, false, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.SKY_COLUMN_OBSTRUCTED), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void surfaceRequirementUnmetIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, true, false, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.SURFACE_REQUIREMENT_NOT_MET), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void checkpointLimitReachedIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, true, true, 5, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.CHECKPOINT_LIMIT_REACHED), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void tooCloseToAnotherCheckpointIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, true, true, 0, 5, 1.0, 3.0, 10.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.TOO_CLOSE_TO_ANOTHER_CHECKPOINT_OR_CORE), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void tooCloseToTheCoreIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 1.0, 3.0, false);

        assertEquals(Optional.of(CheckpointFailureReason.TOO_CLOSE_TO_ANOTHER_CHECKPOINT_OR_CORE), CheckpointPlacementPreconditions.check(ctx));
    }

    @Test
    void chunkClaimedByOtherNationIsRejected()
    {
        final CheckpointPlacementContext ctx = new CheckpointPlacementContext(
                1, true, 0, 0, true, true, true, 0, 5, Double.MAX_VALUE, 3.0, 10.0, 3.0, true);

        assertEquals(Optional.of(CheckpointFailureReason.CHUNK_ALREADY_CLAIMED), CheckpointPlacementPreconditions.check(ctx));
    }
}

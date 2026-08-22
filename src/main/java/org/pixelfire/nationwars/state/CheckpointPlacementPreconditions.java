package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * The eight checkpoint placement preconditions. See {@link CheckpointPlacementContext} for
 * why radius-resolution is checked before citizenship despite spec numbering them 3 and 1 respectively.
 */
public final class CheckpointPlacementPreconditions
{
    private CheckpointPlacementPreconditions()
    {
    }

    public static Optional<CheckpointFailureReason> check(final CheckpointPlacementContext ctx)
    {
        if (ctx.matchingCoreCount() != 1)
        {
            return Optional.of(CheckpointFailureReason.NOT_WITHIN_A_CITYS_RADIUS);
        }
        if (!ctx.citizenOrAllyOfMatchedCity())
        {
            return Optional.of(CheckpointFailureReason.NOT_A_CITIZEN_OR_ALLY);
        }
        if (ctx.memberRankOrdinal() < ctx.requiredRankOrdinal())
        {
            return Optional.of(CheckpointFailureReason.RANK_TOO_LOW);
        }
        if (!ctx.cityActive())
        {
            return Optional.of(CheckpointFailureReason.CITY_NOT_ACTIVE);
        }
        if (!ctx.skyColumnClear())
        {
            return Optional.of(CheckpointFailureReason.SKY_COLUMN_OBSTRUCTED);
        }
        if (!ctx.surfaceRequirementMet())
        {
            return Optional.of(CheckpointFailureReason.SURFACE_REQUIREMENT_NOT_MET);
        }
        if (ctx.existingCheckpointCount() >= ctx.maxCheckpointsForTier())
        {
            return Optional.of(CheckpointFailureReason.CHECKPOINT_LIMIT_REACHED);
        }
        if (ctx.nearestOtherCheckpointDistance() < ctx.minCheckpointSpacing() || ctx.coreDistance() < ctx.minCoreClearance())
        {
            return Optional.of(CheckpointFailureReason.TOO_CLOSE_TO_ANOTHER_CHECKPOINT_OR_CORE);
        }
        if (ctx.anyClaimChunkHeldByOtherNation())
        {
            return Optional.of(CheckpointFailureReason.CHUNK_ALREADY_CLAIMED);
        }
        return Optional.empty();
    }
}

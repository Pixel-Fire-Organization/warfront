package org.pixelfire.nationwars.state;

/**
 * Every input {@link CheckpointPlacementPreconditions#check} needs, snapshotted into primitives so the
 * precondition logic stays pure and world-free, same approach as {@link FoundingContext}.
 *
 * <p>Which city a placement targets is resolved by the caller (the unique city, of any nation, whose
 * tier radius contains the position) before this context is built, since none of the other checks are
 * meaningful without it. {@code matchingCoreCount} carries the result of that resolution: this context
 * doesn't itself repeat the precondition order for "citizen" (1) vs. "within radius" (3), because
 * the identity of "the city" has to exist before citizenship of its nation can even be asked.
 */
public record CheckpointPlacementContext(
        int matchingCoreCount,
        boolean citizenOrAllyOfMatchedCity,
        int memberRankOrdinal,
        int requiredRankOrdinal,
        boolean cityActive,
        boolean skyColumnClear,
        boolean surfaceRequirementMet,
        int existingCheckpointCount,
        int maxCheckpointsForTier,
        double nearestOtherCheckpointDistance,
        double minCheckpointSpacing,
        double coreDistance,
        double minCoreClearance,
        boolean anyClaimChunkHeldByOtherNation)
{
}

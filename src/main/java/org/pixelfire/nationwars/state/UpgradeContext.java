package org.pixelfire.nationwars.state;

/**
 * Every input {@link UpgradePreconditions#check} needs, snapshotted into primitives the same way
 * {@link FoundingContext} and {@link CheckpointPlacementContext} are.
 */
public record UpgradeContext(
        boolean hasNextTier,
        boolean cityActive,
        long bankedPayment,
        long nextTierCost,
        int checkpointCount,
        int currentTierMaximum,
        boolean expandedRadiusTooCloseToAnotherCity,
        boolean nationLocked,
        boolean nationAtWar,
        boolean allowUpgradeDuringWar)
{
}

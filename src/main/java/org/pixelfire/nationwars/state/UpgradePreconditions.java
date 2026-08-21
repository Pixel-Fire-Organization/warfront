package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * The tier upgrade preconditions, checked strictly in order so a rejection always names the first one
 * that actually failed.
 */
public final class UpgradePreconditions
{
    private UpgradePreconditions()
    {
    }

    public static Optional<UpgradeFailureReason> check(final UpgradeContext ctx)
    {
        if (!ctx.hasNextTier())
        {
            return Optional.of(UpgradeFailureReason.MAX_TIER_REACHED);
        }
        if (!ctx.cityActive())
        {
            return Optional.of(UpgradeFailureReason.CITY_NOT_ACTIVE);
        }
        if (ctx.bankedPayment() < ctx.nextTierCost())
        {
            return Optional.of(UpgradeFailureReason.INSUFFICIENT_BANKED_PAYMENT);
        }
        if (ctx.checkpointCount() < ctx.currentTierMaximum())
        {
            return Optional.of(UpgradeFailureReason.CHECKPOINTS_BELOW_TIER_MAXIMUM);
        }
        if (ctx.expandedRadiusTooCloseToAnotherCity())
        {
            return Optional.of(UpgradeFailureReason.EXPANDED_RADIUS_TOO_CLOSE_TO_ANOTHER_CITY);
        }
        if (ctx.nationLocked())
        {
            return Optional.of(UpgradeFailureReason.NATION_LOCKED);
        }
        if (ctx.nationAtWar() && !ctx.allowUpgradeDuringWar())
        {
            return Optional.of(UpgradeFailureReason.NATION_AT_WAR);
        }
        return Optional.empty();
    }
}

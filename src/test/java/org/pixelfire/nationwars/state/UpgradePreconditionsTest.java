package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradePreconditionsTest
{
    private static final UpgradeContext VALID = new UpgradeContext(
            true, true, 128L, 128L, 5, 5, false, false, false, false);

    @Test
    void allPreconditionsMetUpgrades()
    {
        assertTrue(UpgradePreconditions.check(VALID).isEmpty());
    }

    @Test
    void noNextTierIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(false, true, 128L, 128L, 5, 5, false, false, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.MAX_TIER_REACHED), UpgradePreconditions.check(ctx));
    }

    @Test
    void cityNotActiveIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(true, false, 128L, 128L, 5, 5, false, false, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.CITY_NOT_ACTIVE), UpgradePreconditions.check(ctx));
    }

    @Test
    void insufficientBankedPaymentIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 100L, 128L, 5, 5, false, false, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.INSUFFICIENT_BANKED_PAYMENT), UpgradePreconditions.check(ctx));
    }

    @Test
    void belowTierMaximumCheckpointsIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 128L, 128L, 3, 5, false, false, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.CHECKPOINTS_BELOW_TIER_MAXIMUM), UpgradePreconditions.check(ctx));
    }

    @Test
    void expandedRadiusTooCloseIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 128L, 128L, 5, 5, true, false, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.EXPANDED_RADIUS_TOO_CLOSE_TO_ANOTHER_CITY), UpgradePreconditions.check(ctx));
    }

    @Test
    void lockedNationIsRejected()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 128L, 128L, 5, 5, false, true, false, false);

        assertEquals(Optional.of(UpgradeFailureReason.NATION_LOCKED), UpgradePreconditions.check(ctx));
    }

    @Test
    void unsettledWarIsRejectedWhenNotAllowed()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 128L, 128L, 5, 5, false, false, true, false);

        assertEquals(Optional.of(UpgradeFailureReason.NATION_AT_WAR), UpgradePreconditions.check(ctx));
    }

    @Test
    void unsettledWarIsAllowedWhenConfigPermitsIt()
    {
        final UpgradeContext ctx = new UpgradeContext(true, true, 128L, 128L, 5, 5, false, false, true, true);

        assertTrue(UpgradePreconditions.check(ctx).isEmpty());
    }
}

package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarDeclarationPreconditionsTest
{
    private static final WarDeclarationContext VALID = new WarDeclarationContext(
            true, true, true, false, false, true, true, true, false, 10_000L, 0L, false, false, false, false, true);

    @Test
    void allChecksPassAllows()
    {
        assertTrue(WarDeclarationPreconditions.check(VALID).isEmpty());
    }

    @Test
    void notOwnerIsRejectedFirst()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                false, false, false, true, true, false, false, false, true, 0L, 999L, true, true, true, true, false);

        assertEquals(Optional.of(WarDeclarationFailureReason.NOT_NATION_OWNER), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void declarerWithNoCityIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, false, true, false, false, true, true, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.DECLARER_HAS_NO_CITY), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetNotFoundIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, false, false, false, true, true, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_NOT_FOUND), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetIsSelfIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, true, false, true, true, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_IS_SELF), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetIsMutualAllyIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, true, true, true, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_IS_MUTUAL_ALLY), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetHasNoEligibleCityIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, false, true, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_HAS_NO_ELIGIBLE_CITY), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetNotWarReadyIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, false, true, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_NOT_WAR_READY), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void declarerNotWarReadyIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, false, false, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.DECLARER_NOT_WAR_READY), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void existingUnsettledWarIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, true, 10_000L, 0L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.UNSETTLED_WAR_ALREADY_EXISTS), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void cooldownActiveIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 100L, 200L, false, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.COOLDOWN_ACTIVE), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void declarerLockedIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 10_000L, 0L, true, false, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.DECLARER_LOCKED), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetLockedIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 10_000L, 0L, false, true, false, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_LOCKED), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void declarerAtWarCapIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 10_000L, 0L, false, false, true, false, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.DECLARER_AT_WAR_CAP), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void targetAtWarCapIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 10_000L, 0L, false, false, false, true, true);

        assertEquals(Optional.of(WarDeclarationFailureReason.TARGET_AT_WAR_CAP), WarDeclarationPreconditions.check(ctx));
    }

    @Test
    void outsideWarWindowIsRejected()
    {
        final WarDeclarationContext ctx = new WarDeclarationContext(
                true, true, true, false, false, true, true, true, false, 10_000L, 0L, false, false, false, false, false);

        assertEquals(Optional.of(WarDeclarationFailureReason.OUTSIDE_WAR_WINDOW), WarDeclarationPreconditions.check(ctx));
    }
}

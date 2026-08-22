package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarJoinPreconditionsTest
{
    private static final WarJoinContext VALID = new WarJoinContext(true, false, true, false, 10_000L, 0L, false, false);

    @Test
    void allChecksPassAllows()
    {
        assertTrue(WarJoinPreconditions.check(VALID).isEmpty());
    }

    @Test
    void notOwnerIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(false, false, true, false, 10_000L, 0L, false, false);

        assertEquals(Optional.of(WarJoinFailureReason.NOT_NATION_OWNER), WarJoinPreconditions.check(ctx));
    }

    @Test
    void alreadyInThisWarIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, true, true, false, 10_000L, 0L, false, false);

        assertEquals(Optional.of(WarJoinFailureReason.ALREADY_IN_THIS_WAR), WarJoinPreconditions.check(ctx));
    }

    @Test
    void notJoinableIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, false, false, false, 10_000L, 0L, false, false);

        assertEquals(Optional.of(WarJoinFailureReason.WAR_NOT_JOINABLE), WarJoinPreconditions.check(ctx));
    }

    @Test
    void existingUnsettledWarIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, false, true, true, 10_000L, 0L, false, false);

        assertEquals(Optional.of(WarJoinFailureReason.UNSETTLED_WAR_ALREADY_EXISTS), WarJoinPreconditions.check(ctx));
    }

    @Test
    void cooldownActiveIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, false, true, false, 100L, 200L, false, false);

        assertEquals(Optional.of(WarJoinFailureReason.COOLDOWN_ACTIVE), WarJoinPreconditions.check(ctx));
    }

    @Test
    void lockedIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, false, true, false, 10_000L, 0L, true, false);

        assertEquals(Optional.of(WarJoinFailureReason.JOINER_LOCKED), WarJoinPreconditions.check(ctx));
    }

    @Test
    void atWarCapIsRejected()
    {
        final WarJoinContext ctx = new WarJoinContext(true, false, true, false, 10_000L, 0L, false, true);

        assertEquals(Optional.of(WarJoinFailureReason.JOINER_AT_WAR_CAP), WarJoinPreconditions.check(ctx));
    }
}

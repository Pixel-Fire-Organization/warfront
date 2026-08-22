package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterOffensivePreconditionsTest
{
    private static final CounterOffensiveContext VALID = new CounterOffensiveContext(
            false, true, true, 100L, 100L, 1.0, 0L, 100_000L, 50_000L, true);

    @Test
    void allConditionsMetAllows()
    {
        assertTrue(CounterOffensivePreconditions.check(VALID).isEmpty());
    }

    @Test
    void alreadyCounterOffensiveIsRejectedFirst()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                true, false, false, 0L, 100L, 1.0, 0L, 0L, 50_000L, false);

        assertEquals(Optional.of(CounterOffensiveFailureReason.ALREADY_COUNTER_OFFENSIVE), CounterOffensivePreconditions.check(ctx));
    }

    @Test
    void warNotActiveIsRejected()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                false, false, true, 100L, 100L, 1.0, 0L, 100_000L, 50_000L, true);

        assertEquals(Optional.of(CounterOffensiveFailureReason.WAR_NOT_ACTIVE), CounterOffensivePreconditions.check(ctx));
    }

    @Test
    void defenderStillOccupiedIsRejected()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                false, true, false, 100L, 100L, 1.0, 0L, 100_000L, 50_000L, true);

        assertEquals(Optional.of(CounterOffensiveFailureReason.DEFENDER_STILL_OCCUPIED), CounterOffensivePreconditions.check(ctx));
    }

    @Test
    void insufficientWarScoreIsRejected()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                false, true, true, 50L, 100L, 1.0, 0L, 100_000L, 50_000L, true);

        assertEquals(Optional.of(CounterOffensiveFailureReason.INSUFFICIENT_WAR_SCORE), CounterOffensivePreconditions.check(ctx));
    }

    @Test
    void notActiveLongEnoughIsRejected()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                false, true, true, 100L, 100L, 1.0, 0L, 10_000L, 50_000L, true);

        assertEquals(Optional.of(CounterOffensiveFailureReason.WAR_NOT_ACTIVE_LONG_ENOUGH), CounterOffensivePreconditions.check(ctx));
    }

    @Test
    void defenderNotWarReadyIsRejected()
    {
        final CounterOffensiveContext ctx = new CounterOffensiveContext(
                false, true, true, 100L, 100L, 1.0, 0L, 100_000L, 50_000L, false);

        assertEquals(Optional.of(CounterOffensiveFailureReason.DEFENDER_NOT_WAR_READY), CounterOffensivePreconditions.check(ctx));
    }
}

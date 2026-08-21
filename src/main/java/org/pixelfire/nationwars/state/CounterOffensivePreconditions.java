package org.pixelfire.nationwars.state;

import java.util.Optional;

/**
 * The five {@code /war counteroffensive} conditions, checked strictly in order, plus a structural
 * "not already turned" check ahead of them — a war can only flip to two-front once.
 */
public final class CounterOffensivePreconditions
{
    private CounterOffensivePreconditions()
    {
    }

    public static Optional<CounterOffensiveFailureReason> check(final CounterOffensiveContext ctx)
    {
        if (ctx.alreadyCounterOffensive())
        {
            return Optional.of(CounterOffensiveFailureReason.ALREADY_COUNTER_OFFENSIVE);
        }
        if (!ctx.warActive())
        {
            return Optional.of(CounterOffensiveFailureReason.WAR_NOT_ACTIVE);
        }
        if (!ctx.defenderHasZeroOccupied())
        {
            return Optional.of(CounterOffensiveFailureReason.DEFENDER_STILL_OCCUPIED);
        }
        if (ctx.defenderWarScore() < ctx.attackerWarScore() * ctx.counterOffensiveScoreRatio())
        {
            return Optional.of(CounterOffensiveFailureReason.INSUFFICIENT_WAR_SCORE);
        }
        if (ctx.now() - ctx.activeAt() < ctx.counterOffensiveMinDurationMillis())
        {
            return Optional.of(CounterOffensiveFailureReason.WAR_NOT_ACTIVE_LONG_ENOUGH);
        }
        if (!ctx.defenderWarReady())
        {
            return Optional.of(CounterOffensiveFailureReason.DEFENDER_NOT_WAR_READY);
        }
        return Optional.empty();
    }
}

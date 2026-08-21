package org.pixelfire.nationwars.activity;

/**
 * The SHIELDED to READY to AFK transition as a pure function of ticks, so it's testable without a
 * running server. {@code manualAfk} forces AFK regardless of the threshold; it's cleared by whichever
 * code path records real activity, not by this function.
 */
public final class ActivityStateMachine
{
    private ActivityStateMachine()
    {
    }

    public static PlayerActivityState compute(final PlayerActivityData data, final long currentTick, final long afkThresholdTicks)
    {
        if (data.manualAfk())
        {
            return PlayerActivityState.AFK;
        }
        if (currentTick < data.shieldExpiresTick())
        {
            return PlayerActivityState.SHIELDED;
        }
        if (currentTick - data.lastActivityTick() >= afkThresholdTicks)
        {
            return PlayerActivityState.AFK;
        }
        return PlayerActivityState.READY;
    }
}

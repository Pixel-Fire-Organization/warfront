package org.pixelfire.nationwars.state;

/**
 * Pure per-tick advancement of an {@link EvasionTracker}. Deliberately driven by a fixed
 * {@code stepMs} per invocation rather than a wall-clock delta between ticks: the caller only invokes
 * this while the server is actually running, so a fixed step means downtime contributes zero elapsed
 * evasion time with no separate uptime-window bookkeeping needed to subtract it back out.
 */
public final class EvasionProgress
{
    private static final int[] WARNING_THRESHOLDS_DESCENDING = {90, 75, 50};

    private EvasionProgress()
    {
    }

    public static EvasionTracker advance(final EvasionTracker tracker, final boolean nationHasReadyPlayer,
            final boolean opponentHasReadyPlayer, final long stepMs, final long participationMinimumMs)
    {
        long qualifying = tracker.qualifyingReadyMs();
        long evasion = tracker.evasionAccruedMs();
        int warned = tracker.lastWarnedThresholdPercent();

        if (nationHasReadyPlayer)
        {
            qualifying += stepMs;
            if (qualifying >= participationMinimumMs)
            {
                evasion = 0L;
                qualifying = 0L;
                warned = 0;
            }
        }
        else if (opponentHasReadyPlayer)
        {
            evasion += stepMs;
        }

        return new EvasionTracker(tracker.warId(), tracker.nationId(), evasion, qualifying, warned);
    }

    /**
     * @return the highest warning threshold newly crossed (50, 75 or 90), or 0 if none was crossed
     *         since {@code lastWarnedThresholdPercent}
     */
    public static int nextWarningThreshold(final long evasionAccruedMs, final long evasionLimitMs, final int lastWarnedThresholdPercent)
    {
        if (evasionLimitMs <= 0)
        {
            return 0;
        }
        for (final int threshold : WARNING_THRESHOLDS_DESCENDING)
        {
            if (threshold > lastWarnedThresholdPercent && evasionAccruedMs * 100L >= (long) threshold * evasionLimitMs)
            {
                return threshold;
            }
        }
        return 0;
    }

    public static boolean breached(final long evasionAccruedMs, final long evasionLimitMs)
    {
        return evasionLimitMs > 0 && evasionAccruedMs >= evasionLimitMs;
    }
}

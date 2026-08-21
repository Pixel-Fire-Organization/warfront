package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Per (war, nation) evasion-surrender clock. {@code evasionAccruedMs} accrues while this nation has no
 * Ready player and the opposing coalition does; {@code qualifyingReadyMs} accumulates while this nation
 * has any Ready player at all, and resets both counters (and {@code lastWarnedThresholdPercent}) once it
 * reaches {@code warParticipationMinimum} — fielding an hour of presence clears the clock outright rather
 * than merely pausing it. {@code lastWarnedThresholdPercent} is the highest of 50/75/90 already announced,
 * so a threshold is only ever warned once.
 */
public record EvasionTracker(UUID warId, UUID nationId, long evasionAccruedMs, long qualifyingReadyMs, int lastWarnedThresholdPercent)
{
    public static EvasionTracker empty(final UUID warId, final UUID nationId)
    {
        return new EvasionTracker(warId, nationId, 0L, 0L, 0);
    }
}

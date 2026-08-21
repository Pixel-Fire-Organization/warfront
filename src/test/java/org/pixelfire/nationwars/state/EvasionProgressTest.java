package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvasionProgressTest
{
    private static final long PARTICIPATION_MINIMUM_MS = 3_600_000L;
    private final EvasionTracker fresh = EvasionTracker.empty(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void accruesOnlyWhileOpponentIsReadyAndNationIsNot()
    {
        final EvasionTracker advanced = EvasionProgress.advance(fresh, false, true, 1000L, PARTICIPATION_MINIMUM_MS);

        assertEquals(1000L, advanced.evasionAccruedMs());
    }

    @Test
    void doesNotAccrueWhenOpponentIsAbsent()
    {
        final EvasionTracker advanced = EvasionProgress.advance(fresh, false, false, 1000L, PARTICIPATION_MINIMUM_MS);

        assertEquals(0L, advanced.evasionAccruedMs());
    }

    @Test
    void doesNotAccrueWhileNationHasAReadyPlayer()
    {
        final EvasionTracker advanced = EvasionProgress.advance(fresh, true, true, 1000L, PARTICIPATION_MINIMUM_MS);

        assertEquals(0L, advanced.evasionAccruedMs());
        assertEquals(1000L, advanced.qualifyingReadyMs());
    }

    @Test
    void aFewMinutesOfReadyTimeDoesNotResetTheClock()
    {
        final EvasionTracker partiallyEvaded = new EvasionTracker(fresh.warId(), fresh.nationId(), 500_000L, 0L, 0);

        final EvasionTracker advanced = EvasionProgress.advance(partiallyEvaded, true, true, 300_000L, PARTICIPATION_MINIMUM_MS);

        assertEquals(500_000L, advanced.evasionAccruedMs());
        assertEquals(300_000L, advanced.qualifyingReadyMs());
    }

    @Test
    void anHourOfQualifyingReadyTimeClearsTheClock()
    {
        final EvasionTracker partiallyEvaded = new EvasionTracker(fresh.warId(), fresh.nationId(), 500_000L,
                PARTICIPATION_MINIMUM_MS - 1000L, 50);

        final EvasionTracker advanced = EvasionProgress.advance(partiallyEvaded, true, true, 1000L, PARTICIPATION_MINIMUM_MS);

        assertEquals(0L, advanced.evasionAccruedMs());
        assertEquals(0L, advanced.qualifyingReadyMs());
        assertEquals(0, advanced.lastWarnedThresholdPercent());
    }

    @Test
    void qualifyingTimeAccumulatesAcrossMultipleContributingCitizensOverTicks()
    {
        EvasionTracker tracker = fresh;
        for (int i = 0; i < 10; i++)
        {
            tracker = EvasionProgress.advance(tracker, true, false, 1000L, PARTICIPATION_MINIMUM_MS);
        }

        assertEquals(10_000L, tracker.qualifyingReadyMs());
    }

    @Test
    void warningThresholdsFireOnceEachInDescendingOrder()
    {
        final long limit = 100_000L;

        assertEquals(50, EvasionProgress.nextWarningThreshold(50_000L, limit, 0));
        assertEquals(75, EvasionProgress.nextWarningThreshold(75_000L, limit, 50));
        assertEquals(90, EvasionProgress.nextWarningThreshold(90_000L, limit, 75));
        assertEquals(0, EvasionProgress.nextWarningThreshold(95_000L, limit, 90));
    }

    @Test
    void breachedOnlyAtOrPastTheLimit()
    {
        assertFalse(EvasionProgress.breached(99_999L, 100_000L));
        assertTrue(EvasionProgress.breached(100_000L, 100_000L));
    }

    @Test
    void zeroLimitNeverWarnsOrBreaches()
    {
        assertEquals(0, EvasionProgress.nextWarningThreshold(1_000_000L, 0, 0));
        assertFalse(EvasionProgress.breached(1_000_000L, 0));
    }
}

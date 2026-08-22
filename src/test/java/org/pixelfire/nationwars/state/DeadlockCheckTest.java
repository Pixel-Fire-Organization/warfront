package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlockCheckTest
{
    @Test
    void notDeadlockedWhenNeitherThresholdIsMet()
    {
        assertFalse(DeadlockCheck.isDeadlocked(0L, 1, 1000L, 10_000L, 3));
    }

    @Test
    void deadlockedOnceOpenLongEnough()
    {
        assertTrue(DeadlockCheck.isDeadlocked(0L, 0, 10_000L, 10_000L, 3));
    }

    @Test
    void deadlockedOnceEnoughOffersRejected()
    {
        assertTrue(DeadlockCheck.isDeadlocked(0L, 3, 1000L, 10_000L, 3));
    }
}

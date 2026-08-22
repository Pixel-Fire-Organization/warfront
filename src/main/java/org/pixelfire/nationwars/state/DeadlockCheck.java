package org.pixelfire.nationwars.state;

/**
 * A negotiation is flagged for staff attention once it's been open {@code deadlockThreshold} or has
 * accumulated {@code deadlockRejections} rejected offers.
 */
public final class DeadlockCheck
{
    private DeadlockCheck()
    {
    }

    public static boolean isDeadlocked(final long createdAt, final int rejectionCount, final long now,
            final long deadlockThresholdMillis, final int deadlockRejections)
    {
        return now - createdAt >= deadlockThresholdMillis || rejectionCount >= deadlockRejections;
    }
}

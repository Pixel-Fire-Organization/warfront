package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripedLocksTest
{
    @Test
    void rejectsNonPositiveStripeCount()
    {
        assertThrows(IllegalArgumentException.class, () -> new StripedLocks(0));
    }

    @Test
    void sameIdAlwaysMapsToTheSameStripe()
    {
        final StripedLocks locks = new StripedLocks(8);
        final UUID id = UUID.randomUUID();

        assertSame(locks.stripeFor(id), locks.stripeFor(id));
    }

    @Test
    void withLocksReturnsTheActionResult()
    {
        final StripedLocks locks = new StripedLocks(8);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();

        final int result = locks.withLocks(() -> 42, a, b);

        assertEquals(42, result);
    }

    @Test
    void withLocksReleasesEvenOnException()
    {
        final StripedLocks locks = new StripedLocks(8);
        final UUID id = UUID.randomUUID();

        assertThrows(RuntimeException.class, () -> locks.withLocks(() ->
        {
            throw new RuntimeException("boom");
        }, id));

        // The stripe must be free again, or this second call would hang forever.
        final boolean acquiredAgain = locks.withLocks(() -> true, id);
        assertTrue(acquiredAgain);
    }

    /**
     * Two threads lock the same pair of ids in opposite order, repeatedly and concurrently — the
     * exact scenario a naive "acquire in the order the caller passed them" implementation would
     * deadlock on (thread 1 holds stripe(a) waiting for stripe(b) while thread 2 holds stripe(b)
     * waiting for stripe(a)). Because both ids always resolve to the same fixed global acquisition
     * order regardless of which order they were passed in, the two threads instead simply serialize
     * on the shared stripes and both complete well within the timeout.
     */
    @Test
    void reversedAcquisitionOrderDoesNotDeadlock() throws InterruptedException
    {
        final StripedLocks locks = new StripedLocks(4);
        final UUID a = UUID.randomUUID();
        final UUID b = findIdOnADifferentStripe(locks, a);

        final int iterationsPerThread = 200;
        final AtomicInteger completed = new AtomicInteger();

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try
        {
            pool.submit(() ->
            {
                for (int i = 0; i < iterationsPerThread; i++)
                {
                    locks.withLocks(completed::incrementAndGet, a, b);
                }
            });
            pool.submit(() ->
            {
                for (int i = 0; i < iterationsPerThread; i++)
                {
                    locks.withLocks(completed::incrementAndGet, b, a);
                }
            });

            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "both threads should finish without deadlocking");
            assertEquals(2 * iterationsPerThread, completed.get());
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    private static UUID findIdOnADifferentStripe(final StripedLocks locks, final UUID reference)
    {
        for (int i = 0; i < 10_000; i++)
        {
            final UUID candidate = UUID.randomUUID();
            if (!locks.stripeFor(candidate).equals(locks.stripeFor(reference)))
            {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find a UUID on a different stripe; this should be astronomically unlikely");
    }
}

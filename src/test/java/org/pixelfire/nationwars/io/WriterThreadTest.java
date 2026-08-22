package org.pixelfire.nationwars.io;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriterThreadTest
{
    @Test
    void rejectsNonPositiveQueueCapacity()
    {
        assertThrows(IllegalArgumentException.class, () -> new WriterThread(0));
    }

    @Test
    void writesRunOnADedicatedThreadInSubmissionOrder() throws InterruptedException
    {
        // Comfortably larger than the number of writes below so this test exercises ordering, not
        // the saturation fallback (that has its own test).
        try (WriterThread writer = new WriterThread(64))
        {
            final List<Integer> order = new CopyOnWriteArrayList<>();
            final List<String> threadNames = new CopyOnWriteArrayList<>();
            final CountDownLatch done = new CountDownLatch(20);

            for (int i = 0; i < 20; i++)
            {
                final int value = i;
                writer.submit(() ->
                {
                    order.add(value);
                    threadNames.add(Thread.currentThread().getName());
                    done.countDown();
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(20, order.size());
            for (int i = 0; i < 20; i++)
            {
                assertEquals(i, order.get(i), "writes should be drained in submission order");
            }
            assertTrue(threadNames.stream().allMatch(name -> name.startsWith("nationwars-writer-")));
        }
    }

    /**
     * With the queue saturated, a write must still happen — synchronously, on the submitting thread
     * — rather than being dropped. This is the property the whole caller-runs fallback exists for.
     */
    @Test
    void aWriteIsNeverDroppedWhenTheQueueIsSaturated() throws InterruptedException
    {
        try (WriterThread writer = new WriterThread(1))
        {
            final CountDownLatch blockFirstWrite = new CountDownLatch(1);
            writer.submit(() -> awaitQuietly(blockFirstWrite));
            // Fills the one queue slot; the executor is now fully saturated (1 running + 1 queued).
            writer.submit(() -> { });

            final String submittingThreadName = Thread.currentThread().getName();
            final List<String> ranOnThread = new CopyOnWriteArrayList<>();

            writer.submit(() -> ranOnThread.add(Thread.currentThread().getName()));

            // No need to wait: the caller-runs fallback means this already happened, synchronously,
            // before writer.submit() above returned.
            assertEquals(1, ranOnThread.size(), "the write must not be dropped");
            assertEquals(submittingThreadName, ranOnThread.get(0), "a saturated write runs synchronously on the caller");

            blockFirstWrite.countDown();
        }
    }

    @Test
    void aFailingWriteIsLoggedAndDoesNotPreventLaterWrites() throws InterruptedException
    {
        try (WriterThread writer = new WriterThread(16))
        {
            writer.submit(() ->
            {
                throw new RuntimeException("boom");
            });

            final CountDownLatch laterWriteRan = new CountDownLatch(1);
            writer.submit(laterWriteRan::countDown);

            assertTrue(laterWriteRan.await(5, TimeUnit.SECONDS), "the writer thread should survive a failing write");
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            latch.await(5, TimeUnit.SECONDS);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}

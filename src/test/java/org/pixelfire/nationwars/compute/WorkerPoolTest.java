package org.pixelfire.nationwars.compute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkerPoolTest
{
    @Test
    void resolveThreadCountUsesConfiguredValueWhenPositive()
    {
        assertEquals(7, WorkerPool.resolveThreadCount(7));
    }

    @Test
    void resolveThreadCountAutoSizesWhenZeroOrLess()
    {
        final int expected = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);

        assertEquals(expected, WorkerPool.resolveThreadCount(0));
        assertEquals(expected, WorkerPool.resolveThreadCount(-5));
    }

    @Test
    void rejectsNonPositiveSizing()
    {
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new WorkerPool(2, 0));
    }

    @Test
    void submitDoesNotBlockTheCallingThreadAndRunsWorkOnAWorkerThread() throws InterruptedException
    {
        try (WorkerPool pool = new WorkerPool(2, 10))
        {
            final CountDownLatch workStarted = new CountDownLatch(1);
            final CountDownLatch releaseWork = new CountDownLatch(1);
            final AtomicReference<String> workerThreadName = new AtomicReference<>();

            final long before = System.nanoTime();
            pool.submit("test-task", () ->
            {
                workerThreadName.set(Thread.currentThread().getName());
                workStarted.countDown();
                awaitQuietly(releaseWork);
                return "done";
            }, Runnable::run, result -> { });
            final long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

            // submit() must return immediately even though the work is deliberately still blocked.
            assertTrue(elapsedMillis < 1000, "submit() should not block on the work completing, took " + elapsedMillis + "ms");

            assertTrue(workStarted.await(5, TimeUnit.SECONDS), "work should start on a pool thread");
            assertTrue(workerThreadName.get().startsWith("nationwars-worker-"), "unexpected thread name: " + workerThreadName.get());

            releaseWork.countDown();
        }
    }

    @Test
    void resultIsDeliveredThroughTheProvidedExecutor()
    {
        try (WorkerPool pool = new WorkerPool(2, 10))
        {
            final Executor resultExecutor = mock(Executor.class);
            final CountDownLatch done = new CountDownLatch(1);

            pool.submit("test-task", () -> "hello", resultExecutor, result -> done.countDown());

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verifyEventually(resultExecutor, captor);
            // The mock never actually ran the captured runnable, so onResult has not fired yet.
            assertEquals(1, done.getCount());
            captor.getValue().run();
            assertEquals(0, done.getCount());
        }
    }

    @Test
    void aFailingTaskIsSwallowedAndDoesNotPreventLaterTasksFromRunning() throws InterruptedException
    {
        try (WorkerPool pool = new WorkerPool(2, 10))
        {
            final CountDownLatch failedTaskHandled = new CountDownLatch(1);
            pool.submit("failing-task", () ->
            {
                throw new RuntimeException("boom");
            }, Runnable::run, result -> failedTaskHandled.countDown());

            // onResult must never be called for a failed task.
            Thread.sleep(200);
            assertEquals(1, failedTaskHandled.getCount());

            final CountDownLatch laterTaskHandled = new CountDownLatch(1);
            pool.submit("later-task", () -> "ok", Runnable::run, result -> laterTaskHandled.countDown());
            assertTrue(laterTaskHandled.await(5, TimeUnit.SECONDS), "the pool should still accept work after a task fails");
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void verifyEventually(final Executor mockExecutor, final ArgumentCaptor<Runnable> captor)
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AssertionError last = null;
        while (System.nanoTime() < deadline)
        {
            try
            {
                verify(mockExecutor).execute(captor.capture());
                return;
            }
            catch (final AssertionError e)
            {
                last = e;
                try
                {
                    Thread.sleep(10);
                }
                catch (final InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        fail("resultExecutor.execute() was never called: " + last);
    }
}

package org.pixelfire.nationwars.io;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single dedicated thread that owns disk writes (audit log entries, persistence snapshots) so they
 * never block the main thread or a compute worker. Writes are enqueued from the main thread in
 * microseconds and drained from a bounded queue in submission order.
 *
 * <p>If the queue saturates, a write runs synchronously on the submitting thread instead of being
 * queued or dropped — a write is never lost, even under overload; the cost is paid as latency on
 * whoever submitted it rather than as a gap in the log or save file.
 */
public final class WriterThread implements AutoCloseable
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;

    public WriterThread(final int queueCapacity)
    {
        if (queueCapacity < 1)
        {
            throw new IllegalArgumentException("queueCapacity must be at least 1, got " + queueCapacity);
        }
        this.queueCapacity = queueCapacity;
        this.executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new WriterThreadFactory(),
                new SynchronousFallback());
    }

    /**
     * Enqueues a write. Runs on the dedicated writer thread in submission order, unless the queue is
     * saturated, in which case it runs synchronously on the calling thread instead. Any exception the
     * write throws is logged rather than left uncaught, since neither the writer thread nor a
     * saturated caller should die because one write failed.
     */
    public void submit(final Runnable write)
    {
        executor.execute(() ->
        {
            try
            {
                write.run();
            }
            catch (final RuntimeException e)
            {
                LOGGER.error("nationwars writer task failed", e);
            }
        });
    }

    /**
     * Current depth of the pending-write queue, for {@code /nationwars staff perf}.
     */
    public int queueDepth()
    {
        return executor.getQueue().size();
    }

    /**
     * Stops accepting new writes and waits briefly for the queue to drain.
     */
    @Override
    public void close()
    {
        executor.shutdown();
        try
        {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS))
            {
                executor.shutdownNow();
            }
        }
        catch (final InterruptedException e)
        {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private final class SynchronousFallback implements RejectedExecutionHandler
    {
        @Override
        public void rejectedExecution(final Runnable task, final ThreadPoolExecutor pool)
        {
            if (pool.isShutdown())
            {
                return;
            }
            LOGGER.warn("nationwars writer queue saturated (capacity={}); running this write synchronously instead of queuing it", queueCapacity);
            task.run();
        }
    }

    private static final class WriterThreadFactory implements ThreadFactory
    {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "nationwars-writer-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}

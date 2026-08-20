package org.pixelfire.nationwars.compute;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The off-main-thread worker pool: pure computation (claim sets, war score, settlement validation,
 * and similar) runs here, never on the main server thread. A worker only ever computes a result — it
 * never commits one. {@link #submit} always hands the result to a caller-supplied {@link Executor}
 * (in practice, one backed by {@code server::execute}) so the commit still happens on the main thread.
 *
 * <p>Nothing in this class ever calls {@code Future.get()} or {@code Future.join()}: a caller that
 * needs a result synchronously does not have a task that belongs on this pool.
 */
public final class WorkerPool implements AutoCloseable
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ThreadPoolExecutor executor;

    public WorkerPool(final int threadCount, final int queueCapacity)
    {
        if (threadCount < 1)
        {
            throw new IllegalArgumentException("threadCount must be at least 1, got " + threadCount);
        }
        if (queueCapacity < 1)
        {
            throw new IllegalArgumentException("queueCapacity must be at least 1, got " + queueCapacity);
        }
        this.executor = new ThreadPoolExecutor(
                threadCount, threadCount,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new WorkerThreadFactory(),
                // Overload degrades into synchronous execution on the submitting thread rather than
                // growing the queue without bound or dropping work.
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Resolves the configured worker thread count: a positive value is used as-is, and 0 (or less)
     * auto-sizes to at least 2 and roughly a quarter of the machine's processors.
     */
    public static int resolveThreadCount(final int configured)
    {
        return configured > 0 ? configured : Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
    }

    /**
     * Runs {@code work} on the pool, then delivers its result to {@code onResult} via
     * {@code resultExecutor}. Never blocks the calling thread. If {@code work} throws, the failure is
     * logged with {@code taskName} and {@code onResult} is not invoked — a worker task that fails
     * simply produces no result rather than crashing the pool thread or the caller.
     */
    public <T> void submit(final String taskName, final Supplier<T> work, final Executor resultExecutor, final Consumer<T> onResult)
    {
        CompletableFuture.supplyAsync(work::get, executor)
                .handleAsync((result, error) ->
                {
                    if (error != null)
                    {
                        LOGGER.error("nationwars worker task '{}' failed", taskName, error);
                        return null;
                    }
                    return result;
                }, executor)
                .thenAcceptAsync(result ->
                {
                    if (result != null)
                    {
                        onResult.accept(result);
                    }
                }, resultExecutor);
    }

    /**
     * Stops accepting new work and waits briefly for in-flight tasks to finish.
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

    private static final class WorkerThreadFactory implements ThreadFactory
    {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "nationwars-worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }
}

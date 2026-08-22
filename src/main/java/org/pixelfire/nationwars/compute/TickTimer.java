package org.pixelfire.nationwars.compute;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Per-system main-thread cost, for {@code /nationwars staff perf}: a rolling window of the last {@code
 * windowSize} recorded durations, reporting average and worst-in-window as a practical stand-in for
 * p99 — a true percentile needs a much larger sample held in memory for a cost this cheap to measure,
 * which buys little at this scale (a few tick listeners, sampled every tick or every few seconds).
 */
public final class TickTimer
{
    private final AtomicLongArray samplesNanos;
    private final AtomicLong nextIndex = new AtomicLong();
    private final AtomicLong sampleCount = new AtomicLong();

    public TickTimer(final int windowSize)
    {
        this.samplesNanos = new AtomicLongArray(windowSize);
    }

    public void record(final long durationNanos)
    {
        final int index = (int) (nextIndex.getAndIncrement() % samplesNanos.length());
        samplesNanos.set(index, durationNanos);
        sampleCount.incrementAndGet();
    }

    public Snapshot snapshot()
    {
        final int count = (int) Math.min(sampleCount.get(), samplesNanos.length());
        if (count == 0)
        {
            return new Snapshot(0.0, 0L, 0L);
        }
        long total = 0L;
        long max = 0L;
        for (int i = 0; i < count; i++)
        {
            final long value = samplesNanos.get(i);
            total += value;
            max = Math.max(max, value);
        }
        return new Snapshot(total / (double) count / 1_000_000.0, max / 1_000_000L, count);
    }

    public record Snapshot(double averageMs, long worstInWindowMs, long sampleCount)
    {
    }
}

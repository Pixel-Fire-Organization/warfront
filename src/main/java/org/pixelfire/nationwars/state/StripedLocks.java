package org.pixelfire.nationwars.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A fixed pool of locks used to guard an update that touches more than one record at once — for
 * example, moving a checkpoint between two cities, or applying a settlement across several belligerent
 * nations. Every id maps deterministically to one of a fixed number of stripes via its hash, so the
 * same id always lands on the same stripe and unrelated ids usually don't contend with each other.
 *
 * <p>Multi-id updates must go through {@link #withLocks} rather than acquiring stripes by hand: it
 * always locks the stripes touched by the given ids in the same fixed order (by stripe position, not
 * by the order the ids were passed in), so two threads locking overlapping id sets can never deadlock
 * against each other — including the case where two different ids happen to land on the same stripe.
 */
public final class StripedLocks
{
    private final ReentrantLock[] stripes;

    public StripedLocks(final int stripeCount)
    {
        if (stripeCount < 1)
        {
            throw new IllegalArgumentException("stripeCount must be at least 1, got " + stripeCount);
        }
        stripes = new ReentrantLock[stripeCount];
        for (int i = 0; i < stripeCount; i++)
        {
            stripes[i] = new ReentrantLock();
        }
    }

    /**
     * The stripe a given id would lock. Exposed for inspection/testing; regular callers should use
     * {@link #withLocks} instead of locking a single stripe directly.
     */
    public Lock stripeFor(final UUID id)
    {
        return stripes[indexOf(id)];
    }

    /**
     * Runs {@code action} with every stripe touched by {@code ids} held, and returns its result.
     * Locks are acquired in a fixed global order and released in reverse.
     */
    public <T> T withLocks(final Supplier<T> action, final UUID... ids)
    {
        final List<Lock> locks = orderedDistinctStripes(ids);
        for (final Lock lock : locks)
        {
            lock.lock();
        }
        try
        {
            return action.get();
        }
        finally
        {
            for (int i = locks.size() - 1; i >= 0; i--)
            {
                locks.get(i).unlock();
            }
        }
    }

    /**
     * {@link #withLocks(Supplier, UUID...)} for an action with no result.
     */
    public void withLocks(final Runnable action, final UUID... ids)
    {
        withLocks(() ->
        {
            action.run();
            return null;
        }, ids);
    }

    private List<Lock> orderedDistinctStripes(final UUID[] ids)
    {
        final boolean[] touched = new boolean[stripes.length];
        for (final UUID id : ids)
        {
            touched[indexOf(id)] = true;
        }
        final List<Lock> ordered = new ArrayList<>();
        for (int i = 0; i < stripes.length; i++)
        {
            if (touched[i])
            {
                ordered.add(stripes[i]);
            }
        }
        return ordered;
    }

    private int indexOf(final UUID id)
    {
        return Math.floorMod(id.hashCode(), stripes.length);
    }
}

package org.pixelfire.nationwars.state;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds every piece of live game state — cities, checkpoints, wars, and per-nation records — as
 * immutable records keyed by id. Reads never block: a {@link ConcurrentHashMap} lookup always
 * returns a consistent snapshot of one record with no locking. Mutation replaces a record wholesale
 * (put a new instance under the same id) rather than changing one in place.
 *
 * <p>Updates that must keep more than one record consistent with each other go through
 * {@link #stripedLocks()}. Updates rare enough that contention doesn't matter but that touch many
 * records at once (settlement application, city transfer) take {@link #globalWriteLock()} instead.
 */
public final class NationRegistry
{
    private final ConcurrentMap<UUID, City> cities = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Checkpoint> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, War> wars = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, NationState> nationStates = new ConcurrentHashMap<>();

    private final StripedLocks stripedLocks;
    private final Lock globalWriteLock = new ReentrantLock();

    public NationRegistry(final int lockStripes)
    {
        this.stripedLocks = new StripedLocks(lockStripes);
    }

    public ConcurrentMap<UUID, City> cities()
    {
        return cities;
    }

    public ConcurrentMap<UUID, Checkpoint> checkpoints()
    {
        return checkpoints;
    }

    public ConcurrentMap<UUID, War> wars()
    {
        return wars;
    }

    public ConcurrentMap<UUID, NationState> nationStates()
    {
        return nationStates;
    }

    public StripedLocks stripedLocks()
    {
        return stripedLocks;
    }

    /**
     * Guards rare, multi-record atomic changes such as settlement application or a city transfer.
     * Contention is irrelevant at that event rate; correctness under a simple single lock is worth
     * more than the complexity of striping it too.
     */
    public Lock globalWriteLock()
    {
        return globalWriteLock;
    }
}

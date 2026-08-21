package org.pixelfire.nationwars.capture;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when each player entered each checkpoint's capture zone, so presence evaluation can exclude
 * anyone there under a second. Not persisted: a restart just resets everyone's dwell timer,
 * which is unobservable to players since the zone check re-evaluates every {@code captureTickInterval}.
 */
public final class CaptureZoneTracker
{
    private final Map<UUID, Map<UUID, Long>> enteredAtByCheckpoint = new ConcurrentHashMap<>();

    /**
     * Records that {@code playerId} is present in {@code checkpointId}'s zone this tick, returning how
     * many ticks they've been present continuously (0 the first tick they're seen).
     */
    public long recordPresence(final UUID checkpointId, final UUID playerId, final long currentTick)
    {
        final Map<UUID, Long> byPlayer = enteredAtByCheckpoint.computeIfAbsent(checkpointId, id -> new ConcurrentHashMap<>());
        final long enteredAt = byPlayer.computeIfAbsent(playerId, id -> currentTick);
        return currentTick - enteredAt;
    }

    /**
     * Clears dwell time for every player at {@code checkpointId} not in {@code stillPresent}, so leaving
     * and re-entering restarts the dwell timer.
     */
    public void retainOnly(final UUID checkpointId, final Set<UUID> stillPresent)
    {
        final Map<UUID, Long> byPlayer = enteredAtByCheckpoint.get(checkpointId);
        if (byPlayer != null)
        {
            byPlayer.keySet().retainAll(stillPresent);
        }
    }
}

package org.pixelfire.nationwars.world;

import org.pixelfire.nationwars.state.CheckpointStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the one pending re-place per player that {@code checkpointMoveGrace} allows: break
 * a checkpoint, then re-place it within the window and keep its identity and capture history instead of
 * it counting as delete-plus-create. Deliberately in-memory only — the window is measured in seconds, so
 * losing it across a restart is not a real-world concern.
 */
public final class CheckpointMoveGrace
{
    private final Map<UUID, PendingMove> pendingByPlayer = new ConcurrentHashMap<>();

    public record PendingMove(
            UUID checkpointId,
            UUID cityId,
            UUID holderNationId,
            float captureProgress,
            UUID capturingNationId,
            CheckpointStatus status,
            long expiresAt)
    {
    }

    public void record(final UUID playerId, final PendingMove move)
    {
        pendingByPlayer.put(playerId, move);
    }

    /**
     * Consumes and returns the pending move for {@code playerId} targeting {@code cityId}, if one exists
     * and hasn't expired. A mismatched city, an expired entry, or no entry at all all return empty, and
     * any expired entry found is discarded either way.
     */
    public Optional<PendingMove> claim(final UUID playerId, final UUID cityId, final long now)
    {
        final PendingMove pending = pendingByPlayer.get(playerId);
        if (pending == null)
        {
            return Optional.empty();
        }
        if (pending.expiresAt() < now)
        {
            pendingByPlayer.remove(playerId);
            return Optional.empty();
        }
        if (!pending.cityId().equals(cityId))
        {
            return Optional.empty();
        }
        pendingByPlayer.remove(playerId);
        return Optional.of(pending);
    }
}

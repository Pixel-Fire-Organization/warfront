package org.pixelfire.nationwars.capture;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After a checkpoint flips, blocks the side that just lost it from immediately recapturing it for
 * {@code checkpointLockout}.
 */
public final class CheckpointLockout
{
    private final Map<UUID, Long> lockedUntilByCheckpoint = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lockedAgainstNationByCheckpoint = new ConcurrentHashMap<>();

    public void lock(final UUID checkpointId, final UUID previousHolderNationId, final long currentTick, final long lockoutTicks)
    {
        lockedUntilByCheckpoint.put(checkpointId, currentTick + lockoutTicks);
        lockedAgainstNationByCheckpoint.put(checkpointId, previousHolderNationId);
    }

    /**
     * True if {@code nationId} is currently locked out of recapturing {@code checkpointId}.
     */
    public boolean isLockedOut(final UUID checkpointId, final UUID nationId, final long currentTick)
    {
        final Long lockedUntil = lockedUntilByCheckpoint.get(checkpointId);
        if (lockedUntil == null || currentTick >= lockedUntil)
        {
            return false;
        }
        return nationId.equals(lockedAgainstNationByCheckpoint.get(checkpointId));
    }
}

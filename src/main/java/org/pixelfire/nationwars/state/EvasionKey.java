package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Keys an {@link EvasionTracker} to one belligerent nation's clock in one war.
 */
public record EvasionKey(UUID warId, UUID nationId)
{
}

package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Placeholder checkpoint record: only the id needed to key it in {@link NationRegistry}. The full
 * field set (owning city, holder, capture progress, status, claimed chunks, and so on) lands when
 * checkpoint placement is implemented.
 */
public record Checkpoint(UUID checkpointId)
{
}

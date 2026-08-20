package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Placeholder city record: only the id needed to key it in {@link NationRegistry}. The full field
 * set (owner, tier, checkpoints, state, and so on) lands when city founding is implemented.
 */
public record City(UUID cityId)
{
}

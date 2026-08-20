package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Placeholder per-nation record: only the id needed to key it in {@link NationRegistry}. The full
 * field set (owned cities, capital, active wars, cooldowns, and so on) lands alongside the features
 * that need them.
 */
public record NationState(UUID nationId)
{
}

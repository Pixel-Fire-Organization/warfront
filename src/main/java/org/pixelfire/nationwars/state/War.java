package org.pixelfire.nationwars.state;

import java.util.UUID;

/**
 * Placeholder war record: only the id needed to key it in {@link NationRegistry}. The full field set
 * (coalitions, phase, deadlines, targeted cities, war score, and so on) lands when war declaration is
 * implemented.
 */
public record War(UUID warId)
{
}

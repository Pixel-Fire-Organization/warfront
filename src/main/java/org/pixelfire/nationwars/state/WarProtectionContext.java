package org.pixelfire.nationwars.state;

/**
 * Every input {@link WarProtectionOverride#isAllowed} needs, already resolved into booleans by the
 * caller: which war (if any) covers the chunk, whether it's ACTIVE, whether the two nations are on
 * opposing sides of it, and whether the action is in the configured override list.
 */
public record WarProtectionContext(boolean warActive, boolean opposingCoalitions, boolean chunkInTargetCityClaims, boolean actionOverridden)
{
}

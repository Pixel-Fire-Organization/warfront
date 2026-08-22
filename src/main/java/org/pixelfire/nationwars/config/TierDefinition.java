package org.pixelfire.nationwars.config;

/**
 * One entry of the tier ladder: the checkpoint radius, upgrade cost, and checkpoint count bounds
 * for a single city tier. {@code minCheckpoints} of tier {@code N} must equal {@code maxCheckpoints}
 * of tier {@code N-1} — validated by {@link TierValidation}, not enforced here.
 *
 * <p>{@code radius} is in blocks, already converted from the chunk-based value authored in config by
 * {@link TierListParser}.
 */
public record TierDefinition(int radius, long cost, int minCheckpoints, int maxCheckpoints)
{
}

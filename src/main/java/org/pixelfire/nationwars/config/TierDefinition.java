package org.pixelfire.nationwars.config;

/**
 * One entry of the tier ladder: the checkpoint radius, upgrade cost, and checkpoint count bounds
 * for a single city tier. {@code minCheckpoints} of tier {@code N} must equal {@code maxCheckpoints}
 * of tier {@code N-1} — validated by {@link TierValidation}, not enforced here.
 *
 * <p>{@code radius} counts checkpoint-chunk grid cells (see {@code CheckpointChunkGrid} in the
 * {@code world} package), not blocks or raw chunks. Consumers needing a block distance (e.g. comparing
 * against another city's raw position) must convert via {@code CheckpointChunkGrid.BLOCKS_PER_CELL}.
 */
public record TierDefinition(int radius, long cost, int minCheckpoints, int maxCheckpoints)
{
}

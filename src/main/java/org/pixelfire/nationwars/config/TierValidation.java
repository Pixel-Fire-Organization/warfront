package org.pixelfire.nationwars.config;

import org.pixelfire.nationwars.world.CheckpointChunkGrid;

import java.util.List;
import java.util.function.Consumer;

/**
 * Config-load-time validators for the tier ladder, checkpoint spacing feasibility, and minimum core
 * spacing. Pure functions over parsed config so they can be unit-tested without booting Forge.
 */
public final class TierValidation
{
    private TierValidation()
    {
    }

    /**
     * A tier's minCheckpoints must equal the previous tier's maxCheckpoints, so a city has to fill
     * its current tier before it can upgrade. Refuses to start if violated, naming the offending
     * tier (1-indexed, matching how tiers are referred to in-game and in the GUI).
     */
    public static void validateLadder(final List<TierDefinition> tiers)
    {
        if (tiers.isEmpty())
        {
            throw new ConfigValidationException("tiers must not be empty");
        }
        for (int i = 0; i < tiers.size(); i++)
        {
            final TierDefinition tier = tiers.get(i);
            if (tier.minCheckpoints() > tier.maxCheckpoints())
            {
                throw new ConfigValidationException("tier " + (i + 1) + " has minCheckpoints (" + tier.minCheckpoints()
                        + ") greater than maxCheckpoints (" + tier.maxCheckpoints() + ")");
            }
            if (i > 0)
            {
                final int previousMax = tiers.get(i - 1).maxCheckpoints();
                if (tier.minCheckpoints() != previousMax)
                {
                    throw new ConfigValidationException("tier " + (i + 1) + " has minCheckpoints (" + tier.minCheckpoints()
                            + ") but tier " + i + " has maxCheckpoints (" + previousMax
                            + "); the ladder requires min(N) = max(N-1), making tier " + (i + 1) + " unreachable");
                }
            }
        }
    }

    /**
     * Checks that a tier's own maximum checkpoint count can physically fit within its radius: a
     * checkpoint may only occupy one checkpoint-chunk grid cell, so the number of cells within that
     * radius (Euclidean, in cell units, excluding the city's own origin cell) is a hard ceiling.
     * Refuses to start if not — otherwise a city could never reach its own tier maximum.
     */
    public static void validateSpacingFeasibility(final List<TierDefinition> tiers)
    {
        for (int i = 0; i < tiers.size(); i++)
        {
            final TierDefinition tier = tiers.get(i);
            final int availableCells = cellsWithinRadius(tier.radius());
            if (tier.maxCheckpoints() > availableCells)
            {
                throw new ConfigValidationException("tier " + (i + 1) + " cannot place its own maxCheckpoints (" + tier.maxCheckpoints()
                        + ") within its radius of " + tier.radius() + " checkpoint-chunk cells; only " + availableCells
                        + " cells are available at that radius");
            }
        }
    }

    private static int cellsWithinRadius(final int radius)
    {
        int count = 0;
        for (int i = -radius; i <= radius; i++)
        {
            for (int j = -radius; j <= radius; j++)
            {
                if ((i != 0 || j != 0) && i * i + j * j <= radius * radius)
                {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * {@code minCoreDistance} must exceed {@code 2 * maxTierRadius + 8}, or two fully-upgraded
     * cities could overlap. Unlike the two checks above, this one clamps the value up with a warning
     * rather than refusing to start, since it can be corrected automatically without ambiguity.
     *
     * @param warn callback invoked with a human-readable warning iff the value was clamped
     * @return the (possibly clamped) minCoreDistance to use
     */
    public static int clampMinCoreDistance(final int minCoreDistance, final List<TierDefinition> tiers, final Consumer<String> warn)
    {
        final int maxTierRadiusCells = tiers.stream().mapToInt(TierDefinition::radius).max().orElse(0);
        final int maxTierRadiusBlocks = maxTierRadiusCells * CheckpointChunkGrid.BLOCKS_PER_CELL;
        final int floor = 2 * maxTierRadiusBlocks + 8;
        if (minCoreDistance <= floor)
        {
            final int clamped = floor + 1;
            warn.accept("minCoreDistance (" + minCoreDistance + ") must exceed 2 * maxTierRadius (in blocks) + 8 (" + floor
                    + "); clamped to " + clamped);
            return clamped;
        }
        return minCoreDistance;
    }
}

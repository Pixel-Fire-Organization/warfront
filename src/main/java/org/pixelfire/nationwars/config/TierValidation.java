package org.pixelfire.nationwars.config;

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
     * Checks that a tier's own maximum checkpoint count can physically fit on the boundary circle at
     * the configured radius and minimum spacing ({@code spacing <= 2r*sin(pi/n)}). Refuses to start
     * if not — otherwise a city could never reach its own tier maximum.
     */
    public static void validateSpacingFeasibility(final List<TierDefinition> tiers, final double minCheckpointSpacing)
    {
        for (int i = 0; i < tiers.size(); i++)
        {
            final TierDefinition tier = tiers.get(i);
            if (tier.maxCheckpoints() <= 1)
            {
                continue;
            }
            final double maxSpacing = 2 * tier.radius() * Math.sin(Math.PI / tier.maxCheckpoints());
            if (minCheckpointSpacing > maxSpacing)
            {
                throw new ConfigValidationException("tier " + (i + 1) + " cannot place its own maxCheckpoints (" + tier.maxCheckpoints()
                        + ") at radius " + tier.radius() + " with minCheckpointSpacing " + minCheckpointSpacing
                        + "; spacing must be <= " + maxSpacing + " (2 * radius * sin(pi / maxCheckpoints))");
            }
        }
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
        final int maxTierRadius = tiers.stream().mapToInt(TierDefinition::radius).max().orElse(0);
        final int floor = 2 * maxTierRadius + 8;
        if (minCoreDistance <= floor)
        {
            final int clamped = floor + 1;
            warn.accept("minCoreDistance (" + minCoreDistance + ") must exceed 2 * maxTierRadius + 8 (" + floor
                    + "); clamped to " + clamped);
            return clamped;
        }
        return minCoreDistance;
    }
}

package org.pixelfire.nationwars.world;

import java.util.List;

/**
 * Whether a dimension can host a city/checkpoint at all. Takes plain booleans/strings rather than a
 * {@code Level} so the decision itself is testable without booting Forge; the caller pulls these from
 * {@code level.dimensionType()} and {@code level.dimension()}.
 */
public final class DimensionEligibility
{
    private DimensionEligibility()
    {
    }

    public static boolean isEligible(final boolean hasSkyLight, final boolean hasCeiling, final String dimensionId,
            final List<String> allowedDimensions, final List<String> blockedDimensions)
    {
        if (!hasSkyLight || hasCeiling)
        {
            return false;
        }
        if (blockedDimensions.contains(dimensionId))
        {
            return false;
        }
        return allowedDimensions.contains(dimensionId);
    }
}

package org.pixelfire.nationwars.world;

/**
 * Whether a placement is close enough to the surface, blocking 3x3 shafts to bedrock while still
 * allowing terraforming. Pure integer arithmetic so it's testable without a {@code Level}; the caller
 * pulls {@code worldSurfaceY} from {@code level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)}.
 */
public final class SurfaceRequirement
{
    private SurfaceRequirement()
    {
    }

    public static boolean isMet(final boolean requireSurfacePlacement, final int posY, final int worldSurfaceY, final int surfaceTolerance)
    {
        return !requireSurfacePlacement || posY >= worldSurfaceY - surfaceTolerance;
    }
}

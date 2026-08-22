package org.pixelfire.nationwars.world;

/**
 * The chunk footprint a core or checkpoint projects, config-selected per
 * {@code checkpointClaimShape}/{@code cityCoreClaimShape}.
 */
public enum ClaimShape
{
    PLUS,
    SINGLE,
    SQUARE,
    NONE;

    public static ClaimShape parse(final String name, final ClaimShape fallback)
    {
        try
        {
            return ClaimShape.valueOf(name);
        }
        catch (final IllegalArgumentException e)
        {
            return fallback;
        }
    }
}

package org.pixelfire.nationwars.world;

import java.util.UUID;

/**
 * A stable RGB colour derived from a nation's party UUID. Neither this mod nor OPAC's API surfaces a
 * per-party colour to key rendering off, so a UUID hash is the simplest source that is at least
 * consistent for the same nation across every render and every player's client.
 */
public final class NationColor
{
    private NationColor()
    {
    }

    public static float[] of(final UUID nationId)
    {
        final int hash = nationId.hashCode();
        final float r = ((hash >> 16) & 0xFF) / 255.0f;
        final float g = ((hash >> 8) & 0xFF) / 255.0f;
        final float b = (hash & 0xFF) / 255.0f;
        // Floor each channel so no nation's colour renders as pure black, which would be invisible
        // against a dark background.
        return new float[] {Math.max(0.25f, r), Math.max(0.25f, g), Math.max(0.25f, b)};
    }
}

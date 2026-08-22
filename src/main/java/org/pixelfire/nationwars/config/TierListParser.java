package org.pixelfire.nationwars.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code tiers} config list. Each entry is {@code "radius/cost/minCheckpoints/maxCheckpoints"},
 * where {@code radius} counts checkpoint-chunk grid cells (see {@code CheckpointChunkGrid}), not blocks
 * or raw chunks — it's compared directly against a checkpoint's cell distance from the city's own cell.
 */
public final class TierListParser
{
    private TierListParser()
    {
    }

    public static List<TierDefinition> parse(final List<? extends String> raw)
    {
        final List<TierDefinition> tiers = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++)
        {
            tiers.add(parseEntry(i, raw.get(i)));
        }
        return tiers;
    }

    private static TierDefinition parseEntry(final int index, final String entry)
    {
        final String[] parts = entry.split("/");
        if (parts.length != 4)
        {
            throw new ConfigValidationException("tiers[" + index + "] = \"" + entry
                    + "\" is malformed; expected \"radius/cost/minCheckpoints/maxCheckpoints\"");
        }
        try
        {
            final int radiusCells = Integer.parseInt(parts[0].trim());
            final long cost = Long.parseLong(parts[1].trim());
            final int min = Integer.parseInt(parts[2].trim());
            final int max = Integer.parseInt(parts[3].trim());
            return new TierDefinition(radiusCells, cost, min, max);
        }
        catch (final NumberFormatException e)
        {
            throw new ConfigValidationException("tiers[" + index + "] = \"" + entry + "\" is malformed: " + e.getMessage());
        }
    }
}

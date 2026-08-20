package org.pixelfire.nationwars.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code payments.values} config list: {@code "<item-or-tag>=<value>"}, tags prefixed with
 * {@code #}. Item/tag id resolution against the game registries happens elsewhere; this only
 * validates syntax so it stays testable without Forge.
 */
public final class PaymentListParser
{
    private PaymentListParser()
    {
    }

    public static List<PaymentEntry> parse(final List<? extends String> raw)
    {
        final List<PaymentEntry> entries = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++)
        {
            entries.add(parseEntry(i, raw.get(i)));
        }
        return entries;
    }

    private static PaymentEntry parseEntry(final int index, final String entry)
    {
        final int eq = entry.indexOf('=');
        if (eq < 0)
        {
            throw new ConfigValidationException("payments.values[" + index + "] = \"" + entry
                    + "\" is malformed; expected \"<item-or-tag>=<value>\"");
        }
        final String key = entry.substring(0, eq).trim();
        final String valuePart = entry.substring(eq + 1).trim();
        if (key.isEmpty())
        {
            throw new ConfigValidationException("payments.values[" + index + "] = \"" + entry + "\" has an empty item/tag id");
        }
        final long value;
        try
        {
            value = Long.parseLong(valuePart);
        }
        catch (final NumberFormatException e)
        {
            throw new ConfigValidationException("payments.values[" + index + "] = \"" + entry + "\" has a malformed value: " + valuePart);
        }
        if (value <= 0)
        {
            throw new ConfigValidationException("payments.values[" + index + "] = \"" + entry + "\" must have a positive value");
        }
        final boolean isTag = key.startsWith("#");
        return new PaymentEntry(isTag ? key.substring(1) : key, isTag, value);
    }
}

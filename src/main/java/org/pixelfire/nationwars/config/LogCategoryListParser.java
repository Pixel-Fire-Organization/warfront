package org.pixelfire.nationwars.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the {@code logging.categories} config list: {@code "<category>=<level>"}.
 */
public final class LogCategoryListParser
{
    private LogCategoryListParser()
    {
    }

    public static Map<String, String> parse(final List<? extends String> raw)
    {
        final Map<String, String> categories = new LinkedHashMap<>();
        for (int i = 0; i < raw.size(); i++)
        {
            final String entry = raw.get(i);
            final int eq = entry.indexOf('=');
            if (eq < 0)
            {
                throw new ConfigValidationException("logging.categories[" + i + "] = \"" + entry
                        + "\" is malformed; expected \"<category>=<level>\"");
            }
            final String category = entry.substring(0, eq).trim();
            final String level = entry.substring(eq + 1).trim();
            if (category.isEmpty() || level.isEmpty())
            {
                throw new ConfigValidationException("logging.categories[" + i + "] = \"" + entry + "\" is malformed");
            }
            categories.put(category, level.toUpperCase(Locale.ROOT));
        }
        return categories;
    }
}

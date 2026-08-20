package org.pixelfire.nationwars.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogCategoryListParserTest
{
    @Test
    void parsesDefaultCategories()
    {
        final Map<String, String> categories = LogCategoryListParser.parse(List.of("capture=INFO", "threading=WARN"));

        assertEquals("INFO", categories.get("capture"));
        assertEquals("WARN", categories.get("threading"));
    }

    @Test
    void upperCasesLevel()
    {
        final Map<String, String> categories = LogCategoryListParser.parse(List.of("capture=debug"));
        assertEquals("DEBUG", categories.get("capture"));
    }

    @Test
    void rejectsMissingEquals()
    {
        assertThrows(ConfigValidationException.class, () -> LogCategoryListParser.parse(List.of("capture")));
    }
}

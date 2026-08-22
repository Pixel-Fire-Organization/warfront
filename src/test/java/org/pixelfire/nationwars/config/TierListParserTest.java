package org.pixelfire.nationwars.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TierListParserTest
{
    @Test
    void parsesDefaultTierList()
    {
        final List<TierDefinition> tiers = TierListParser.parse(List.of("5/0/1/5", "8/128/5/8", "13/512/8/13", "21/2048/13/21"));

        assertEquals(4, tiers.size());
        assertEquals(new TierDefinition(5, 0, 1, 5), tiers.get(0));
        assertEquals(new TierDefinition(21, 2048, 13, 21), tiers.get(3));
    }

    @Test
    void rejectsWrongFieldCount()
    {
        assertThrows(ConfigValidationException.class, () -> TierListParser.parse(List.of("5/0/1")));
    }

    @Test
    void rejectsNonNumericField()
    {
        assertThrows(ConfigValidationException.class, () -> TierListParser.parse(List.of("five/0/1/5")));
    }
}

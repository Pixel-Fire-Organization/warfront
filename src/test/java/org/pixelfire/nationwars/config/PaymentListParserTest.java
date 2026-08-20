package org.pixelfire.nationwars.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentListParserTest
{
    @Test
    void parsesExactItemId()
    {
        final List<PaymentEntry> entries = PaymentListParser.parse(List.of("minecraft:iron_ingot=1"));

        assertEquals(1, entries.size());
        assertEquals("minecraft:iron_ingot", entries.get(0).itemOrTag());
        assertFalse(entries.get(0).tag());
        assertEquals(1L, entries.get(0).value());
    }

    @Test
    void parsesTagPrefixedEntry()
    {
        final List<PaymentEntry> entries = PaymentListParser.parse(List.of("#forge:gems/ruby=12"));

        assertTrue(entries.get(0).tag());
        assertEquals("forge:gems/ruby", entries.get(0).itemOrTag());
        assertEquals(12L, entries.get(0).value());
    }

    @Test
    void rejectsMissingEquals()
    {
        assertThrows(ConfigValidationException.class, () -> PaymentListParser.parse(List.of("minecraft:iron_ingot")));
    }

    @Test
    void rejectsNonPositiveValue()
    {
        assertThrows(ConfigValidationException.class, () -> PaymentListParser.parse(List.of("minecraft:iron_ingot=0")));
    }

    @Test
    void rejectsNonNumericValue()
    {
        assertThrows(ConfigValidationException.class, () -> PaymentListParser.parse(List.of("minecraft:iron_ingot=lots")));
    }
}

package org.pixelfire.nationwars.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentValuationTest
{
    private static final List<PaymentEntry> ENTRIES = List.of(
            new PaymentEntry("minecraft:iron_ingot", false, 1),
            new PaymentEntry("minecraft:diamond", false, 9),
            new PaymentEntry("forge:gems/ruby", true, 12));

    @Test
    void exactItemIdMatches()
    {
        assertEquals(OptionalLong.of(1), PaymentValuation.valueOf("minecraft:iron_ingot", tag -> false, ENTRIES));
    }

    @Test
    void tagMatchesWhenNoExactIdMatches()
    {
        assertEquals(OptionalLong.of(12), PaymentValuation.valueOf("somemod:star_ruby", tag -> tag.equals("forge:gems/ruby"), ENTRIES));
    }

    @Test
    void exactIdIsCheckedBeforeTags()
    {
        final List<PaymentEntry> both = List.of(
                new PaymentEntry("minecraft:iron_ingot", false, 1),
                new PaymentEntry("forge:ingots/iron", true, 999));

        assertEquals(OptionalLong.of(1), PaymentValuation.valueOf("minecraft:iron_ingot", tag -> true, both));
    }

    @Test
    void unpricedItemHasNoValue()
    {
        assertTrue(PaymentValuation.valueOf("minecraft:cobblestone", tag -> false, ENTRIES).isEmpty());
    }

    @Test
    void blockFormOfAnIngotIsMultiplied()
    {
        assertEquals(OptionalLong.of(9), PaymentValuation.blockFormValueOf("minecraft:iron_block", tag -> false, ENTRIES, 9));
    }

    @Test
    void blockFormOfABareGemIsMultiplied()
    {
        assertEquals(OptionalLong.of(81), PaymentValuation.blockFormValueOf("minecraft:diamond_block", tag -> false, ENTRIES, 9));
    }

    @Test
    void nonBlockItemHasNoBlockFormValue()
    {
        assertTrue(PaymentValuation.blockFormValueOf("minecraft:iron_ingot", tag -> false, ENTRIES, 9).isEmpty());
    }

    @Test
    void blockWithNoPricedBaseHasNoValue()
    {
        assertTrue(PaymentValuation.blockFormValueOf("minecraft:redstone_block", tag -> false, ENTRIES, 9).isEmpty());
    }
}

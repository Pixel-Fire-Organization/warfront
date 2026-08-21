package org.pixelfire.nationwars.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionEligibilityTest
{
    private static final List<String> ALLOWED = List.of("minecraft:overworld");
    private static final List<String> BLOCKED = List.of();

    @Test
    void overworldIsEligibleByDefault()
    {
        assertTrue(DimensionEligibility.isEligible(true, false, "minecraft:overworld", ALLOWED, BLOCKED));
    }

    @Test
    void netherIsIneligibleBecauseItHasACeiling()
    {
        assertFalse(DimensionEligibility.isEligible(true, true, "minecraft:the_nether", ALLOWED, BLOCKED));
    }

    @Test
    void endIsIneligibleBecauseItHasNoSkyLight()
    {
        assertFalse(DimensionEligibility.isEligible(false, false, "minecraft:the_end", ALLOWED, BLOCKED));
    }

    @Test
    void aDimensionNotOnTheAllowedListIsIneligible()
    {
        assertFalse(DimensionEligibility.isEligible(true, false, "somemod:custom_sky_dimension", ALLOWED, BLOCKED));
    }

    @Test
    void aBlockedDimensionIsIneligibleEvenIfOtherwiseAllowed()
    {
        final List<String> allowed = List.of("minecraft:overworld", "somemod:custom");
        final List<String> blocked = List.of("somemod:custom");

        assertFalse(DimensionEligibility.isEligible(true, false, "somemod:custom", allowed, blocked));
    }
}

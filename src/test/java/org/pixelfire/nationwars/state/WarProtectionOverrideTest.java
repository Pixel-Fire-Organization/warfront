package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarProtectionOverrideTest
{
    @Test
    void allowedWhenEveryConditionHolds()
    {
        assertTrue(WarProtectionOverride.isAllowed(new WarProtectionContext(true, true, true, true)));
    }

    @Test
    void deniedIfWarNotActive()
    {
        assertFalse(WarProtectionOverride.isAllowed(new WarProtectionContext(false, true, true, true)));
    }

    @Test
    void deniedIfNotOpposingCoalitions()
    {
        assertFalse(WarProtectionOverride.isAllowed(new WarProtectionContext(true, false, true, true)));
    }

    @Test
    void deniedIfChunkNotInTargetCityClaims()
    {
        assertFalse(WarProtectionOverride.isAllowed(new WarProtectionContext(true, true, false, true)));
    }

    @Test
    void deniedIfActionNotOverridden()
    {
        assertFalse(WarProtectionOverride.isAllowed(new WarProtectionContext(true, true, true, false)));
    }
}

package org.pixelfire.nationwars.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceRequirementTest
{
    @Test
    void aboveTheSurfaceMeetsTheRequirement()
    {
        assertTrue(SurfaceRequirement.isMet(true, 70, 65, 4));
    }

    @Test
    void withinToleranceBelowTheSurfaceStillMeetsIt()
    {
        assertTrue(SurfaceRequirement.isMet(true, 61, 65, 4));
    }

    @Test
    void beyondToleranceBelowTheSurfaceFailsIt()
    {
        assertFalse(SurfaceRequirement.isMet(true, 60, 65, 4));
    }

    @Test
    void requirementIsSkippedWhenDisabled()
    {
        assertTrue(SurfaceRequirement.isMet(false, 0, 65, 4));
    }
}

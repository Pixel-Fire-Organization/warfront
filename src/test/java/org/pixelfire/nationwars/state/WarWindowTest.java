package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarWindowTest
{
    @Test
    void emptyConfigAlwaysAllows()
    {
        assertTrue(WarWindow.isWithin("", "", 0));
        assertTrue(WarWindow.isWithin("", "06:00", 700));
    }

    @Test
    void simpleWindowWithinSameDay()
    {
        assertTrue(WarWindow.isWithin("18:00", "22:00", 19 * 60));
        assertFalse(WarWindow.isWithin("18:00", "22:00", 10 * 60));
    }

    @Test
    void windowBoundariesAreStartInclusiveEndExclusive()
    {
        assertTrue(WarWindow.isWithin("18:00", "22:00", 18 * 60));
        assertFalse(WarWindow.isWithin("18:00", "22:00", 22 * 60));
    }

    @Test
    void windowWrappingPastMidnight()
    {
        assertTrue(WarWindow.isWithin("22:00", "06:00", 23 * 60));
        assertTrue(WarWindow.isWithin("22:00", "06:00", 2 * 60));
        assertFalse(WarWindow.isWithin("22:00", "06:00", 12 * 60));
    }
}

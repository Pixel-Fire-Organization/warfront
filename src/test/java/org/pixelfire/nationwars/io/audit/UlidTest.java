package org.pixelfire.nationwars.io.audit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UlidTest
{
    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    void generatedUlidIsTwentySixCrockfordBase32Characters()
    {
        final String ulid = Ulid.generate();

        assertEquals(26, ulid.length());
        for (final char c : ulid.toCharArray())
        {
            assertTrue(CROCKFORD_BASE32.indexOf(c) >= 0, "unexpected character '" + c + "' in " + ulid);
        }
    }

    @Test
    void timestampRoundTrips()
    {
        final long now = System.currentTimeMillis();

        assertEquals(now, Ulid.timestampMillis(Ulid.generate(now)));
        assertEquals(0L, Ulid.timestampMillis(Ulid.generate(0L)));
    }

    @Test
    void rejectsATimestampOutOfRange()
    {
        assertThrows(IllegalArgumentException.class, () -> Ulid.generate(-1L));
        assertThrows(IllegalArgumentException.class, () -> Ulid.generate(1L << 48));
    }

    @Test
    void rejectsAStringOfTheWrongLength()
    {
        assertThrows(IllegalArgumentException.class, () -> Ulid.timestampMillis("tooshort"));
    }

    @Test
    void twoUlidsAtIncreasingTimestampsSortInTimeOrder()
    {
        final String earlier = Ulid.generate(1_000L);
        final String later = Ulid.generate(2_000L);

        assertTrue(earlier.compareTo(later) < 0, earlier + " should sort before " + later);
    }

    @Test
    void generatedUlidsAreUnique()
    {
        final Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++)
        {
            assertTrue(seen.add(Ulid.generate()), "duplicate ULID generated");
        }
    }
}

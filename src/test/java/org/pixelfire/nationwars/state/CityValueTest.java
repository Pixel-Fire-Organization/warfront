package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CityValueTest
{
    @Test
    void combinesAllThreeWeightedTerms()
    {
        final double value = CityValue.of(512L, 100L, 8, 1.0, 0.5, 10.0);

        assertEquals(512.0 + 50.0 + 80.0, value, 0.0001);
    }

    @Test
    void zeroWeightsZeroOutTheirTerm()
    {
        final double value = CityValue.of(512L, 100L, 8, 0.0, 0.0, 0.0);

        assertEquals(0.0, value, 0.0001);
    }
}

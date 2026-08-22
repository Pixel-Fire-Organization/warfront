package org.pixelfire.nationwars.state;

/**
 * The war-score price of a city, spent by whoever receives it in a {@code TransferCity} clause (or pays
 * it as tribute, priced the same way).
 */
public final class CityValue
{
    private CityValue()
    {
    }

    public static double of(final long tierCost, final long bankedPayment, final int checkpointCount,
            final double tierWeight, final double bankWeight, final double checkpointWeight)
    {
        return tierCost * tierWeight + bankedPayment * bankWeight + checkpointCount * checkpointWeight;
    }
}

package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeaceSettlementTest
{
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static PeaceSettlement withRatifications(final Map<UUID, RatificationState> ratifications)
    {
        return new PeaceSettlement(UUID.randomUUID(), UUID.randomUUID(), A, List.of(), 0L, 0L, ratifications, 0);
    }

    @Test
    void fullyRatifiedWhenEveryoneHasSigned()
    {
        assertTrue(withRatifications(Map.of(A, RatificationState.SIGNED, B, RatificationState.SIGNED)).fullyRatified());
    }

    @Test
    void notFullyRatifiedWhileSomeonePending()
    {
        assertFalse(withRatifications(Map.of(A, RatificationState.SIGNED, B, RatificationState.PENDING)).fullyRatified());
    }

    @Test
    void anyRejectedDetectsARejection()
    {
        assertTrue(withRatifications(Map.of(A, RatificationState.SIGNED, B, RatificationState.REJECTED)).anyRejected());
    }

    @Test
    void noRejectionWhenNoneRejected()
    {
        assertFalse(withRatifications(Map.of(A, RatificationState.SIGNED, B, RatificationState.PENDING)).anyRejected());
    }
}

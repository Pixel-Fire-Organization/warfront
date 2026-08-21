package org.pixelfire.nationwars.state;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param proposedByNationId {@code null} for a staff-imposed settlement
 * @param ratifications      one entry per required signatory: both coalition primaries, plus the leader
 *                           of any non-primary member whose city a {@code TransferCity} clause names —
 *                           a primary negotiates for the coalition but cannot sign away an ally's property
 * @param rejectionCount     rejected offers so far in this negotiation, toward {@code deadlockRejections}
 */
public record PeaceSettlement(
        UUID settlementId,
        UUID warId,
        UUID proposedByNationId,
        List<StagedClause> clauses,
        long createdAt,
        long expiresAt,
        Map<UUID, RatificationState> ratifications,
        int rejectionCount)
{
    public boolean fullyRatified()
    {
        return ratifications.values().stream().allMatch(state -> state == RatificationState.SIGNED);
    }

    public boolean anyRejected()
    {
        return ratifications.values().stream().anyMatch(state -> state == RatificationState.REJECTED);
    }
}

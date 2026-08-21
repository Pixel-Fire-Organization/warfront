package org.pixelfire.nationwars.state;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @param primaryNationId the declarer (attackers) or original target (defenders); leads negotiation
 */
public record Coalition(Set<UUID> members, Map<UUID, PendingEntry> pendingMembers, UUID primaryNationId)
{
    public static Coalition ofPrimary(final UUID primaryNationId)
    {
        return new Coalition(Set.of(primaryNationId), Map.of(), primaryNationId);
    }
}

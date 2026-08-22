package org.pixelfire.nationwars.state;

public record WarJoinContext(
        boolean senderIsNationOwner,
        boolean alreadyInThisWar,
        boolean warJoinable,
        boolean unsettledWarAlreadyExists,
        long now,
        long cooldownExpiresAt,
        boolean joinerLocked,
        boolean joinerAtWarCap)
{
}

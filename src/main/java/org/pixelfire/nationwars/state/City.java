package org.pixelfire.nationwars.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

/**
 * @param cityId               survives ownership changes
 * @param ownerNationId         changes only at settlement
 * @param founderNationId       immutable
 * @param tier                  index into the configured tier list; 0 is tier 1
 * @param bankedPayment         progress toward the next tier upgrade
 * @param occupiedByNationId    non-null iff {@code state == OCCUPIED}
 * @param pendingDisbandAt      0 if no disbandment is pending
 * @param dormantSince          when this city entered {@code DORMANT}, 0 if not currently dormant; not
 *                              part of the original data model, added because the
 *                              {@code dormantCityExpiry} removal can't be timed without it
 */
public record City(
        UUID cityId,
        String name,
        UUID ownerNationId,
        UUID founderNationId,
        ResourceKey<Level> dimension,
        BlockPos corePos,
        int tier,
        long bankedPayment,
        Set<UUID> checkpointIds,
        CityState state,
        UUID occupiedByNationId,
        long occupiedSince,
        long occupationLockUntil,
        long foundedAt,
        long lastTransferAt,
        int transferCount,
        long pendingDisbandAt,
        long dormantSince)
{
}

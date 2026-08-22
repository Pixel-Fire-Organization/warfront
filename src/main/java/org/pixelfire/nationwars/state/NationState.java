package org.pixelfire.nationwars.state;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @param capitalCityId     {@code null} until the founding nation designates one
 * @param warCooldowns      opponent nation id to the earliest time that nation may be declared on again
 * @param lastCityFoundedAt 0 if this nation has never founded a city
 * @param lockedByWarId     non-null while this nation is frozen pending a settlement
 */
public record NationState(
        UUID nationId,
        Set<UUID> cityIds,
        UUID capitalCityId,
        Set<UUID> activeWarIds,
        Map<UUID, Long> warCooldowns,
        long lastCityFoundedAt,
        UUID lockedByWarId)
{
    public static NationState empty(final UUID nationId)
    {
        return new NationState(nationId, Set.of(), null, Set.of(), Map.of(), 0L, null);
    }
}

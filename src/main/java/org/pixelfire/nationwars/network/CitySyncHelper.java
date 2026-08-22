package org.pixelfire.nationwars.network;

import net.minecraft.server.MinecraftServer;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;

/**
 * Broadcasts a fresh {@link SyncCityPacket} for one city — the single call site every mutation of a
 * {@link City} record that should reach clients funnels through, so "held/total checkpoints" is
 * computed the same way everywhere rather than duplicated per caller.
 */
public final class CitySyncHelper
{
    private CitySyncHelper()
    {
    }

    public static void broadcast(final MinecraftServer server, final NationRegistry registry, final City city)
    {
        final int held = (int) city.checkpointIds().stream()
                .map(registry.checkpoints()::get)
                .filter(cp -> cp != null && cp.holderNationId().equals(city.ownerNationId()))
                .count();
        NationWarsNetwork.broadcast(server, SyncCityPacket.of(city, held, city.checkpointIds().size()));
    }
}

package org.pixelfire.nationwars.war;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.network.SyncCoalitionPacket;
import org.pixelfire.nationwars.network.SyncWarScorePacket;
import org.pixelfire.nationwars.network.SyncWarStatePacket;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.world.OpacNations;

/**
 * Broadcasts a war's phase/deadline and coalition membership to every online player (not
 * privacy-sensitive, unlike war score), and each belligerent nation's own war score to just its own
 * online members.
 */
public final class WarStateSync
{
    private WarStateSync()
    {
    }

    public static void broadcastWarAndCoalitions(final MinecraftServer server, final War war)
    {
        NationWarsNetwork.broadcast(server, SyncWarStatePacket.of(war));
        NationWarsNetwork.broadcast(server, SyncCoalitionPacket.of(war.warId(), true, war.attackers()));
        NationWarsNetwork.broadcast(server, SyncCoalitionPacket.of(war.warId(), false, war.defenders()));
    }

    public static void sendWarScores(final MinecraftServer server, final War war)
    {
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            final var nation = OpacNations.nationOf(server, player);
            if (nation == null)
            {
                continue;
            }
            final boolean belligerent = war.attackers().members().contains(nation.nationId())
                    || war.defenders().members().contains(nation.nationId());
            if (belligerent)
            {
                final long score = war.warScore().getOrDefault(nation.nationId(), 0L);
                NationWarsNetwork.sendTo(player, SyncWarScorePacket.of(war.warId(), score));
            }
        }
    }
}

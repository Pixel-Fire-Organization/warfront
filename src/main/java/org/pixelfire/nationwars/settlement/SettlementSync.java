package org.pixelfire.nationwars.settlement;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.network.OpenPeaceDealPacket;
import org.pixelfire.nationwars.network.SyncSettlementPacket;
import org.pixelfire.nationwars.state.PeaceSettlement;
import org.pixelfire.nationwars.world.OpacNations;

/**
 * Sends a settlement to every online player belonging to one of its signatory nations —
 * {@link OpenPeaceDealPacket} opens the peace-deal screen fresh, {@link SyncSettlementPacket} just
 * updates ratification progress on a screen already open.
 */
final class SettlementSync
{
    private SettlementSync()
    {
    }

    static void sendOpenDeal(final MinecraftServer server, final PeaceSettlement settlement)
    {
        final var packet = OpenPeaceDealPacket.of(settlement);
        forEachSignatoryOnline(server, settlement, player -> NationWarsNetwork.sendTo(player, packet));
    }

    static void sendProgress(final MinecraftServer server, final PeaceSettlement settlement)
    {
        final var packet = SyncSettlementPacket.of(settlement);
        forEachSignatoryOnline(server, settlement, player -> NationWarsNetwork.sendTo(player, packet));
    }

    private static void forEachSignatoryOnline(final MinecraftServer server, final PeaceSettlement settlement,
            final java.util.function.Consumer<ServerPlayer> action)
    {
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            final var nation = OpacNations.nationOf(server, player);
            if (nation != null && settlement.ratifications().containsKey(nation.nationId()))
            {
                action.accept(player);
            }
        }
    }
}

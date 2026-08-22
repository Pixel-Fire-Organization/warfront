package org.pixelfire.nationwars.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.war.WarDeclarationService;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side handling for every C2S packet — each one re-runs the exact same service call and
 * preconditions its command-fallback equivalent does; nothing here trusts the client further than
 * that. Reached only after {@link NationWarsNetwork}'s rate limiter has already accepted the packet.
 */
final class ServerPacketHandlers
{
    private ServerPacketHandlers()
    {
    }

    static void handleRequestCityInfo(final ServerPlayer player, final RequestCityInfoPacket packet)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final City city = NegotiationService.findCityByName(registry, packet.cityName());
        if (city == null)
        {
            return;
        }
        final int held = (int) city.checkpointIds().stream()
                .map(registry.checkpoints()::get)
                .filter(cp -> cp != null && cp.holderNationId().equals(city.ownerNationId()))
                .count();
        NationWarsNetwork.sendTo(player, SyncCityPacket.of(city, held, city.checkpointIds().size()));
    }

    static void handleDeclareWar(final ServerPlayer player, final DeclareWarPacket packet)
    {
        final UUID targetId = OpacNations.findNationByName(player.getServer(), packet.targetNationName());
        WarDeclarationService.declare(player.getServer(), player, targetId)
                .ifPresent(failure -> player.sendSystemMessage(Component.literal(failure.message())));
    }

    static void handleProposeSettlement(final ServerPlayer player, final ProposeSettlementPacket packet)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final War war = registry.wars().get(packet.warId());
        final NationSnapshot nation = OpacNations.nationOf(player.getServer(), player);
        if (war == null || nation == null)
        {
            return;
        }
        NegotiationService.send(player.getServer(), registry, war, nation.nationId(), packet.clauses())
                .ifPresent(failure -> player.sendSystemMessage(Component.literal(failure)));
    }

    static void handleSettlementResponse(final ServerPlayer player, final SettlementResponsePacket packet)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final War war = registry.wars().get(packet.warId());
        final NationSnapshot nation = OpacNations.nationOf(player.getServer(), player);
        if (war == null || nation == null)
        {
            return;
        }
        final Optional<String> failure = packet.accept()
                ? NegotiationService.accept(player.getServer(), registry, war, nation.nationId())
                : NegotiationService.reject(player.getServer(), registry, war, nation.nationId());
        failure.ifPresent(message -> player.sendSystemMessage(Component.literal(message)));
    }
}

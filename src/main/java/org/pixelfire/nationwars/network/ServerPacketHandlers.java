package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.settlement.NegotiationService;
import org.pixelfire.nationwars.state.City;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.war.WarDeclarationService;
import org.pixelfire.nationwars.world.OpacNations;
import org.pixelfire.nationwars.world.OpacNations.NationSnapshot;
import xaero.pac.common.parties.party.member.PartyMemberRank;

import java.util.List;
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

    static void handleRenameCity(final ServerPlayer player, final RenameCityPacket packet)
    {
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final City city = registry.cities().values().stream()
                .filter(c -> c.corePos().equals(packet.corePos()) && c.dimension().equals(player.level().dimension()))
                .findFirst().orElse(null);
        if (city == null)
        {
            return;
        }
        final NationSnapshot nation = OpacNations.nationOf(player.getServer(), player);
        if (nation == null || !nation.nationId().equals(city.ownerNationId()))
        {
            player.sendSystemMessage(Component.literal("Only citizens of " + city.name() + "'s nation may rename it."));
            return;
        }
        final PartyMemberRank requiredRank = parseRank(NationWarsConfig.CITY_RENAME_RANK.get());
        if (nation.rankOrdinal() < requiredRank.ordinal())
        {
            player.sendSystemMessage(Component.literal("Your rank is too low to rename this city."));
            return;
        }

        final String newName = packet.newName().trim();
        if (newName.length() < 3 || newName.length() > 24)
        {
            player.sendSystemMessage(Component.literal("City names must be between 3 and 24 characters."));
            return;
        }
        final City existing = NegotiationService.findCityByName(registry, newName);
        if (existing != null && !existing.cityId().equals(city.cityId()))
        {
            player.sendSystemMessage(Component.literal("A city named \"" + newName + "\" already exists."));
            return;
        }

        final String oldName = city.name();
        registry.stripedLocks().withLocks(() ->
        {
            final City current = registry.cities().get(city.cityId());
            if (current != null)
            {
                registry.cities().put(city.cityId(), withName(current, newName));
            }
        }, city.cityId());

        final CompoundTag before = new CompoundTag();
        before.putString("name", oldName);
        final CompoundTag after = new CompoundTag();
        after.putString("name", newName);
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                player.getUUID(), player.getGameProfile().getName(), nation.nationId(), ActorRole.MODERATOR,
                AuditSource.GUI, ResourceLocation.tryBuild(NationWarsMod.MODID, "city_renamed"),
                List.of(city.cityId()), before, after, false));

        CitySyncHelper.broadcast(player.getServer(), registry, registry.cities().get(city.cityId()));
        player.sendSystemMessage(Component.literal("Renamed " + oldName + " to " + newName + "."));
    }

    private static City withName(final City city, final String name)
    {
        return new City(city.cityId(), name, city.ownerNationId(), city.founderNationId(), city.dimension(),
                city.corePos(), city.tier(), city.bankedPayment(), city.checkpointIds(), city.state(), city.occupiedByNationId(),
                city.occupiedSince(), city.occupationLockUntil(), city.foundedAt(), city.lastTransferAt(),
                city.transferCount(), city.pendingDisbandAt(), city.dormantSince());
    }

    private static PartyMemberRank parseRank(final String name)
    {
        try
        {
            return PartyMemberRank.valueOf(name);
        }
        catch (final IllegalArgumentException e)
        {
            return PartyMemberRank.MODERATOR;
        }
    }
}

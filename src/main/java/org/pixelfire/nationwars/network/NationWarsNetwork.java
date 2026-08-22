package org.pixelfire.nationwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.client.ClientPacketHandlers;
import org.pixelfire.nationwars.config.NationWarsConfig;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@code nationwars:main} channel: every S2C sync/effect packet and every C2S packet a future
 * client GUI needs, registered here in one place so the id sequence (and therefore wire
 * compatibility) is obvious at a glance. C2S handling re-validates fully server-side and is
 * rate-limited via {@link #RATE_LIMITER} before touching any service — a client is never trusted
 * further than a chat command typed by the same player would be.
 *
 * <p>S2C handlers are passed as {@code Supplier<Consumer<T>>}, not a plain {@code Consumer<T>}: the
 * method reference into {@link ClientPacketHandlers} must not resolve until inside the {@code
 * Dist.CLIENT}-guarded lambda {@link DistExecutor#unsafeRunWhenOn} runs, or a dedicated server would
 * try to verify a method whose body touches client-only classes.
 */
public final class NationWarsNetwork
{
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryBuild(NationWarsMod.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static final PacketRateLimiter RATE_LIMITER = new PacketRateLimiter();

    private NationWarsNetwork()
    {
    }

    public static void register()
    {
        int id = 0;

        registerS2C(SyncCityPacket.class, SyncCityPacket::decode, id++, () -> ClientPacketHandlers::handleSyncCity);
        registerS2C(SyncCheckpointStatePacket.class, SyncCheckpointStatePacket::decode, id++,
                () -> ClientPacketHandlers::handleSyncCheckpointState);
        registerS2C(SyncWarStatePacket.class, SyncWarStatePacket::decode, id++, () -> ClientPacketHandlers::handleSyncWarState);
        registerS2C(SyncCoalitionPacket.class, SyncCoalitionPacket::decode, id++, () -> ClientPacketHandlers::handleSyncCoalition);
        registerS2C(SyncWarScorePacket.class, SyncWarScorePacket::decode, id++, () -> ClientPacketHandlers::handleSyncWarScore);
        registerS2C(SyncReadinessPacket.class, SyncReadinessPacket::decode, id++, () -> ClientPacketHandlers::handleSyncReadiness);
        registerS2C(SyncCombatTagPacket.class, SyncCombatTagPacket::decode, id++, () -> ClientPacketHandlers::handleSyncCombatTag);
        registerS2C(SyncEvasionWarningPacket.class, SyncEvasionWarningPacket::decode, id++,
                () -> ClientPacketHandlers::handleSyncEvasionWarning);
        registerS2C(OpenPeaceDealPacket.class, OpenPeaceDealPacket::decode, id++, () -> ClientPacketHandlers::handleOpenPeaceDeal);
        registerS2C(SyncSettlementPacket.class, SyncSettlementPacket::decode, id++, () -> ClientPacketHandlers::handleSyncSettlement);
        registerS2C(CheckpointEffectPacket.class, CheckpointEffectPacket::decode, id++, () -> ClientPacketHandlers::handleCheckpointEffect);

        registerC2S(RequestCityInfoPacket.class, RequestCityInfoPacket::decode, id++, ServerPacketHandlers::handleRequestCityInfo);
        registerC2S(DeclareWarPacket.class, DeclareWarPacket::decode, id++, ServerPacketHandlers::handleDeclareWar);
        registerC2S(ProposeSettlementPacket.class, ProposeSettlementPacket::decode, id++, ServerPacketHandlers::handleProposeSettlement);
        registerC2S(SettlementResponsePacket.class, SettlementResponsePacket::decode, id, ServerPacketHandlers::handleSettlementResponse);
    }

    private static <T extends NationWarsPacket> void registerS2C(final Class<T> type, final Function<FriendlyByteBuf, T> decoder,
            final int id, final Supplier<Consumer<T>> clientHandlerSupplier)
    {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NationWarsPacket::encode)
                .decoder(decoder)
                .consumerMainThread((msg, ctxSupplier) ->
                {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientHandlerSupplier.get().accept(msg));
                    ctxSupplier.get().setPacketHandled(true);
                })
                .add();
    }

    private static <T extends NationWarsPacket> void registerC2S(final Class<T> type, final Function<FriendlyByteBuf, T> decoder,
            final int id, final BiConsumer<ServerPlayer, T> serverHandler)
    {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NationWarsPacket::encode)
                .decoder(decoder)
                .consumerMainThread((msg, ctxSupplier) ->
                {
                    final NetworkEvent.Context ctx = ctxSupplier.get();
                    final ServerPlayer player = ctx.getSender();
                    ctx.setPacketHandled(true);
                    if (player == null)
                    {
                        return;
                    }
                    final long cooldownMs = NationWarsConfig.C2S_PACKET_RATE_LIMIT_MS.get();
                    if (!RATE_LIMITER.tryAccept(player.getUUID(), type, System.currentTimeMillis(), cooldownMs))
                    {
                        return;
                    }
                    serverHandler.accept(player, msg);
                })
                .add();
    }

    /**
     * Sends {@code message} to {@code player} if their client has this channel open — a vanilla client
     * or one with the mod absent is skipped silently rather than kicked, per the vanilla-fallback rule.
     */
    public static void sendTo(final ServerPlayer player, final Object message)
    {
        if (!CHANNEL.isRemotePresent(player.connection.connection))
        {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with((Supplier<ServerPlayer>) () -> player), message);
    }

    public static void sendToServer(final Object message)
    {
        CHANNEL.sendToServer(message);
    }

    /**
     * Sends {@code message} to every online player whose client has this channel open. City and
     * coalition state aren't privacy-sensitive the way readiness/war score are, so these packets skip
     * per-player targeting.
     */
    public static void broadcast(final MinecraftServer server, final Object message)
    {
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            sendTo(player, message);
        }
    }
}

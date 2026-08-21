package org.pixelfire.nationwars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.pixelfire.nationwars.network.CheckpointEffectPacket;
import org.pixelfire.nationwars.network.OpenPeaceDealPacket;
import org.pixelfire.nationwars.network.SyncCheckpointStatePacket;
import org.pixelfire.nationwars.network.SyncCityPacket;
import org.pixelfire.nationwars.network.SyncCoalitionPacket;
import org.pixelfire.nationwars.network.SyncCombatTagPacket;
import org.pixelfire.nationwars.network.SyncEvasionWarningPacket;
import org.pixelfire.nationwars.network.SyncReadinessPacket;
import org.pixelfire.nationwars.network.SyncSettlementPacket;
import org.pixelfire.nationwars.network.SyncWarScorePacket;
import org.pixelfire.nationwars.network.SyncWarStatePacket;

/**
 * Every S2C packet's actual client-side effect: update {@link ClientNationCache}, and for the two
 * packets that need more than a cache write, act on it (open the peace-deal screen; play the
 * shatter-and-reform particle burst). Only ever invoked from inside {@code
 * DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} in {@link org.pixelfire.nationwars.network.NationWarsNetwork}.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers
{
    private ClientPacketHandlers()
    {
    }

    public static void handleSyncCity(final SyncCityPacket packet)
    {
        ClientNationCache.putCity(packet);
    }

    public static void handleSyncCheckpointState(final SyncCheckpointStatePacket packet)
    {
        ClientCheckpointCache.putCheckpoint(packet);
    }

    public static void handleSyncWarState(final SyncWarStatePacket packet)
    {
        ClientNationCache.putWar(packet);
    }

    public static void handleSyncCoalition(final SyncCoalitionPacket packet)
    {
        ClientNationCache.putCoalition(packet);
    }

    public static void handleSyncWarScore(final SyncWarScorePacket packet)
    {
        ClientNationCache.putWarScore(packet);
    }

    public static void handleSyncReadiness(final SyncReadinessPacket packet)
    {
        ClientNationCache.setReadiness(packet);
    }

    public static void handleSyncCombatTag(final SyncCombatTagPacket packet)
    {
        ClientNationCache.setCombatTag(packet);
    }

    public static void handleSyncEvasionWarning(final SyncEvasionWarningPacket packet)
    {
        ClientNationCache.setEvasionWarning(packet, System.currentTimeMillis());
    }

    public static void handleOpenPeaceDeal(final OpenPeaceDealPacket packet)
    {
        ClientNationCache.setOpenDeal(packet);
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new PeaceDealScreen(packet)));
    }

    public static void handleSyncSettlement(final SyncSettlementPacket packet)
    {
        ClientNationCache.setSettlementProgress(packet);
    }

    public static void handleCheckpointEffect(final CheckpointEffectPacket packet)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return;
        }
        final var pos = packet.pos();
        for (int i = 0; i < 24; i++)
        {
            minecraft.level.addParticle(ParticleTypes.CRIT,
                    pos.getX() + 0.5 + (minecraft.level.random.nextDouble() - 0.5), pos.getY() + 1.0,
                    pos.getZ() + 0.5 + (minecraft.level.random.nextDouble() - 0.5),
                    (minecraft.level.random.nextDouble() - 0.5) * 0.3, minecraft.level.random.nextDouble() * 0.4,
                    (minecraft.level.random.nextDouble() - 0.5) * 0.3);
        }
    }
}

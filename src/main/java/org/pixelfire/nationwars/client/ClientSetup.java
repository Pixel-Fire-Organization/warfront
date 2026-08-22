package org.pixelfire.nationwars.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.pixelfire.nationwars.NationWarsMod;

/**
 * Client-only bootstrap: registers the HUD overlay, and clears {@link ClientNationCache}/{@link
 * ClientCheckpointCache} on disconnect so a fresh join to a different server never shows stale state
 * from the last one.
 */
@Mod.EventBusSubscriber(modid = NationWarsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup
{
    private ClientSetup()
    {
    }

    @SubscribeEvent
    public static void registerOverlays(final RegisterGuiOverlaysEvent event)
    {
        event.registerAboveAll("hud", new NationWarsHudOverlay());
    }

    @Mod.EventBusSubscriber(modid = NationWarsMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBusListeners
    {
        private ForgeBusListeners()
        {
        }

        @SubscribeEvent
        public static void onDisconnect(final ClientPlayerNetworkEvent.LoggingOut event)
        {
            ClientNationCache.clear();
            ClientCheckpointCache.clear();
        }
    }
}

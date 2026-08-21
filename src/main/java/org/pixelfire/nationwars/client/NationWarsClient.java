package org.pixelfire.nationwars.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.world.block.NationWarsMenus;

@Mod.EventBusSubscriber(modid = NationWarsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NationWarsClient
{
    private NationWarsClient()
    {
    }

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(NationWarsMenus.CITY_CORE.get(), CityCoreScreen::new));
    }
}

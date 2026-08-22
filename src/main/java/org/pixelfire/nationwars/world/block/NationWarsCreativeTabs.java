package org.pixelfire.nationwars.world.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.pixelfire.nationwars.NationWarsMod;

public final class NationWarsCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NationWarsMod.MODID);

    public static final RegistryObject<CreativeModeTab> NATIONWARS = CREATIVE_MODE_TABS.register("nationwars",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nationwars"))
                    .icon(() -> new ItemStack(NationWarsBlocks.CITY_CORE_ITEM.get()))
                    .displayItems((parameters, output) ->
                    {
                        output.accept(NationWarsBlocks.CITY_CORE_ITEM.get());
                        output.accept(NationWarsBlocks.CHECKPOINT_ITEM.get());
                    })
                    .build());

    private NationWarsCreativeTabs()
    {
    }

    /** Forces this class to load (and its {@code RegistryObject}s to be created) before registration fires. */
    public static void bootstrap()
    {
    }
}

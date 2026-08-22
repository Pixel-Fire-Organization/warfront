package org.pixelfire.nationwars.world.block;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.world.menu.CityCoreMenu;

public final class NationWarsMenus
{
    public static final RegistryObject<MenuType<CityCoreMenu>> CITY_CORE =
            NationWarsMod.MENU_TYPES.register("city_core",
                    () -> IForgeMenuType.create((windowId, inventory, data) -> new CityCoreMenu(windowId, inventory, data.readBlockPos())));

    private NationWarsMenus()
    {
    }

    /** Forces this class to load (and its {@code RegistryObject}s to be created) before registration fires. */
    public static void bootstrap()
    {
    }
}

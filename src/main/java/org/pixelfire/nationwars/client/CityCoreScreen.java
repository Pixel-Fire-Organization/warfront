package org.pixelfire.nationwars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.pixelfire.nationwars.state.CityState;
import org.pixelfire.nationwars.world.menu.CityCoreMenu;

/**
 * Minimal display of the synced tier/payment/checkpoint/state fields plus an upgrade button; no custom
 * texture yet, same placeholder-first approach the blocks themselves took.
 */
@OnlyIn(Dist.CLIENT)
public final class CityCoreScreen extends AbstractContainerScreen<CityCoreMenu>
{
    public CityCoreScreen(final CityCoreMenu menu, final Inventory inventory, final Component title)
    {
        super(menu, inventory, title);
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY)
    {
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
        graphics.drawString(font, "Tier " + (menu.tier() + 1), 8, 18, 0x404040, false);
        graphics.drawString(font, "Banked payment: " + menu.bankedPayment(), 8, 30, 0x404040, false);
        graphics.drawString(font, "Checkpoints: " + menu.checkpointCount(), 8, 42, 0x404040, false);
        graphics.drawString(font, "State: " + CityState.values()[menu.cityStateOrdinal()], 8, 54, 0x404040, false);
    }

    @Override
    protected void init()
    {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Upgrade"),
                        button -> Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, CityCoreMenu.UPGRADE_BUTTON_ID))
                .bounds(leftPos + 8, topPos + 70, 80, 20)
                .build());
    }
}

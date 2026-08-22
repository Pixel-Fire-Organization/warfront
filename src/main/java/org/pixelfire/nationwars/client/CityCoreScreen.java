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
        // No custom texture yet, same placeholder-first approach the blocks themselves took. Without
        // this the screen had no backdrop at all: the labels and the payment slot were drawn directly
        // over the unobstructed 3D world, hotbar, and JEI, and the slot itself had nothing behind it to
        // frame it as an actual slot.
        final int x = leftPos;
        final int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF0101010);
        graphics.fill(x, y, x + imageWidth, y + 1, 0xFF8B8B8B);
        graphics.fill(x, y, x + 1, y + imageHeight, 0xFF8B8B8B);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF000000);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF000000);

        // Frames the payment slot (menu-relative 80,35, standard 16x16 slot) so it reads as an actual
        // slot to deposit into rather than empty space.
        final int slotX = x + 80 - 1;
        final int slotY = y + 35 - 1;
        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF373737);
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
        graphics.drawString(font, "Tier " + (menu.tier() + 1), 8, 18, 0x404040, false);
        graphics.drawString(font, "Banked payment: " + menu.bankedPayment(), 8, 30, 0x404040, false);
        graphics.drawString(font, "Checkpoints: " + menu.checkpointCount(), 8, 42, 0x404040, false);
        graphics.drawString(font, "State: " + CityState.values()[menu.cityStateOrdinal()], 8, 54, 0x404040, false);
        graphics.drawString(font, "Deposit", 55, 39, 0x404040, false);
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

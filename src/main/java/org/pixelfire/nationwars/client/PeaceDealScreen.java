package org.pixelfire.nationwars.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.network.OpenPeaceDealPacket;
import org.pixelfire.nationwars.network.SettlementResponsePacket;
import org.pixelfire.nationwars.state.RatificationState;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.StagedClauseSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The dedicated-packet negotiation GUI the command fallback ({@code /war negotiate}) was always meant
 * to be backed by. Read-only beyond Accept/Reject — counter-offering and building a new draft stay on
 * the command fallback, since that flow already exists and duplicating it as GUI widgets isn't what
 * this screen is for.
 */
@OnlyIn(Dist.CLIENT)
public final class PeaceDealScreen extends Screen
{
    private final UUID warId;
    private final List<StagedClause> clauses;
    private final List<UUID> signatoryNationIds;
    private final List<RatificationState> signatoryStates;

    public PeaceDealScreen(final OpenPeaceDealPacket packet)
    {
        super(Component.literal("Peace Deal"));
        this.warId = packet.data().getUUID("warId");
        this.clauses = StagedClauseSnapshot.read(packet.data().getList("clauses", Tag.TAG_COMPOUND));
        this.signatoryNationIds = new ArrayList<>();
        this.signatoryStates = new ArrayList<>();
        for (final Tag element : packet.data().getList("ratifications", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) element;
            signatoryNationIds.add(entry.getUUID("nationId"));
            signatoryStates.add(RatificationState.valueOf(entry.getString("state")));
        }
    }

    @Override
    protected void init()
    {
        final int centerX = width / 2;
        addRenderableWidget(Button.builder(Component.literal("Accept"), b -> respond(true))
                .bounds(centerX - 105, height - 40, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reject"), b -> respond(false))
                .bounds(centerX + 5, height - 40, 100, 20).build());
    }

    private void respond(final boolean accept)
    {
        NationWarsNetwork.sendToServer(SettlementResponsePacket.of(warId, accept));
        onClose();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        int y = 45;
        graphics.drawString(font, "Terms:", 20, y, 0xFFFFFF);
        y += 14;
        for (final StagedClause clause : clauses)
        {
            graphics.drawString(font, "- " + describe(clause), 24, y, 0xCCCCCC);
            y += 12;
        }

        y += 10;
        graphics.drawString(font, "Ratifications:", 20, y, 0xFFFFFF);
        y += 14;
        for (int i = 0; i < signatoryNationIds.size(); i++)
        {
            graphics.drawString(font, "- " + signatoryNationIds.get(i) + ": " + signatoryStates.get(i), 24, y, 0xCCCCCC);
            y += 12;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String describe(final StagedClause clause)
    {
        final ResourceLocation id = clause.clauseTypeId();
        final String path = id.getPath();
        return switch (path)
        {
            case "transfer_city" -> "Transfer city " + clause.params().getUUID("cityId") + " to " + clause.params().getUUID("toNationId");
            case "tribute" -> "Tribute " + clause.params().getLong("value") + " from " + clause.params().getUUID("from")
                    + " to " + clause.params().getUUID("to");
            case "ceasefire" -> "Ceasefire " + clause.params().getLong("durationHours") + "h";
            case "release_occupation" -> "Release occupation of city " + clause.params().getUUID("cityId");
            default -> path;
        };
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}

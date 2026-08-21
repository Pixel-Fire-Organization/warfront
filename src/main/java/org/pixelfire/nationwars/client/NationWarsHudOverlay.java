package org.pixelfire.nationwars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.pixelfire.nationwars.network.SyncCityPacket;
import org.pixelfire.nationwars.network.SyncCombatTagPacket;
import org.pixelfire.nationwars.network.SyncEvasionWarningPacket;
import org.pixelfire.nationwars.network.SyncReadinessPacket;
import org.pixelfire.nationwars.network.SyncSettlementPacket;
import org.pixelfire.nationwars.network.SyncWarStatePacket;

import java.util.UUID;

/**
 * Every HUD element the spec lists in one overlay: per-war target cities held/total, occupation
 * countdowns, war deadline, ACTIVE/suspended indicator, own war score, and — while a settlement is open
 * — the lock banner and ratification progress; plus the standing indicators (shield, combat tag,
 * evasion warning). AFK is the client's own {@code manualAfk} choice and has no packet to reflect —
 * nothing server-side needs to tell a client it set itself AFK.
 */
@OnlyIn(Dist.CLIENT)
public final class NationWarsHudOverlay implements IGuiOverlay
{
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final long EVASION_WARNING_DISPLAY_MS = 15_000L;

    @Override
    public void render(final ForgeGui gui, final GuiGraphics graphics, final float partialTick, final int screenWidth, final int screenHeight)
    {
        int y = 4;
        for (final SyncWarStatePacket war : ClientNationCache.wars().values())
        {
            y = renderWar(graphics, war, y);
        }
        y = renderOccupationBadges(graphics, y);
        y = renderSettlementBanner(graphics, y);
        renderIndicators(graphics, screenHeight);
    }

    private int renderOccupationBadges(final GuiGraphics graphics, final int startY)
    {
        int y = startY;
        final Font font = Minecraft.getInstance().font;
        final long now = System.currentTimeMillis();
        for (final SyncCityPacket city : ClientNationCache.cities().values())
        {
            if (city.occupiedByNationId() == null)
            {
                continue;
            }
            final long remainingMs = Math.max(0L, city.occupationLockUntil() - now);
            graphics.drawString(font, city.name() + " OCCUPIED, lock " + (remainingMs / 60_000L) + "m remaining", 4, y, 0xFFAA00);
            y += 10;
        }
        return y;
    }

    private int renderWar(final GuiGraphics graphics, final SyncWarStatePacket war, final int startY)
    {
        final CompoundTag data = war.data();
        final UUID warId = data.getUUID("warId");
        int y = startY;

        final String phase = data.getString("phase");
        final long now = System.currentTimeMillis();
        final long remainingMs = Math.max(0L, data.getLong("warExpiresAt") - now);
        final long days = remainingMs / 86_400_000L;
        final long hours = (remainingMs / 3_600_000L) % 24;
        final boolean suspended = data.getLong("suspendedSince") > 0L;
        final Font font = Minecraft.getInstance().font;

        graphics.drawString(font,
                "War " + shortId(warId) + " [" + phase + (suspended ? "/SUSPENDED" : "/ACTIVE") + "] "
                        + days + "d " + hours + "h remaining, " + data.getInt("occupiedCities") + "/" + data.getInt("targetCities")
                        + " cities occupied, score " + ClientNationCache.ownWarScore(warId),
                4, y, TEXT_COLOR);
        y += 10;

        if ("SETTLEMENT".equals(phase) && data.getLong("settlementDeadline") > 0L)
        {
            final long settlementRemainingMs = Math.max(0L, data.getLong("settlementDeadline") - now);
            graphics.drawString(font, "  Settlement window: " + (settlementRemainingMs / 3_600_000L) + "h remaining", 4, y, TEXT_COLOR);
            y += 10;
        }

        final var attackers = ClientNationCache.attackerCoalition(warId);
        final var defenders = ClientNationCache.defenderCoalition(warId);
        if (attackers != null)
        {
            graphics.drawString(font, "  Attackers: " + attackers.data().getList("members", 8).size() + " nation(s)", 4, y, TEXT_COLOR);
            y += 10;
        }
        if (defenders != null)
        {
            graphics.drawString(font, "  Defenders: " + defenders.data().getList("members", 8).size() + " nation(s)", 4, y, TEXT_COLOR);
            y += 10;
        }
        return y + 2;
    }

    private int renderSettlementBanner(final GuiGraphics graphics, final int startY)
    {
        final SyncSettlementPacket progress = ClientNationCache.settlementProgress();
        if (progress == null || progress.data().getBoolean("fullyRatified") || progress.data().getBoolean("anyRejected"))
        {
            return startY;
        }
        graphics.drawString(Minecraft.getInstance().font, "Peace deal pending ratification — /war negotiate review", 4, startY, 0xFFFF55);
        return startY + 12;
    }

    private void renderIndicators(final GuiGraphics graphics, final int screenHeight)
    {
        int y = screenHeight - 14;
        final Font font = Minecraft.getInstance().font;

        final SyncReadinessPacket readiness = ClientNationCache.readiness();
        if (readiness != null && readiness.data().getLong("shieldTicksRemaining") > 0)
        {
            final long seconds = readiness.data().getLong("shieldTicksRemaining") / 20L;
            graphics.drawString(font, "Shield: " + seconds + "s", 4, y, 0x55FFFF);
            y -= 10;
        }

        final SyncCombatTagPacket combatTag = ClientNationCache.combatTag();
        if (combatTag != null && combatTag.data().getBoolean("tagged"))
        {
            final long seconds = combatTag.data().getLong("ticksRemaining") / 20L;
            graphics.drawString(font, "Combat tagged: " + seconds + "s", 4, y, 0xFF5555);
            y -= 10;
        }

        final SyncEvasionWarningPacket evasion = ClientNationCache.lastEvasionWarning();
        if (evasion != null && System.currentTimeMillis() - ClientNationCache.evasionWarningShownAt() < EVASION_WARNING_DISPLAY_MS)
        {
            final long remainingMinutes = evasion.data().getLong("remainingMs") / 60_000L;
            graphics.drawString(font, "EVASION WARNING (" + evasion.data().getInt("thresholdPercent") + "%): "
                    + remainingMinutes + "m remaining", 4, y, 0xFF0000);
        }
    }

    private static String shortId(final UUID id)
    {
        return id.toString().substring(0, 8);
    }
}

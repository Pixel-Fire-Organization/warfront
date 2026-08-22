package org.pixelfire.nationwars.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.compute.TickTimer;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.network.SyncEvasionWarningPacket;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.EvasionKey;
import org.pixelfire.nationwars.state.EvasionProgress;
import org.pixelfire.nationwars.state.EvasionTracker;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarPhase;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Advances every belligerent's evasion clock once a second while its war is {@code ACTIVE} or
 * {@code SUSPENDED} — the clock keeps running through suspension, only pausing while the war itself is
 * still in {@code PREPARATION} or already past {@code SETTLEMENT}. Attacker-side members are only
 * evaluated when {@code evasionAppliesToAttackers} is enabled; defenders always are, since the whole
 * point is that a targeted nation cannot dodge its own war.
 */
public final class EvasionTickListener
{
    private static final long STEP_MS = 1000L;
    private final TickTimer perfTimer = new TickTimer(64);
    private int tickCounter;

    public TickTimer perfTimer()
    {
        return perfTimer;
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;

        final long startNanos = System.nanoTime();
        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long participationMinimumMs = NationWarsConfig.WAR_PARTICIPATION_MINIMUM_SECONDS.get() * 1000L;
        final long evasionLimitMs = NationWarsConfig.WAR_EVASION_LIMIT_SECONDS.get() * 1000L;
        final boolean applyToAttackers = NationWarsConfig.EVASION_APPLIES_TO_ATTACKERS.get();

        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            if (war.phase() != WarPhase.ACTIVE && war.phase() != WarPhase.SUSPENDED)
            {
                continue;
            }
            evaluateSide(server, registry, war, war.defenders(), war.attackers(), participationMinimumMs, evasionLimitMs);
            if (applyToAttackers)
            {
                evaluateSide(server, registry, war, war.attackers(), war.defenders(), participationMinimumMs, evasionLimitMs);
            }
        }
        perfTimer.record(System.nanoTime() - startNanos);
    }

    private void evaluateSide(final MinecraftServer server, final NationRegistry registry, final War war, final Coalition ownSide,
            final Coalition otherSide, final long participationMinimumMs, final long evasionLimitMs)
    {
        final boolean opponentReady = coalitionReady(server, otherSide);
        for (final UUID nationId : new ArrayList<>(ownSide.members()))
        {
            evaluateNation(server, registry, war, nationId, opponentReady, participationMinimumMs, evasionLimitMs);
        }
    }

    private void evaluateNation(final MinecraftServer server, final NationRegistry registry, final War war, final UUID nationId,
            final boolean opponentReady, final long participationMinimumMs, final long evasionLimitMs)
    {
        final EvasionKey key = new EvasionKey(war.warId(), nationId);
        final EvasionTracker tracker = registry.evasionTrackers().getOrDefault(key, EvasionTracker.empty(war.warId(), nationId));
        final boolean nationReady = isNationReady(server, nationId);

        final EvasionTracker advanced = EvasionProgress.advance(tracker, nationReady, opponentReady, STEP_MS, participationMinimumMs);

        final int warningThreshold = EvasionProgress.nextWarningThreshold(advanced.evasionAccruedMs(), evasionLimitMs,
                tracker.lastWarnedThresholdPercent());
        final EvasionTracker toStore = warningThreshold > 0
                ? new EvasionTracker(advanced.warId(), advanced.nationId(), advanced.evasionAccruedMs(),
                        advanced.qualifyingReadyMs(), warningThreshold)
                : advanced;

        if (EvasionProgress.breached(toStore.evasionAccruedMs(), evasionLimitMs))
        {
            registry.evasionTrackers().remove(key);
            EvasionSurrenderService.applyEvasionSurrender(server, registry, war, nationId);
            return;
        }

        registry.evasionTrackers().put(key, toStore);
        if (warningThreshold > 0)
        {
            warnNation(server, war.warId(), nationId, warningThreshold, evasionLimitMs, toStore.evasionAccruedMs());
        }
    }

    private boolean coalitionReady(final MinecraftServer server, final Coalition coalition)
    {
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        return coalition.members().stream()
                .anyMatch(nationId -> Readiness.isNationReady(server, nationId, tracker, currentTick, afkThresholdTicks));
    }

    private boolean isNationReady(final MinecraftServer server, final UUID nationId)
    {
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        return Readiness.isNationReady(server, nationId, tracker, currentTick, afkThresholdTicks);
    }

    private void warnNation(final MinecraftServer server, final UUID warId, final UUID nationId, final int thresholdPercent,
            final long evasionLimitMs, final long evasionAccruedMs)
    {
        final var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(nationId);
        if (party == null)
        {
            return;
        }
        final long remainingMs = Math.max(0L, evasionLimitMs - evasionAccruedMs);
        final long remainingMinutes = remainingMs / 60_000L;
        final var packet = SyncEvasionWarningPacket.of(warId, thresholdPercent, remainingMs);
        party.getOnlineMemberStream().forEach((ServerPlayer player) ->
        {
            player.sendSystemMessage(Component.literal(
                    "Your nation is at " + thresholdPercent + "% of its evasion-surrender limit in an active war ("
                            + remainingMinutes + " minutes remaining). Field an hour of active presence to clear the clock."));
            NationWarsNetwork.sendTo(player, packet);
        });
    }
}

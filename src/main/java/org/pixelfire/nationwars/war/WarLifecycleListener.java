package org.pixelfire.nationwars.war;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.activity.Readiness;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the phase state machine off timers and readiness alone (no capture exists yet, so entering
 * {@code ACTIVE}/{@code SUSPENDED} is readiness-only): {@code PREPARATION} ends at {@code warPrepDuration}
 * into either state depending on readiness at that instant; {@code ACTIVE} degrades to {@code SUSPENDED}
 * after {@code presenceGraceDuration} of either side having no Ready player; {@code SUSPENDED} resumes to
 * {@code ACTIVE} the instant both sides are ready again, with no grace on the way back up.
 * {@code warExpiresAt} is checked every pass regardless of phase, since it never pauses.
 */
public final class WarLifecycleListener
{
    private final Map<UUID, Long> readinessFailingSince = new ConcurrentHashMap<>();
    private int tickCounter;

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

        final MinecraftServer server = event.getServer();
        final NationRegistry registry = NationWarsMod.get().getNationRegistry();
        final long now = System.currentTimeMillis();

        for (final War war : new ArrayList<>(registry.wars().values()))
        {
            evaluate(server, registry, war, now);
        }
    }

    private void evaluate(final MinecraftServer server, final NationRegistry registry, final War war, final long now)
    {
        if (war.phase() == WarPhase.ENDED || war.phase() == WarPhase.SETTLEMENT)
        {
            return;
        }
        if (!bothCoalitionsExist(server, war))
        {
            readinessFailingSince.remove(war.warId());
            WarTermination.conclude(registry, war, WarOutcome.VOID, now);
            return;
        }
        if (now >= war.warExpiresAt())
        {
            readinessFailingSince.remove(war.warId());
            WarTermination.conclude(registry, war, WarOutcome.TIMEOUT, now);
            return;
        }

        final boolean bothReady = bothCoalitionsReady(server, war, now);

        if (war.phase() == WarPhase.PREPARATION)
        {
            if (now >= war.declaredAt() + NationWarsConfig.WAR_PREP_DURATION_SECONDS.get() * 1000L)
            {
                setPhase(registry, war, bothReady ? WarPhase.ACTIVE : WarPhase.SUSPENDED, now);
            }
            return;
        }

        if (war.phase() == WarPhase.ACTIVE)
        {
            if (bothReady)
            {
                readinessFailingSince.remove(war.warId());
                return;
            }
            final long failingSince = readinessFailingSince.computeIfAbsent(war.warId(), id -> now);
            if (now - failingSince >= NationWarsConfig.PRESENCE_GRACE_DURATION_SECONDS.get() * 1000L)
            {
                readinessFailingSince.remove(war.warId());
                setPhase(registry, war, WarPhase.SUSPENDED, now);
            }
            return;
        }

        if (war.phase() == WarPhase.SUSPENDED && bothReady)
        {
            setPhase(registry, war, WarPhase.ACTIVE, now);
        }
    }

    private boolean bothCoalitionsExist(final MinecraftServer server, final War war)
    {
        return OpacNations.nationExists(server, war.attackers().primaryNationId())
                && OpacNations.nationExists(server, war.defenders().primaryNationId());
    }

    private boolean bothCoalitionsReady(final MinecraftServer server, final War war, final long now)
    {
        final var tracker = NationWarsMod.get().getActivityTracker();
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = server.overworld().getGameTime();
        return Readiness.isNationReady(server, war.attackers().primaryNationId(), tracker, currentTick, afkThresholdTicks)
                && Readiness.isNationReady(server, war.defenders().primaryNationId(), tracker, currentTick, afkThresholdTicks);
    }

    private static void setPhase(final NationRegistry registry, final War war, final WarPhase phase, final long now)
    {
        final long activeAt = phase == WarPhase.ACTIVE && war.activeAt() == 0L ? now : war.activeAt();
        final long suspendedSince = phase == WarPhase.SUSPENDED ? now : 0L;
        registry.stripedLocks().withLocks(() -> registry.wars().put(war.warId(), new War(war.warId(), war.attackers(),
                war.defenders(), phase, war.declaredAt(), activeAt, war.warExpiresAt(), war.targetCityIds(),
                war.occupiedCityIds(), war.warScore(), suspendedSince, war.contestedTimeMs(), war.settlementDeadline(),
                war.outcome())), war.warId());
    }
}

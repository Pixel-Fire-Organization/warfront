package org.pixelfire.nationwars.activity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.pixelfire.nationwars.config.NationWarsConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds every event that counts as activity into {@link ActivityTracker}. Movement is the only
 * one needing extra state (a per-player last-position cache) since Forge has no dedicated "player moved"
 * event; everything else is a direct event-to-{@code recordActivity} mapping. Known limitation:
 * position-delta movement can't distinguish a player's own walking from being shoved by water or a
 * piston while standing still — only the vehicle-passenger case (explicitly in scope) is filtered out.
 */
public final class ActivityEventListener
{
    private final ActivityTracker tracker;
    private final Map<UUID, Vec3> lastPosition = new ConcurrentHashMap<>();

    public ActivityEventListener(final ActivityTracker tracker)
    {
        this.tracker = tracker;
    }

    @SubscribeEvent
    public void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        final long shieldTicks = NationWarsConfig.LOGIN_SHIELD_DURATION_SECONDS.get() * 20L;
        tracker.onLogin(event.getEntity().getUUID(), currentTick(event.getEntity()), shieldTicks);
    }

    @SubscribeEvent
    public void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        tracker.onLogout(event.getEntity().getUUID());
        lastPosition.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (player.isPassenger() && player.getVehicle() != null && player.getVehicle().getControllingPassenger() != player)
        {
            return;
        }
        final Vec3 current = player.position();
        final Vec3 previous = lastPosition.put(player.getUUID(), current);
        if (previous != null && previous.distanceToSqr(current) >= NationWarsConfig.ACTIVITY_MOVE_THRESHOLD.get())
        {
            recordActivity(player.getUUID(), player);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(final BlockEvent.BreakEvent event)
    {
        recordActivityFromEntity(event.getPlayer());
    }

    @SubscribeEvent
    public void onBlockPlace(final BlockEvent.EntityPlaceEvent event)
    {
        recordActivityFromEntity(event.getEntity());
    }

    @SubscribeEvent
    public void onInteract(final PlayerInteractEvent event)
    {
        recordActivityFromEntity(event.getEntity());
    }

    @SubscribeEvent
    public void onHurt(final LivingHurtEvent event)
    {
        recordActivityFromEntity(event.getEntity());
        recordActivityFromEntity(event.getSource().getEntity());
    }

    @SubscribeEvent
    public void onContainerOpen(final PlayerContainerEvent.Open event)
    {
        recordActivityFromEntity(event.getEntity());
    }

    @SubscribeEvent
    public void onChat(final ServerChatEvent event)
    {
        recordActivity(event.getPlayer().getUUID(), event.getPlayer());
    }

    @SubscribeEvent
    public void onCommand(final CommandEvent event)
    {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)
        {
            recordActivity(player.getUUID(), player);
        }
    }

    private void recordActivityFromEntity(final Entity entity)
    {
        if (entity instanceof ServerPlayer player)
        {
            recordActivity(player.getUUID(), player);
        }
    }

    private void recordActivity(final UUID playerId, final Entity ticking)
    {
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long afkExitShieldTicks = NationWarsConfig.AFK_EXIT_SHIELD_SECONDS.get() * 20L;
        tracker.recordActivity(playerId, currentTick(ticking), afkThresholdTicks, afkExitShieldTicks);
    }

    private static long currentTick(final Entity entity)
    {
        return entity.level().getGameTime();
    }
}

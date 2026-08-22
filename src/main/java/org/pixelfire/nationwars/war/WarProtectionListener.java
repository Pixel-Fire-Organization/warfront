package org.pixelfire.nationwars.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.pixelfire.nationwars.state.ProtectionAction;
import org.pixelfire.nationwars.world.OpacNations;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un-cancels OPAC's own protection when the war-sanctioned {@link WarProtectionResolver} says the action
 * is allowed, at {@code LOWEST} priority with {@code receiveCanceled = true} so OPAC's (presumably
 * default-priority) cancellation has already run. Nothing here is stored: the check re-runs every event.
 *
 * <p>{@code ExplosionEvent.Detonate} isn't cancelable at all — verified against the target OPAC build's
 * source, its protection removes blocks from the event's own affected-block list rather than cancelling
 * the event. There's nothing to "un-cancel", so the override instead snapshots that list at {@code HIGH}
 * priority (before OPAC's default-priority handler runs) and, at {@code LOWEST}, re-adds any war-sanctioned
 * block OPAC's pass removed.
 */
public final class WarProtectionListener
{
    private final Map<Explosion, List<BlockPos>> explosionSnapshots = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBreak(final BlockEvent.BreakEvent event)
    {
        if (!event.isCanceled() || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        final UUID actorNationId = nationOf(level.getServer(), event.getPlayer());
        if (WarProtectionResolver.isOverridden(level.dimension(), event.getPos(), actorNationId, ProtectionAction.BLOCK_BREAK))
        {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final UUID actorNationId = nationOf(level.getServer(), player);
        if (WarProtectionResolver.isOverridden(level.dimension(), event.getPos(), actorNationId, ProtectionAction.BLOCK_PLACE))
        {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttackEntity(final AttackEntityEvent event)
    {
        if (!event.isCanceled())
        {
            return;
        }
        uncancelIfOverridden(event.getEntity(), event.getTarget(), event::setCanceled);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttack(final LivingAttackEvent event)
    {
        if (!event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer attacker))
        {
            return;
        }
        uncancelIfOverridden(attacker, event.getEntity(), event::setCanceled);
    }

    private void uncancelIfOverridden(final Entity attackerEntity, final Entity target, final java.util.function.Consumer<Boolean> setCanceled)
    {
        if (!(attackerEntity instanceof ServerPlayer attacker) || !(target.level() instanceof ServerLevel level))
        {
            return;
        }
        final ProtectionAction action = target instanceof Player ? ProtectionAction.PVP : ProtectionAction.ENTITY_DAMAGE;
        final UUID actorNationId = nationOf(level.getServer(), attacker);
        if (WarProtectionResolver.isOverridden(level.dimension(), target.blockPosition(), actorNationId, action))
        {
            setCanceled.accept(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onExplosionDetonateSnapshot(final ExplosionEvent.Detonate event)
    {
        explosionSnapshots.put(event.getExplosion(), List.copyOf(event.getAffectedBlocks()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonateRestore(final ExplosionEvent.Detonate event)
    {
        final List<BlockPos> before = explosionSnapshots.remove(event.getExplosion());
        if (before == null || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        final LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        if (!(source instanceof ServerPlayer player))
        {
            return;
        }
        final UUID actorNationId = nationOf(level.getServer(), player);
        final List<BlockPos> after = event.getAffectedBlocks();
        for (final BlockPos removed : before)
        {
            if (!after.contains(removed)
                    && WarProtectionResolver.isOverridden(level.dimension(), removed, actorNationId, ProtectionAction.EXPLOSIONS))
            {
                after.add(removed);
            }
        }
    }

    private static UUID nationOf(final MinecraftServer server, final Player player)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return null;
        }
        final var nation = OpacNations.nationOf(server, serverPlayer);
        return nation == null ? null : nation.nationId();
    }
}

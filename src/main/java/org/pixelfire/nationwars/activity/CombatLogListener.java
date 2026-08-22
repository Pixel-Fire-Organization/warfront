package org.pixelfire.nationwars.activity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.pixelfire.nationwars.NationWarsMod;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditSource;

import java.util.List;
import java.util.UUID;

/**
 * Tags both sides of player-vs-player damage; a disconnect while tagged kills the player
 * immediately unless the server is stopping. Standing in an {@code ACTIVE} war's capture zone is the
 * other documented trigger, deferred until capture zones exist (Stage 16) — nothing to tag from yet.
 */
public final class CombatLogListener
{
    private final CombatTagTracker tracker;

    public CombatLogListener(final CombatTagTracker tracker)
    {
        this.tracker = tracker;
    }

    @SubscribeEvent
    public void onHurt(final LivingHurtEvent event)
    {
        if (!NationWarsConfig.COMBAT_LOG_KILL.get())
        {
            return;
        }
        final long tagDurationTicks = NationWarsConfig.COMBAT_TAG_DURATION_SECONDS.get() * 20L;
        final boolean victimIsPlayer = event.getEntity() instanceof ServerPlayer;
        final boolean attackerIsPlayer = event.getSource().getEntity() instanceof ServerPlayer;
        if (!victimIsPlayer || !attackerIsPlayer)
        {
            return;
        }
        final UUID victimId = event.getEntity().getUUID();
        final UUID attackerId = event.getSource().getEntity().getUUID();
        final long currentTick = event.getEntity().level().getGameTime();
        tracker.tag(victimId, attackerId, currentTick, tagDurationTicks);
        tracker.tag(attackerId, victimId, currentTick, tagDurationTicks);
    }

    @SubscribeEvent
    public void onLogout(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final long currentTick = player.level().getGameTime();
        final boolean tagged = tracker.isTagged(player.getUUID(), currentTick);
        tracker.clear(player.getUUID());

        if (!tagged || !NationWarsConfig.COMBAT_LOG_KILL.get())
        {
            return;
        }
        if (NationWarsConfig.COMBAT_LOG_GRACE_ON_SERVER_STOP.get() && NationWarsMod.get().isServerStopping())
        {
            return;
        }

        player.kill();

        final CompoundTag after = new CompoundTag();
        after.putDouble("x", player.getX());
        after.putDouble("y", player.getY());
        after.putDouble("z", player.getZ());
        NationWarsMod.get().getAuditWriter().append(AuditEntry.of(
                player.getUUID(), player.getGameProfile().getName(), null, ActorRole.MEMBER, AuditSource.AUTO,
                ResourceLocation.tryBuild(NationWarsMod.MODID, "combat_log_kill"), List.of(),
                new CompoundTag(), after, false));
    }
}

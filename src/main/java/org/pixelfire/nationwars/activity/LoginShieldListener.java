package org.pixelfire.nationwars.activity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.pixelfire.nationwars.config.NationWarsConfig;

/**
 * The login shield does not grant invulnerability by default: a shielded player can still
 * fight, be killed, and count in capture zones. {@code loginShieldGrantsInvulnerability} exists only
 * for servers that deliberately want the (documented, unbalanced) alternative.
 */
public final class LoginShieldListener
{
    private final ActivityTracker tracker;

    public LoginShieldListener(final ActivityTracker tracker)
    {
        this.tracker = tracker;
    }

    @SubscribeEvent
    public void onHurt(final LivingHurtEvent event)
    {
        if (!NationWarsConfig.LOGIN_SHIELD_GRANTS_INVULNERABILITY.get() || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        final long afkThresholdTicks = NationWarsConfig.AFK_THRESHOLD_SECONDS.get() * 20L;
        final long currentTick = player.level().getGameTime();
        if (tracker.stateOf(player.getUUID(), currentTick, afkThresholdTicks) == PlayerActivityState.SHIELDED)
        {
            event.setCanceled(true);
        }
    }
}

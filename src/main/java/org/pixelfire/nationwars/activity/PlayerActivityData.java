package org.pixelfire.nationwars.activity;

import java.util.UUID;

/**
 * @param lastActivityTick initialised to {@code shieldExpiresTick} at login, so a player who stands
 *                         still through the whole shield goes AFK at login + shield + afkThreshold
 * @param manualAfk        set by {@code /afk}; cleared the moment any real activity is recorded
 */
public record PlayerActivityData(UUID playerId, long loginTick, long shieldExpiresTick, long lastActivityTick, boolean manualAfk)
{
}

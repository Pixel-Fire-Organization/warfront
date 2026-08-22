package org.pixelfire.nationwars.activity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link PlayerActivityData} per online player. Not persisted: a restart simply
 * re-shields everyone at their next login, same as a fresh join.
 */
public final class ActivityTracker
{
    private final Map<UUID, PlayerActivityData> byPlayer = new ConcurrentHashMap<>();

    public void onLogin(final UUID playerId, final long currentTick, final long shieldDurationTicks)
    {
        final long shieldExpiresTick = currentTick + shieldDurationTicks;
        byPlayer.put(playerId, new PlayerActivityData(playerId, currentTick, shieldExpiresTick, shieldExpiresTick, false));
    }

    public void onLogout(final UUID playerId)
    {
        byPlayer.remove(playerId);
    }

    /**
     * Records real activity, clearing {@code manualAfk} and, if the player was previously AFK, granting
     * {@code afkExitShieldTicks} of fresh shield time before they're read as READY again.
     */
    public void recordActivity(final UUID playerId, final long currentTick, final long afkThresholdTicks, final long afkExitShieldTicks)
    {
        byPlayer.computeIfPresent(playerId, (id, data) ->
        {
            final boolean wasAfk = ActivityStateMachine.compute(data, currentTick, afkThresholdTicks) == PlayerActivityState.AFK;
            final long shieldExpiresTick = wasAfk
                    ? Math.max(data.shieldExpiresTick(), currentTick + afkExitShieldTicks)
                    : data.shieldExpiresTick();
            return new PlayerActivityData(id, data.loginTick(), shieldExpiresTick, currentTick, false);
        });
    }

    public void markManualAfk(final UUID playerId)
    {
        byPlayer.computeIfPresent(playerId, (id, data) ->
                new PlayerActivityData(id, data.loginTick(), data.shieldExpiresTick(), data.lastActivityTick(), true));
    }

    public PlayerActivityState stateOf(final UUID playerId, final long currentTick, final long afkThresholdTicks)
    {
        final PlayerActivityData data = byPlayer.get(playerId);
        return data == null ? PlayerActivityState.AFK : ActivityStateMachine.compute(data, currentTick, afkThresholdTicks);
    }
}

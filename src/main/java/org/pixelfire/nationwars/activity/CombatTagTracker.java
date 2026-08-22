package org.pixelfire.nationwars.activity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link CombatTracker} per combat-tagged player. Not persisted: the tag is only ever seconds
 * long, so a restart losing it is not a real-world concern.
 */
public final class CombatTagTracker
{
    private final Map<UUID, CombatTracker> byPlayer = new ConcurrentHashMap<>();

    public void tag(final UUID playerId, final UUID attackerId, final long currentTick, final long tagDurationTicks)
    {
        byPlayer.put(playerId, new CombatTracker(playerId, currentTick + tagDurationTicks, attackerId));
    }

    public boolean isTagged(final UUID playerId, final long currentTick)
    {
        final CombatTracker tracker = byPlayer.get(playerId);
        return tracker != null && tracker.combatTagExpiresTick() > currentTick;
    }

    public void clear(final UUID playerId)
    {
        byPlayer.remove(playerId);
    }
}

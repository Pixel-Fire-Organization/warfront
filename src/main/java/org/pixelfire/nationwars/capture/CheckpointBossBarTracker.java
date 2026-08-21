package org.pixelfire.nationwars.capture;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One vanilla {@link ServerBossEvent} per contested checkpoint, showing capture progress to whoever is
 * currently in its zone — reusing the boss bar rather than a custom overlay element, so it works for a
 * vanilla client too.
 */
public final class CheckpointBossBarTracker
{
    private final Map<UUID, ServerBossEvent> bars = new ConcurrentHashMap<>();

    public void update(final UUID checkpointId, final String cityName, final float progress, final Set<ServerPlayer> playersInZone)
    {
        ServerBossEvent bar = bars.get(checkpointId);
        if (bar == null)
        {
            bar = new ServerBossEvent(Component.literal(cityName + " checkpoint"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bars.put(checkpointId, bar);
        }
        bar.setProgress(Math.max(0f, Math.min(1f, progress)));

        final Set<UUID> wanted = new HashSet<>();
        for (final ServerPlayer player : playersInZone)
        {
            wanted.add(player.getUUID());
            if (!bar.getPlayers().contains(player))
            {
                bar.addPlayer(player);
            }
        }
        for (final ServerPlayer viewer : Set.copyOf(bar.getPlayers()))
        {
            if (!wanted.contains(viewer.getUUID()))
            {
                bar.removePlayer(viewer);
            }
        }
    }

    public void clear(final UUID checkpointId)
    {
        final ServerBossEvent bar = bars.remove(checkpointId);
        if (bar != null)
        {
            bar.removeAllPlayers();
        }
    }
}

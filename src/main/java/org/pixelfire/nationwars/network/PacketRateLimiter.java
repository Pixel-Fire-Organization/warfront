package org.pixelfire.nationwars.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-packet-type cooldown for C2S packets: a packet arriving before {@code
 * c2sPacketRateLimitMs} has elapsed since that player's last accepted packet of the same type is
 * dropped outright, before any server-side re-validation runs.
 */
public final class PacketRateLimiter
{
    private final Map<String, Long> lastAcceptedAt = new ConcurrentHashMap<>();

    /**
     * @return true if this arrival is accepted (and the cooldown is now reset from this moment)
     */
    public boolean tryAccept(final UUID playerId, final Class<?> packetType, final long now, final long cooldownMs)
    {
        final String key = playerId + ":" + packetType.getName();
        final Long last = lastAcceptedAt.get(key);
        if (last != null && now - last < cooldownMs)
        {
            return false;
        }
        lastAcceptedAt.put(key, now);
        return true;
    }
}

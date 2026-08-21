package org.pixelfire.nationwars.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.pixelfire.nationwars.network.SyncCheckpointStatePacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest {@link SyncCheckpointStatePacket} per checkpoint, read by {@link CheckpointRenderer} for the
 * progress ring and chain overlay. Only ever populated for checkpoints currently contested and within
 * 128 blocks, per the packet's own send condition, so a stale entry here just means "no longer
 * contested or out of range" rather than something needing active eviction.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCheckpointCache
{
    private static final Map<UUID, SyncCheckpointStatePacket> CHECKPOINTS = new ConcurrentHashMap<>();

    private ClientCheckpointCache()
    {
    }

    public static void putCheckpoint(final SyncCheckpointStatePacket packet)
    {
        CHECKPOINTS.put(packet.data().getUUID("checkpointId"), packet);
    }

    public static SyncCheckpointStatePacket get(final UUID checkpointId)
    {
        return CHECKPOINTS.get(checkpointId);
    }

    public static void clear()
    {
        CHECKPOINTS.clear();
    }
}

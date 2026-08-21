package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.Checkpoint;

/**
 * S2C: one checkpoint's contested state, sent every 10 ticks while contested to clients within 128
 * blocks. Fields are read straight off {@link #data()} by {@link
 * org.pixelfire.nationwars.client.ClientNationCache} rather than through per-field accessors here —
 * this packet (and every S2C packet after it) is a thin NBT envelope, not a second copy of the domain
 * model's field list.
 */
public record SyncCheckpointStatePacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncCheckpointStatePacket of(final Checkpoint checkpoint)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("checkpointId", checkpoint.checkpointId());
        tag.putUUID("cityId", checkpoint.cityId());
        tag.putInt("posX", checkpoint.pos().getX());
        tag.putInt("posY", checkpoint.pos().getY());
        tag.putInt("posZ", checkpoint.pos().getZ());
        tag.putUUID("holderNationId", checkpoint.holderNationId());
        tag.putFloat("captureProgress", checkpoint.captureProgress());
        if (checkpoint.capturingNationId() != null)
        {
            tag.putUUID("capturingNationId", checkpoint.capturingNationId());
        }
        tag.putString("status", checkpoint.status().name());
        return new SyncCheckpointStatePacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncCheckpointStatePacket decode(final FriendlyByteBuf buf)
    {
        return new SyncCheckpointStatePacket(buf.readNbt());
    }
}

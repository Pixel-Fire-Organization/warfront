package org.pixelfire.nationwars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S2C: the shatter-and-reform cosmetic played at a checkpoint's position on a real break or a cosmetic
 * break-while-under-siege — purely visual, carries no state change.
 */
public record CheckpointEffectPacket(CompoundTag data) implements NationWarsPacket
{
    public static CheckpointEffectPacket of(final BlockPos pos, final String effect)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("posX", pos.getX());
        tag.putInt("posY", pos.getY());
        tag.putInt("posZ", pos.getZ());
        tag.putString("effect", effect);
        return new CheckpointEffectPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static CheckpointEffectPacket decode(final FriendlyByteBuf buf)
    {
        return new CheckpointEffectPacket(buf.readNbt());
    }

    public BlockPos pos()
    {
        return new BlockPos(data.getInt("posX"), data.getInt("posY"), data.getInt("posZ"));
    }
}

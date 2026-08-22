package org.pixelfire.nationwars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S: renames a city from the City Core GUI's rename field. Addressed by the core's block position
 * (all the client has — a city's own {@code cityId} is never synced to it) rather than a city id.
 * Re-validated fully server-side (rank, name length, uniqueness) — the client sending this is never
 * trusted further than that.
 */
public record RenameCityPacket(CompoundTag data) implements NationWarsPacket
{
    public static RenameCityPacket of(final BlockPos corePos, final String newName)
    {
        final CompoundTag tag = new CompoundTag();
        tag.put("corePos", NbtUtils.writeBlockPos(corePos));
        tag.putString("newName", newName);
        return new RenameCityPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static RenameCityPacket decode(final FriendlyByteBuf buf)
    {
        return new RenameCityPacket(buf.readNbt());
    }

    public BlockPos corePos()
    {
        return NbtUtils.readBlockPos(data.getCompound("corePos"));
    }

    public String newName()
    {
        return data.getString("newName");
    }
}

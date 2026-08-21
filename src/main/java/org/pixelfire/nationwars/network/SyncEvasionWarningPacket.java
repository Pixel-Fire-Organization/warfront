package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * S2C: an evasion-clock warning threshold (50/75/90%) crossed for the receiving player's nation in one
 * war.
 */
public record SyncEvasionWarningPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncEvasionWarningPacket of(final UUID warId, final int thresholdPercent, final long remainingMs)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.putInt("thresholdPercent", thresholdPercent);
        tag.putLong("remainingMs", remainingMs);
        return new SyncEvasionWarningPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncEvasionWarningPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncEvasionWarningPacket(buf.readNbt());
    }
}

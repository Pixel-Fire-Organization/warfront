package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * C2S: accept or reject the currently open peace deal for a war — the packet-native equivalent of
 * {@code /war negotiate accept|reject}.
 */
public record SettlementResponsePacket(CompoundTag data) implements NationWarsPacket
{
    public static SettlementResponsePacket of(final UUID warId, final boolean accept)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.putBoolean("accept", accept);
        return new SettlementResponsePacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SettlementResponsePacket decode(final FriendlyByteBuf buf)
    {
        return new SettlementResponsePacket(buf.readNbt());
    }

    public UUID warId()
    {
        return data.getUUID("warId");
    }

    public boolean accept()
    {
        return data.getBoolean("accept");
    }
}

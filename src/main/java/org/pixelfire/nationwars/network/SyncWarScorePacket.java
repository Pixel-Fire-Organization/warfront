package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * S2C: the receiving player's own nation's war score in one war — never another nation's, mirroring
 * the readiness-roster privacy rule.
 */
public record SyncWarScorePacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncWarScorePacket of(final UUID warId, final long ownScore)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.putLong("ownScore", ownScore);
        return new SyncWarScorePacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncWarScorePacket decode(final FriendlyByteBuf buf)
    {
        return new SyncWarScorePacket(buf.readNbt());
    }
}

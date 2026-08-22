package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.Coalition;

import java.util.UUID;

/**
 * S2C: one side's coalition membership and pending entries, sent whenever either changes.
 */
public record SyncCoalitionPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncCoalitionPacket of(final UUID warId, final boolean isAttackerSide, final Coalition coalition)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.putBoolean("isAttackerSide", isAttackerSide);
        tag.putUUID("primaryNationId", coalition.primaryNationId());
        final ListTag members = new ListTag();
        coalition.members().forEach(id -> members.add(StringTag.valueOf(id.toString())));
        tag.put("members", members);
        final ListTag pending = new ListTag();
        coalition.pendingMembers().keySet().forEach(id -> pending.add(StringTag.valueOf(id.toString())));
        tag.put("pendingMembers", pending);
        return new SyncCoalitionPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncCoalitionPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncCoalitionPacket(buf.readNbt());
    }
}

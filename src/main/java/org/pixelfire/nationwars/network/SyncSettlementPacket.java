package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.PeaceSettlement;

/**
 * S2C: a ratification-progress update for an already-open peace deal — lighter than {@link
 * OpenPeaceDealPacket} since the clause list itself never changes after an offer is sent, only who has
 * signed.
 */
public record SyncSettlementPacket(CompoundTag data) implements NationWarsPacket
{
    public static SyncSettlementPacket of(final PeaceSettlement settlement)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", settlement.warId());
        tag.putUUID("settlementId", settlement.settlementId());
        final ListTag ratifications = new ListTag();
        settlement.ratifications().forEach((nationId, state) ->
        {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("nationId", nationId);
            entry.putString("state", state.name());
            ratifications.add(entry);
        });
        tag.put("ratifications", ratifications);
        tag.putBoolean("fullyRatified", settlement.fullyRatified());
        tag.putBoolean("anyRejected", settlement.anyRejected());
        return new SyncSettlementPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static SyncSettlementPacket decode(final FriendlyByteBuf buf)
    {
        return new SyncSettlementPacket(buf.readNbt());
    }
}

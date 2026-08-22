package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.PeaceSettlement;
import org.pixelfire.nationwars.state.StagedClauseSnapshot;

/**
 * S2C: opens the peace-deal screen client-side, either because the receiving player just ran
 * {@code /war negotiate send} (their own offer) or their nation is a signatory on someone else's.
 */
public record OpenPeaceDealPacket(CompoundTag data) implements NationWarsPacket
{
    public static OpenPeaceDealPacket of(final PeaceSettlement settlement)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", settlement.warId());
        tag.putUUID("settlementId", settlement.settlementId());
        tag.putUUID("proposedByNationId", settlement.proposedByNationId());
        tag.putLong("expiresAt", settlement.expiresAt());
        tag.put("clauses", StagedClauseSnapshot.write(settlement.clauses()));
        final ListTag ratifications = new ListTag();
        settlement.ratifications().forEach((nationId, state) ->
        {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("nationId", nationId);
            entry.putString("state", state.name());
            ratifications.add(entry);
        });
        tag.put("ratifications", ratifications);
        return new OpenPeaceDealPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static OpenPeaceDealPacket decode(final FriendlyByteBuf buf)
    {
        return new OpenPeaceDealPacket(buf.readNbt());
    }
}

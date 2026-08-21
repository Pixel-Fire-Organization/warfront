package org.pixelfire.nationwars.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.pixelfire.nationwars.state.StagedClause;
import org.pixelfire.nationwars.state.StagedClauseSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * C2S: sends the peace-deal screen's current clause list as a real offer — the packet-native
 * equivalent of building a draft via {@code /war negotiate offer|demand|...} and then {@code send},
 * collapsed into one round trip since the screen already holds the whole draft client-side.
 */
public record ProposeSettlementPacket(CompoundTag data) implements NationWarsPacket
{
    public static ProposeSettlementPacket of(final UUID warId, final List<StagedClause> clauses)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.put("clauses", StagedClauseSnapshot.write(clauses));
        return new ProposeSettlementPacket(tag);
    }

    @Override
    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeNbt(data);
    }

    public static ProposeSettlementPacket decode(final FriendlyByteBuf buf)
    {
        return new ProposeSettlementPacket(buf.readNbt());
    }

    public UUID warId()
    {
        return data.getUUID("warId");
    }

    public List<StagedClause> clauses()
    {
        return StagedClauseSnapshot.read(data.getList("clauses", Tag.TAG_COMPOUND));
    }
}

package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Round-trips a {@link PeaceSettlement} through NBT for persistence, reusing {@link
 * StagedClauseSnapshot} for its clause list.
 */
public final class PeaceSettlementSnapshot
{
    private PeaceSettlementSnapshot()
    {
    }

    public static CompoundTag write(final PeaceSettlement settlement)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("settlementId", settlement.settlementId());
        tag.putUUID("warId", settlement.warId());
        if (settlement.proposedByNationId() != null)
        {
            tag.putUUID("proposedByNationId", settlement.proposedByNationId());
        }
        tag.put("clauses", StagedClauseSnapshot.write(settlement.clauses()));
        tag.putLong("createdAt", settlement.createdAt());
        tag.putLong("expiresAt", settlement.expiresAt());
        final ListTag ratifications = new ListTag();
        settlement.ratifications().forEach((nationId, state) ->
        {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("nationId", nationId);
            entry.putString("state", state.name());
            ratifications.add(entry);
        });
        tag.put("ratifications", ratifications);
        tag.putInt("rejectionCount", settlement.rejectionCount());
        return tag;
    }

    public static PeaceSettlement read(final CompoundTag tag)
    {
        final Map<UUID, RatificationState> ratifications = new HashMap<>();
        for (final Tag element : tag.getList("ratifications", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) element;
            ratifications.put(entry.getUUID("nationId"), RatificationState.valueOf(entry.getString("state")));
        }
        return new PeaceSettlement(
                tag.getUUID("settlementId"),
                tag.getUUID("warId"),
                tag.contains("proposedByNationId") ? tag.getUUID("proposedByNationId") : null,
                StagedClauseSnapshot.read(tag.getList("clauses", Tag.TAG_COMPOUND)),
                tag.getLong("createdAt"),
                tag.getLong("expiresAt"),
                Map.copyOf(ratifications),
                tag.getInt("rejectionCount"));
    }
}

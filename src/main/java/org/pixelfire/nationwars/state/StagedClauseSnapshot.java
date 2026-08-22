package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-trips a {@code List<StagedClause>} through a single {@link ListTag}, shared by every packet
 * that needs to carry a settlement's clause list across the wire (the peace-deal screen and its
 * negotiation counter-offer).
 */
public final class StagedClauseSnapshot
{
    private StagedClauseSnapshot()
    {
    }

    public static ListTag write(final List<StagedClause> clauses)
    {
        final ListTag list = new ListTag();
        for (final StagedClause clause : clauses)
        {
            final CompoundTag tag = new CompoundTag();
            tag.putString("clauseTypeId", clause.clauseTypeId().toString());
            tag.put("params", clause.params().copy());
            list.add(tag);
        }
        return list;
    }

    public static List<StagedClause> read(final ListTag list)
    {
        final List<StagedClause> clauses = new ArrayList<>();
        for (final Tag element : list)
        {
            final CompoundTag tag = (CompoundTag) element;
            clauses.add(new StagedClause(ResourceLocation.parse(tag.getString("clauseTypeId")), tag.getCompound("params")));
        }
        return clauses;
    }
}

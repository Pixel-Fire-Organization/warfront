package org.pixelfire.nationwars.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One concrete clause of a settlement: which {@link PeaceClause} kind (by registry id) and that use's
 * own parameters, e.g. {@code cityId}/{@code toNationId} for a transfer.
 */
public record StagedClause(ResourceLocation clauseTypeId, CompoundTag params)
{
}

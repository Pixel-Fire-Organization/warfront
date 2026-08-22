package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A registered protected column, identified by the core or checkpoint block anchoring it.
 */
public record ColumnRef(ResourceKey<Level> dimension, BlockPos corePos)
{
}

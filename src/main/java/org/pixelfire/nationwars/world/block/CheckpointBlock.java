package org.pixelfire.nationwars.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breaks like any normal block for now. State-gated breaking (real break only while a city is
 * {@code ACTIVE}/{@code DORMANT}, cosmetic shatter-and-respawn during a siege) lands once a
 * {@code Checkpoint} record can actually own this block.
 */
public class CheckpointBlock extends Block implements EntityBlock
{
    public CheckpointBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state)
    {
        return new CheckpointBlockEntity(pos, state);
    }
}

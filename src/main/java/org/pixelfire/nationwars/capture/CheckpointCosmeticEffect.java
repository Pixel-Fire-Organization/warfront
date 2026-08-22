package org.pixelfire.nationwars.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.world.block.CheckpointBlockEntity;
import org.pixelfire.nationwars.world.block.NationWarsBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The shatter-and-reform effect: whether triggered by a real capture flip or a swing at a
 * checkpoint mid-siege, the block is never actually removed from the registry's perspective — only the
 * world-visible block state cycles out and back, on a plain scheduled delay rather than a vanilla block
 * tick, since the block is briefly air and so isn't scheduling its own ticks.
 */
public final class CheckpointCosmeticEffect
{
    private record PendingRespawn(ResourceKey<Level> dimension, BlockPos pos, UUID checkpointId, UUID cityId, long respawnAtTick)
    {
    }

    private final List<PendingRespawn> pending = new CopyOnWriteArrayList<>();

    public void shatter(final ServerLevel level, final BlockPos pos, final UUID checkpointId, final UUID cityId)
    {
        final BlockState currentState = level.getBlockState(pos);
        level.levelEvent(2001, pos, Block.getId(currentState));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        final long respawnAtTick = level.getGameTime() + NationWarsConfig.CHECKPOINT_RESPAWN_DELAY_SECONDS.get() * 20L;
        pending.add(new PendingRespawn(level.dimension(), pos, checkpointId, cityId, respawnAtTick));
    }

    public void tick(final MinecraftServer server)
    {
        if (pending.isEmpty())
        {
            return;
        }
        final long now = server.overworld().getGameTime();
        final List<PendingRespawn> due = new ArrayList<>();
        for (final PendingRespawn respawn : pending)
        {
            if (now >= respawn.respawnAtTick())
            {
                due.add(respawn);
            }
        }
        for (final PendingRespawn respawn : due)
        {
            pending.remove(respawn);
            final ServerLevel level = server.getLevel(respawn.dimension());
            if (level == null)
            {
                continue;
            }
            level.setBlock(respawn.pos(), NationWarsBlocks.CHECKPOINT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(respawn.pos()) instanceof CheckpointBlockEntity blockEntity)
            {
                blockEntity.setIds(respawn.checkpointId(), respawn.cityId());
            }
        }
    }
}

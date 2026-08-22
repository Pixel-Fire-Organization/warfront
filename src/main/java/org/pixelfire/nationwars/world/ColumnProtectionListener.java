package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * Prevention side of the sky column rule: {@link BlockEvent.EntityPlaceEvent} (which
 * {@link BlockEvent.EntityMultiPlaceEvent} extends, so one handler covers both) and
 * {@link BlockEvent.FluidPlaceBlockEvent} are cancelled inside a registered column, and a falling
 * block that reaches one is removed and drops its item instead of settling inside it. Validation
 * against placements that bypass these events ({@code /setblock}, world-edit tools) is a periodic
 * sweep, added once cities/checkpoints exist to sweep over.
 */
public final class ColumnProtectionListener
{
    private final ColumnRegistry registry;

    public ColumnProtectionListener(final ColumnRegistry registry)
    {
        this.registry = registry;
    }

    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel() instanceof Level level && registry.isInsideAnyColumn(level.dimension(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFluidPlace(final BlockEvent.FluidPlaceBlockEvent event)
    {
        if (event.getLevel() instanceof Level level && registry.isInsideAnyColumn(level.dimension(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLevelTick(final TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
        {
            return;
        }
        final Level level = event.level;
        for (final ColumnRef ref : registry.allIn(level.dimension()))
        {
            removeFallingBlocksInColumn(level, ref.corePos());
        }
    }

    private void removeFallingBlocksInColumn(final Level level, final BlockPos corePos)
    {
        final AABB columnBounds = new AABB(
                corePos.getX() - 1, corePos.getY() + 1, corePos.getZ() - 1,
                corePos.getX() + 2, level.getMaxBuildHeight(), corePos.getZ() + 2);

        for (final FallingBlockEntity entity : level.getEntitiesOfClass(FallingBlockEntity.class, columnBounds))
        {
            final Block block = entity.getBlockState().getBlock();
            entity.discard();
            entity.spawnAtLocation(new ItemStack(block.asItem()));
        }
    }
}

package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Is the sky column above a position clear — every block from just above it up to the build height
 * is air? {@link #isColumnClear} is the synchronous, main-thread check used for one-off placement
 * validation: it tests {@link LevelChunkSection#hasOnlyAir()} per 16-block section first, and only
 * inspects individual blocks within a section that isn't entirely air. A column above open ground is
 * typically 15+ empty sections, so this is effectively free — good enough on its own for a single
 * placement check.
 *
 * <p>{@link #snapshot} and {@link #analyze} split that same check into a main-thread read and a pure
 * function over the result, for the periodic revalidation sweep across many columns at once, where
 * offloading the actual air/obstruction comparison to a worker thread is worth the extra step.
 */
public final class SkyColumnScanner
{
    private SkyColumnScanner()
    {
    }

    public static boolean isColumnClear(final Level level, final BlockPos corePos)
    {
        final int startY = corePos.getY() + 1;
        final int endY = level.getMaxBuildHeight() - 1;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (!isSingleColumnClear(level, corePos.getX() + dx, corePos.getZ() + dz, startY, endY))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSingleColumnClear(final Level level, final int x, final int z, final int startY, final int endY)
    {
        if (startY > endY)
        {
            return true;
        }
        final ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));

        int y = startY;
        while (y <= endY)
        {
            final int sectionIndex = level.getSectionIndex(y);
            final int sectionBottomY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(sectionIndex));
            final int sectionTopY = sectionBottomY + 15;
            final LevelChunkSection section = chunk.getSection(sectionIndex);

            if (section.hasOnlyAir())
            {
                y = sectionTopY + 1;
                continue;
            }

            final int scanEndY = Math.min(endY, sectionTopY);
            for (; y <= scanEndY; y++)
            {
                if (!level.getBlockState(new BlockPos(x, y, z)).isAir())
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Captures every block's air/non-air state in the column above {@code corePos}, on the main
     * thread. {@link #analyze} can then run on the result off it.
     */
    public static ColumnSnapshot snapshot(final Level level, final BlockPos corePos)
    {
        final int startY = corePos.getY() + 1;
        final int endY = level.getMaxBuildHeight() - 1;
        final boolean[][] isAir = new boolean[9][Math.max(0, endY - startY + 1)];

        int columnIndex = 0;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                final int x = corePos.getX() + dx;
                final int z = corePos.getZ() + dz;
                for (int y = startY; y <= endY; y++)
                {
                    isAir[columnIndex][y - startY] = level.getBlockState(new BlockPos(x, y, z)).isAir();
                }
                columnIndex++;
            }
        }
        return new ColumnSnapshot(isAir);
    }

    public static boolean analyze(final ColumnSnapshot snapshot)
    {
        for (final boolean[] column : snapshot.isAirByColumn())
        {
            for (final boolean air : column)
            {
                if (!air)
                {
                    return false;
                }
            }
        }
        return true;
    }

    public record ColumnSnapshot(boolean[][] isAirByColumn)
    {
    }
}

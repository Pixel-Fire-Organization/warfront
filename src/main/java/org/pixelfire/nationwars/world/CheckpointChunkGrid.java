package org.pixelfire.nationwars.world;

import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.Set;

/**
 * The checkerboard grid a city's checkpoints must sit on: plus-shaped 5-chunk "checkpoint chunk" cells
 * (the same {@link ClaimShape#PLUS} footprint a checkpoint claims once placed), spaced {@link #CELL_SPACING}
 * chunks apart on both axes from the city's own core chunk, which is cell (0,0). The 2x2 chunk gaps
 * between four diagonally-adjacent cells are never part of any cell — a checkpoint may not be placed
 * there — but {@link #gapChunksBetween} auto-absorbs one once all four surrounding cells are occupied.
 *
 * <p>A tier's configured radius counts how many cells out (Euclidean, in cell units) from the city's own
 * cell a checkpoint's cell may be, not a block or raw chunk distance.
 */
public final class CheckpointChunkGrid
{
    public static final int CELL_SPACING = 3;
    public static final int BLOCKS_PER_CHUNK = 16;
    public static final int BLOCKS_PER_CELL = CELL_SPACING * BLOCKS_PER_CHUNK;

    private CheckpointChunkGrid()
    {
    }

    public record Cell(int i, int j)
    {
        public double distanceFromOrigin()
        {
            return Math.sqrt((double) i * i + (double) j * j);
        }
    }

    /**
     * The cell {@code target} belongs to, relative to {@code coreChunk}, or empty if it falls in an
     * unclaimed gap chunk between cells.
     */
    public static Optional<Cell> resolveCell(final ChunkPos coreChunk, final ChunkPos target)
    {
        final int rx = target.x - coreChunk.x;
        final int rz = target.z - coreChunk.z;
        final int rxMod = Math.floorMod(rx, CELL_SPACING);
        final int rzMod = Math.floorMod(rz, CELL_SPACING);

        if (rzMod == 0)
        {
            return Optional.of(new Cell(nearestCellIndex(rx, rxMod), rz / CELL_SPACING));
        }
        if (rxMod == 0)
        {
            return Optional.of(new Cell(rx / CELL_SPACING, nearestCellIndex(rz, rzMod)));
        }
        return Optional.empty();
    }

    /** The five chunks (center + N/S/E/W) belonging to one cell. */
    public static Set<ChunkPos> chunksForCell(final ChunkPos coreChunk, final Cell cell)
    {
        final ChunkPos center = new ChunkPos(coreChunk.x + cell.i() * CELL_SPACING, coreChunk.z + cell.j() * CELL_SPACING);
        return ClaimSetComputation.chunksFor(ClaimShape.PLUS, center);
    }

    /**
     * The four gap chunks sitting between the 2x2 group of cells whose lowest-index corner is
     * {@code (i, j)} — i.e. between cells (i,j), (i+1,j), (i,j+1), (i+1,j+1).
     */
    public static Set<ChunkPos> gapChunksBetween(final ChunkPos coreChunk, final int i, final int j)
    {
        final int baseX = coreChunk.x + i * CELL_SPACING + 1;
        final int baseZ = coreChunk.z + j * CELL_SPACING + 1;
        return Set.of(
                new ChunkPos(baseX, baseZ), new ChunkPos(baseX + 1, baseZ),
                new ChunkPos(baseX, baseZ + 1), new ChunkPos(baseX + 1, baseZ + 1));
    }

    /** The base (lowest-index) corner of every 2x2 cell group that {@code cell} is a member of. */
    public static Set<Cell> adjacentGapGroupBases(final Cell cell)
    {
        return Set.of(
                new Cell(cell.i() - 1, cell.j() - 1), new Cell(cell.i() - 1, cell.j()),
                new Cell(cell.i(), cell.j() - 1), new Cell(cell.i(), cell.j()));
    }

    private static int nearestCellIndex(final int coord, final int mod)
    {
        return switch (mod)
        {
            case 0 -> coord / CELL_SPACING;
            case 1 -> (coord - 1) / CELL_SPACING;
            default -> (coord + 1) / CELL_SPACING;
        };
    }
}

package org.pixelfire.nationwars.world;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointChunkGridTest
{
    private static final ChunkPos CORE = new ChunkPos(100, -50);

    @Test
    void coreChunkIsCellOrigin()
    {
        assertEquals(Optional.of(new CheckpointChunkGrid.Cell(0, 0)), CheckpointChunkGrid.resolveCell(CORE, CORE));
    }

    @Test
    void cardinalArmsOfTheOriginCellResolveToIt()
    {
        final CheckpointChunkGrid.Cell origin = new CheckpointChunkGrid.Cell(0, 0);
        assertEquals(Optional.of(origin), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 1, CORE.z)));
        assertEquals(Optional.of(origin), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x - 1, CORE.z)));
        assertEquals(Optional.of(origin), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x, CORE.z + 1)));
        assertEquals(Optional.of(origin), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x, CORE.z - 1)));
    }

    @Test
    void diagonalChunksBetweenCellsAreGaps()
    {
        assertEquals(Optional.empty(), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 1, CORE.z + 1)));
        assertEquals(Optional.empty(), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 2, CORE.z + 1)));
        assertEquals(Optional.empty(), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 1, CORE.z + 2)));
        assertEquals(Optional.empty(), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 2, CORE.z + 2)));
    }

    @Test
    void neighbouringCellArmsResolveCorrectly()
    {
        final CheckpointChunkGrid.Cell east = new CheckpointChunkGrid.Cell(1, 0);
        assertEquals(Optional.of(east), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 3, CORE.z)));
        assertEquals(Optional.of(east), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 2, CORE.z)));
        assertEquals(Optional.of(east), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(CORE.x + 4, CORE.z)));

        final CheckpointChunkGrid.Cell negative = new CheckpointChunkGrid.Cell(-1, -2);
        final ChunkPos negativeCenter = new ChunkPos(CORE.x - 3, CORE.z - 6);
        assertEquals(Optional.of(negative), CheckpointChunkGrid.resolveCell(CORE, negativeCenter));
        assertEquals(Optional.of(negative), CheckpointChunkGrid.resolveCell(CORE, new ChunkPos(negativeCenter.x + 1, negativeCenter.z)));
    }

    @Test
    void chunksForCellMatchesThePlusClaimShape()
    {
        final CheckpointChunkGrid.Cell cell = new CheckpointChunkGrid.Cell(1, -1);
        final ChunkPos expectedCenter = new ChunkPos(CORE.x + 3, CORE.z - 3);

        assertEquals(ClaimSetComputation.chunksFor(ClaimShape.PLUS, expectedCenter), CheckpointChunkGrid.chunksForCell(CORE, cell));
    }

    @Test
    void gapChunksBetweenTheFourCellsSurroundingIt()
    {
        final Set<ChunkPos> gap = CheckpointChunkGrid.gapChunksBetween(CORE, 0, 0);

        assertEquals(4, gap.size());
        assertTrue(gap.contains(new ChunkPos(CORE.x + 1, CORE.z + 1)));
        assertTrue(gap.contains(new ChunkPos(CORE.x + 2, CORE.z + 1)));
        assertTrue(gap.contains(new ChunkPos(CORE.x + 1, CORE.z + 2)));
        assertTrue(gap.contains(new ChunkPos(CORE.x + 2, CORE.z + 2)));
    }

    @Test
    void aCellBordersFourDistinctGapGroups()
    {
        final Set<CheckpointChunkGrid.Cell> bases = CheckpointChunkGrid.adjacentGapGroupBases(new CheckpointChunkGrid.Cell(2, 3));

        assertEquals(Set.of(
                new CheckpointChunkGrid.Cell(1, 2), new CheckpointChunkGrid.Cell(1, 3),
                new CheckpointChunkGrid.Cell(2, 2), new CheckpointChunkGrid.Cell(2, 3)), bases);
    }

    @Test
    void distanceFromOriginIsEuclideanInCellUnits()
    {
        assertEquals(5.0, new CheckpointChunkGrid.Cell(3, 4).distanceFromOrigin(), 1e-9);
        assertEquals(0.0, new CheckpointChunkGrid.Cell(0, 0).distanceFromOrigin(), 1e-9);
    }
}

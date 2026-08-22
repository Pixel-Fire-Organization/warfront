package org.pixelfire.nationwars.world;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSetComputationTest
{
    private static final ChunkPos CENTER = new ChunkPos(10, 20);

    @Test
    void noneClaimsNothing()
    {
        assertEquals(Set.of(), ClaimSetComputation.chunksFor(ClaimShape.NONE, CENTER));
    }

    @Test
    void singleClaimsOnlyItsOwnChunk()
    {
        assertEquals(Set.of(CENTER), ClaimSetComputation.chunksFor(ClaimShape.SINGLE, CENTER));
    }

    @Test
    void plusClaimsItselfAndFourCardinalNeighbours()
    {
        final Set<ChunkPos> plus = ClaimSetComputation.chunksFor(ClaimShape.PLUS, CENTER);

        assertEquals(5, plus.size());
        assertTrue(plus.contains(CENTER));
        assertTrue(plus.contains(new ChunkPos(10, 19)));
        assertTrue(plus.contains(new ChunkPos(10, 21)));
        assertTrue(plus.contains(new ChunkPos(9, 20)));
        assertTrue(plus.contains(new ChunkPos(11, 20)));
    }

    @Test
    void squareClaimsAllNineSurroundingChunks()
    {
        final Set<ChunkPos> square = ClaimSetComputation.chunksFor(ClaimShape.SQUARE, CENTER);

        assertEquals(9, square.size());
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                assertTrue(square.contains(new ChunkPos(CENTER.x + dx, CENTER.z + dz)));
            }
        }
    }
}

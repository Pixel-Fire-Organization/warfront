package org.pixelfire.nationwars.world;

import net.minecraft.world.level.ChunkPos;

import java.util.Set;

/**
 * Pure function of a center chunk and a {@link ClaimShape} (safe to run off-thread,
 * since it only touches positions and config, never the world).
 */
public final class ClaimSetComputation
{
    private ClaimSetComputation()
    {
    }

    public static Set<ChunkPos> chunksFor(final ClaimShape shape, final ChunkPos center)
    {
        return switch (shape)
        {
            case NONE -> Set.of();
            case SINGLE -> Set.of(center);
            case PLUS -> Set.of(center,
                    new ChunkPos(center.x, center.z - 1),
                    new ChunkPos(center.x, center.z + 1),
                    new ChunkPos(center.x - 1, center.z),
                    new ChunkPos(center.x + 1, center.z));
            case SQUARE -> Set.of(
                    new ChunkPos(center.x - 1, center.z - 1), new ChunkPos(center.x, center.z - 1), new ChunkPos(center.x + 1, center.z - 1),
                    new ChunkPos(center.x - 1, center.z), center, new ChunkPos(center.x + 1, center.z),
                    new ChunkPos(center.x - 1, center.z + 1), new ChunkPos(center.x, center.z + 1), new ChunkPos(center.x + 1, center.z + 1));
        };
    }
}

package org.pixelfire.nationwars.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

/**
 * @param captureProgress  0-1
 * @param claimedChunks    cached plus-shape (or whatever {@code checkpointClaimShape} configures)
 * @param lastEvaluatedTime for lazy decay
 */
public record Checkpoint(
        UUID checkpointId,
        UUID cityId,
        ResourceKey<Level> dimension,
        BlockPos pos,
        UUID holderNationId,
        float captureProgress,
        UUID capturingNationId,
        CheckpointStatus status,
        Set<ChunkPos> claimedChunks,
        long lastEvaluatedTime,
        UUID placedBy,
        long placedAt)
{
}

package org.pixelfire.nationwars.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Round-trips a {@link Checkpoint} through NBT for audit {@code before}/{@code after} snapshots, so a
 * revert can fully reconstruct one rather than just knowing its id existed.
 */
public final class CheckpointSnapshot
{
    private CheckpointSnapshot()
    {
    }

    public static CompoundTag write(final Checkpoint checkpoint)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("checkpointId", checkpoint.checkpointId());
        tag.putUUID("cityId", checkpoint.cityId());
        tag.putString("dimension", checkpoint.dimension().location().toString());
        tag.putInt("posX", checkpoint.pos().getX());
        tag.putInt("posY", checkpoint.pos().getY());
        tag.putInt("posZ", checkpoint.pos().getZ());
        tag.putUUID("holderNationId", checkpoint.holderNationId());
        tag.putFloat("captureProgress", checkpoint.captureProgress());
        if (checkpoint.capturingNationId() != null)
        {
            tag.putUUID("capturingNationId", checkpoint.capturingNationId());
        }
        tag.putString("status", checkpoint.status().name());
        final ListTag chunks = new ListTag();
        for (final ChunkPos chunk : checkpoint.claimedChunks())
        {
            final CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", chunk.x);
            chunkTag.putInt("z", chunk.z);
            chunks.add(chunkTag);
        }
        tag.put("claimedChunks", chunks);
        tag.putLong("lastEvaluatedTime", checkpoint.lastEvaluatedTime());
        if (checkpoint.placedBy() != null)
        {
            tag.putUUID("placedBy", checkpoint.placedBy());
        }
        tag.putLong("placedAt", checkpoint.placedAt());
        return tag;
    }

    public static Checkpoint read(final CompoundTag tag)
    {
        final Set<ChunkPos> chunks = new HashSet<>();
        for (final Tag element : tag.getList("claimedChunks", Tag.TAG_COMPOUND))
        {
            final CompoundTag chunkTag = (CompoundTag) element;
            chunks.add(new ChunkPos(chunkTag.getInt("x"), chunkTag.getInt("z")));
        }
        final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension")));
        return new Checkpoint(
                tag.getUUID("checkpointId"),
                tag.getUUID("cityId"),
                dimension,
                new BlockPos(tag.getInt("posX"), tag.getInt("posY"), tag.getInt("posZ")),
                tag.getUUID("holderNationId"),
                tag.getFloat("captureProgress"),
                tag.contains("capturingNationId") ? tag.getUUID("capturingNationId") : null,
                CheckpointStatus.valueOf(tag.getString("status")),
                Set.copyOf(chunks),
                tag.getLong("lastEvaluatedTime"),
                tag.contains("placedBy") ? tag.getUUID("placedBy") : null,
                tag.getLong("placedAt"));
    }
}

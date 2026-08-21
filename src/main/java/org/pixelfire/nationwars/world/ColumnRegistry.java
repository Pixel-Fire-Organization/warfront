package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Every protected sky column, keyed by chunk so a block/fluid placement handler can reject a column
 * in O(1) and exit immediately for placements nowhere near one. A column's 3x3 (x,z) footprint can
 * straddle up to four chunks, so registration indexes it under all of them.
 */
public final class ColumnRegistry
{
    private final ConcurrentHashMap<ChunkKey, List<ColumnRef>> byChunk = new ConcurrentHashMap<>();
    private final Set<ColumnRef> all = new CopyOnWriteArraySet<>();

    public void register(final ResourceKey<Level> dimension, final BlockPos corePos)
    {
        final ColumnRef ref = new ColumnRef(dimension, corePos);
        all.add(ref);
        for (final ChunkPos chunk : chunksTouched(corePos))
        {
            byChunk.computeIfAbsent(new ChunkKey(dimension, chunk.toLong()), key -> new CopyOnWriteArrayList<>()).add(ref);
        }
    }

    public void unregister(final ResourceKey<Level> dimension, final BlockPos corePos)
    {
        all.remove(new ColumnRef(dimension, corePos));
        for (final ChunkPos chunk : chunksTouched(corePos))
        {
            final ChunkKey key = new ChunkKey(dimension, chunk.toLong());
            final List<ColumnRef> refs = byChunk.get(key);
            if (refs != null)
            {
                refs.removeIf(ref -> ref.corePos().equals(corePos));
            }
        }
    }

    public List<ColumnRef> columnsNear(final ResourceKey<Level> dimension, final ChunkPos chunkPos)
    {
        return byChunk.getOrDefault(new ChunkKey(dimension, chunkPos.toLong()), List.of());
    }

    /**
     * Every registered column in {@code dimension}, for the periodic falling-block sweep — the only
     * caller that needs to walk all of them rather than looking up a specific chunk.
     */
    public List<ColumnRef> allIn(final ResourceKey<Level> dimension)
    {
        return all.stream().filter(ref -> ref.dimension().equals(dimension)).toList();
    }

    public boolean isInsideAnyColumn(final ResourceKey<Level> dimension, final BlockPos pos)
    {
        for (final ColumnRef ref : columnsNear(dimension, new ChunkPos(pos)))
        {
            if (isInsideColumn(ref.corePos(), pos))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInsideColumn(final BlockPos corePos, final BlockPos pos)
    {
        return Math.abs(pos.getX() - corePos.getX()) <= 1
                && Math.abs(pos.getZ() - corePos.getZ()) <= 1
                && pos.getY() > corePos.getY();
    }

    private static Set<ChunkPos> chunksTouched(final BlockPos corePos)
    {
        final Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                chunks.add(new ChunkPos(corePos.offset(dx, 0, dz)));
            }
        }
        return chunks;
    }

    private record ChunkKey(ResourceKey<Level> dimension, long chunkPos)
    {
    }
}

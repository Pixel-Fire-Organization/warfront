package org.pixelfire.nationwars.io;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Writes a {@link NationWarsSavedData} snapshot to disk through the {@link WriterThread}, rather than
 * relying on vanilla's own (synchronous, main-thread) {@code SavedData} write path. The snapshot
 * itself — reading the in-memory fields into a {@link CompoundTag} — must happen on the main thread,
 * since that is the only thread allowed to touch live state; everything after that (NBT encoding,
 * gzip, the actual file write) happens on the writer thread instead.
 */
public final class PersistenceIo
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WriterThread writer;

    public PersistenceIo(final WriterThread writer)
    {
        this.writer = writer;
    }

    /**
     * Snapshots {@code data} now (call this on the main thread) and queues the encode-and-write for
     * the writer thread. Returns immediately; the file is not necessarily written yet when this
     * method returns.
     */
    public void save(final NationWarsSavedData data, final File file)
    {
        final CompoundTag snapshot = data.save(new CompoundTag());
        writer.submit(() -> writeToDisk(snapshot, file));
    }

    private void writeToDisk(final CompoundTag snapshot, final File file)
    {
        try
        {
            final File parent = file.getParentFile();
            if (parent != null)
            {
                parent.mkdirs();
            }
            NbtIo.writeCompressed(snapshot, file);
        }
        catch (final IOException e)
        {
            LOGGER.error("nationwars failed to write save data to {}", file, e);
        }
    }

    /**
     * Reads a save file synchronously. Only ever needed once, at startup, before there is anything
     * else for the main thread to be doing — unlike writes, this does not go through the writer
     * thread.
     */
    public static NationWarsSavedData load(final File file) throws IOException
    {
        if (!file.exists())
        {
            return new NationWarsSavedData();
        }
        return NationWarsSavedData.load(NbtIo.readCompressed(file));
    }
}

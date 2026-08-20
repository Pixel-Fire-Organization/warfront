package org.pixelfire.nationwars.io;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * The mod's persisted state, attached to the Overworld {@code ServerLevel} at {@code world/data/nationwars.dat}
 * like any other vanilla {@link SavedData}. Only a schema version and a placeholder payload exist so far —
 * this stage proves the attach/save/load round trip works before any real state (cities, checkpoints, wars)
 * is added to it.
 *
 * <p>{@code schemaVersion} is written into the root compound on every save. {@link #migrate} is the hook
 * future stages extend when the schema changes; today there is only one version, so it is a no-op.
 */
public final class NationWarsSavedData extends SavedData
{
    public static final String DATA_NAME = "nationwars";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_DUMMY_PAYLOAD = "dummyPayload";

    private volatile String dummyPayload = "";

    public String dummyPayload()
    {
        return dummyPayload;
    }

    public void setDummyPayload(final String value)
    {
        this.dummyPayload = value;
        setDirty();
    }

    @Override
    public CompoundTag save(final CompoundTag tag)
    {
        tag.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        tag.putString(KEY_DUMMY_PAYLOAD, dummyPayload);
        return tag;
    }

    /**
     * Reads a previously-saved compound, migrating it forward first if it was written by an older
     * schema version.
     */
    public static NationWarsSavedData load(final CompoundTag rawTag)
    {
        final CompoundTag tag = migrate(rawTag);
        final NationWarsSavedData data = new NationWarsSavedData();
        data.dummyPayload = tag.getString(KEY_DUMMY_PAYLOAD);
        return data;
    }

    /**
     * Brings a compound written by an older schema version up to {@link #CURRENT_SCHEMA_VERSION}.
     * There has only ever been one schema version so far, so this is currently a no-op; it exists so
     * the migration chain has somewhere to grow without disturbing callers.
     */
    private static CompoundTag migrate(final CompoundTag tag)
    {
        final int fromVersion = tag.contains(KEY_SCHEMA_VERSION) ? tag.getInt(KEY_SCHEMA_VERSION) : 0;
        if (fromVersion > CURRENT_SCHEMA_VERSION)
        {
            throw new IllegalStateException("nationwars save data schema version " + fromVersion
                    + " is newer than this build supports (" + CURRENT_SCHEMA_VERSION + "); refusing to load it and risk data loss");
        }
        return tag;
    }

    /**
     * Attaches this mod's save data to the given server's Overworld, creating it fresh if this is a
     * new world.
     */
    public static NationWarsSavedData get(final MinecraftServer server)
    {
        final DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(NationWarsSavedData::load, NationWarsSavedData::new, DATA_NAME);
    }
}

package org.pixelfire.nationwars.io;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationWarsSavedDataMigrationTest
{
    @Test
    void migratesAV1DummyPayloadTagIntoAnEmptyV2Shape()
    {
        final CompoundTag v1Tag = new CompoundTag();
        v1Tag.putInt("schemaVersion", 1);
        v1Tag.putString("dummyPayload", "leftover from an earlier stage");

        final CompoundTag migrated = NationWarsSavedData.migrate(v1Tag);

        assertEquals(2, migrated.getInt("schemaVersion"));
        for (final String key : new String[] {"cities", "checkpoints", "wars", "nationStates", "settlements", "evasionTrackers"})
        {
            assertTrue(migrated.contains(key), "expected v2 shape to contain " + key);
            assertTrue(migrated.getList(key, Tag.TAG_COMPOUND).isEmpty(), key + " should be empty after migrating from v1");
        }
    }

    @Test
    void aMissingSchemaVersionIsTreatedAsPreV1AndAlsoMigrated()
    {
        final CompoundTag bareTag = new CompoundTag();

        final CompoundTag migrated = NationWarsSavedData.migrate(bareTag);

        assertEquals(2, migrated.getInt("schemaVersion"));
    }

    @Test
    void anAlreadyCurrentV2TagPassesThroughUnchanged()
    {
        final CompoundTag v2Tag = new CompoundTag();
        v2Tag.putInt("schemaVersion", 2);
        v2Tag.put("cities", new ListTag());
        v2Tag.put("checkpoints", new ListTag());
        v2Tag.put("wars", new ListTag());
        v2Tag.put("nationStates", new ListTag());
        v2Tag.put("settlements", new ListTag());
        v2Tag.put("evasionTrackers", new ListTag());

        assertEquals(v2Tag, NationWarsSavedData.migrate(v2Tag));
    }

    @Test
    void refusesToLoadAFutureSchemaVersion()
    {
        final CompoundTag futureTag = new CompoundTag();
        futureTag.putInt("schemaVersion", 999);

        assertThrows(IllegalStateException.class, () -> NationWarsSavedData.migrate(futureTag));
    }
}

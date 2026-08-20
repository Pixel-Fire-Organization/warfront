package org.pixelfire.nationwars.io;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NationWarsSavedDataTest
{
    @Test
    void freshInstanceHasAnEmptyPayloadAndIsNotDirty()
    {
        final NationWarsSavedData data = new NationWarsSavedData();

        assertEquals("", data.dummyPayload());
        assertFalse(data.isDirty());
    }

    @Test
    void settingThePayloadMarksItDirty()
    {
        final NationWarsSavedData data = new NationWarsSavedData();

        data.setDummyPayload("hello");

        assertEquals("hello", data.dummyPayload());
        assertTrue(data.isDirty());
    }

    @Test
    void saveThenLoadRoundTripsThePayloadAndSchemaVersion()
    {
        final NationWarsSavedData original = new NationWarsSavedData();
        original.setDummyPayload("a dummy payload");

        final CompoundTag tag = original.save(new CompoundTag());
        assertEquals(NationWarsSavedData.CURRENT_SCHEMA_VERSION, tag.getInt("schemaVersion"));

        final NationWarsSavedData reloaded = NationWarsSavedData.load(tag);

        assertEquals("a dummy payload", reloaded.dummyPayload());
    }

    @Test
    void loadingAnEmptyTagProducesAFreshInstance()
    {
        final NationWarsSavedData reloaded = NationWarsSavedData.load(new CompoundTag());

        assertEquals("", reloaded.dummyPayload());
    }

    @Test
    void loadingAFutureSchemaVersionRefusesRatherThanRiskDataLoss()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", NationWarsSavedData.CURRENT_SCHEMA_VERSION + 1);

        assertThrows(IllegalStateException.class, () -> NationWarsSavedData.load(tag));
    }
}

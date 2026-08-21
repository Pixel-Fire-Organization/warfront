package org.pixelfire.nationwars.io;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.pixelfire.nationwars.state.Coalition;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.War;
import org.pixelfire.nationwars.state.WarOutcome;
import org.pixelfire.nationwars.state.WarPhase;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses a {@link War} (not a {@link org.pixelfire.nationwars.state.City} or {@link
 * org.pixelfire.nationwars.state.Checkpoint}, which carry a real {@code ResourceKey<Level>} dimension —
 * constructing one reaches into {@code BuiltInRegistries}, which refuses to initialize outside a fully
 * bootstrapped game, per {@link org.pixelfire.nationwars.world.ColumnRegistryTest}'s precedent) to keep
 * this test runnable standalone while still exercising the same save/load round trip.
 */
class NationWarsSavedDataTest
{
    private static War testWar()
    {
        final UUID primary = UUID.randomUUID();
        final Coalition solo = new Coalition(Set.of(primary), Map.of(), primary);
        return new War(UUID.randomUUID(), solo, solo, WarPhase.ACTIVE, 0L, 0L, 100L, Set.of(), Set.of(), Map.of(),
                0L, 0L, 0L, WarOutcome.TIMEOUT, Map.of());
    }

    @Test
    void freshInstanceAppliesNothing()
    {
        final NationWarsSavedData data = new NationWarsSavedData();
        final NationRegistry registry = new NationRegistry(4);

        data.applyTo(registry);

        assertTrue(registry.wars().isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsAWarAndSchemaVersion()
    {
        final NationRegistry registry = new NationRegistry(4);
        final War war = testWar();
        registry.wars().put(war.warId(), war);

        final NationWarsSavedData original = new NationWarsSavedData();
        original.syncFromRegistry(registry);

        final CompoundTag tag = original.save(new CompoundTag());
        assertEquals(NationWarsSavedData.CURRENT_SCHEMA_VERSION, tag.getInt("schemaVersion"));

        final NationWarsSavedData reloaded = NationWarsSavedData.load(tag);
        final NationRegistry reloadedRegistry = new NationRegistry(4);
        reloaded.applyTo(reloadedRegistry);

        assertEquals(war, reloadedRegistry.wars().get(war.warId()));
    }

    @Test
    void loadingAnEmptyTagProducesAnInstanceThatAppliesNothing()
    {
        final NationWarsSavedData reloaded = NationWarsSavedData.load(new CompoundTag());
        final NationRegistry registry = new NationRegistry(4);

        reloaded.applyTo(registry);

        assertTrue(registry.wars().isEmpty());
    }

    @Test
    void loadingAFutureSchemaVersionRefusesRatherThanRiskDataLoss()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", NationWarsSavedData.CURRENT_SCHEMA_VERSION + 1);

        assertThrows(IllegalStateException.class, () -> NationWarsSavedData.load(tag));
    }
}

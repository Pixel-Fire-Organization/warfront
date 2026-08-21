package org.pixelfire.nationwars.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NationRegistryTest
{
    @Test
    void rejectsNonPositiveLockStripes()
    {
        assertThrows(IllegalArgumentException.class, () -> new NationRegistry(0));
    }

    @Test
    void putThenGetReturnsTheSameCityRecord()
    {
        final NationRegistry registry = new NationRegistry(4);
        final UUID cityId = UUID.randomUUID();
        final City city = newCity(cityId);

        registry.cities().put(cityId, city);

        assertSame(city, registry.cities().get(cityId));
    }

    @Test
    void unknownIdsReturnNull()
    {
        final NationRegistry registry = new NationRegistry(4);

        assertNull(registry.cities().get(UUID.randomUUID()));
        assertNull(registry.checkpoints().get(UUID.randomUUID()));
        assertNull(registry.wars().get(UUID.randomUUID()));
        assertNull(registry.nationStates().get(UUID.randomUUID()));
    }

    @Test
    void mutationReplacesTheRecordRatherThanEditingItInPlace()
    {
        final NationRegistry registry = new NationRegistry(4);
        final UUID warId = UUID.randomUUID();
        final War original = new War(warId);
        final War replacement = new War(warId);

        registry.wars().put(warId, original);
        registry.wars().put(warId, replacement);

        assertSame(replacement, registry.wars().get(warId));
    }

    @Test
    void eachRegistryHasItsOwnStripedLocksInstance()
    {
        final NationRegistry a = new NationRegistry(4);
        final NationRegistry b = new NationRegistry(4);

        assertNotSame(a.stripedLocks(), b.stripedLocks());
    }

    @SuppressWarnings("unchecked")
    private static City newCity(final UUID cityId)
    {
        final ResourceKey<Level> dimension = Mockito.mock(ResourceKey.class);
        return new City(cityId, "Testville", UUID.randomUUID(), UUID.randomUUID(), dimension, BlockPos.ZERO,
                0, 0L, Set.of(), CityState.ACTIVE, null, 0L, 0L, 0L, 0L, 0, 0L, 0L);
    }
}

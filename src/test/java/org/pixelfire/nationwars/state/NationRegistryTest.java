package org.pixelfire.nationwars.state;

import org.junit.jupiter.api.Test;

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
        final City city = new City(cityId);

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
}

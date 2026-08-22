package org.pixelfire.nationwars.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Uses mocked dimension keys rather than a real {@code ResourceKey<Level>}: constructing one for real
 * (even {@code Level.OVERWORLD}, or building one from scratch via {@code ResourceKey.create}) reaches
 * into {@code BuiltInRegistries}, which refuses to initialize outside a fully bootstrapped game.
 * {@link ColumnRegistry} only ever treats a dimension key as an opaque identity key, so a mock with
 * default identity equals/hashCode exercises the same logic as a real one would.
 */
class ColumnRegistryTest
{
    private static final ResourceKey<Level> OVERWORLD = mockDimensionKey();
    private static final ResourceKey<Level> NETHER = mockDimensionKey();

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> mockDimensionKey()
    {
        return mock(ResourceKey.class);
    }

    @Test
    void aPositionInsideARegisteredColumnIsDetected()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        final BlockPos corePos = new BlockPos(100, 64, 100);
        registry.register(OVERWORLD, corePos);

        assertTrue(registry.isInsideAnyColumn(OVERWORLD, new BlockPos(100, 200, 100)));
        assertTrue(registry.isInsideAnyColumn(OVERWORLD, new BlockPos(99, 100, 101)));
    }

    @Test
    void aPositionOutsideTheColumnFootprintIsNotDetected()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        registry.register(OVERWORLD, new BlockPos(100, 64, 100));

        assertFalse(registry.isInsideAnyColumn(OVERWORLD, new BlockPos(103, 200, 100)));
    }

    @Test
    void aPositionAtOrBelowTheCoreIsNotInsideItsOwnColumn()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        final BlockPos corePos = new BlockPos(100, 64, 100);
        registry.register(OVERWORLD, corePos);

        assertFalse(registry.isInsideAnyColumn(OVERWORLD, corePos));
        assertFalse(registry.isInsideAnyColumn(OVERWORLD, new BlockPos(100, 10, 100)));
    }

    @Test
    void differentDimensionsDoNotShareColumns()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        registry.register(OVERWORLD, new BlockPos(100, 64, 100));

        assertFalse(registry.isInsideAnyColumn(NETHER, new BlockPos(100, 200, 100)));
    }

    @Test
    void unregisterRemovesTheColumn()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        final BlockPos corePos = new BlockPos(100, 64, 100);
        registry.register(OVERWORLD, corePos);

        registry.unregister(OVERWORLD, corePos);

        assertFalse(registry.isInsideAnyColumn(OVERWORLD, new BlockPos(100, 200, 100)));
    }

    @Test
    void aColumnStraddlingAChunkBoundaryIsFoundFromEitherChunk()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        // x=16 is the first block of chunk (1,0); the column's footprint (15-17) straddles chunk 0 and chunk 1.
        final BlockPos corePos = new BlockPos(16, 64, 8);
        registry.register(OVERWORLD, corePos);

        assertFalse(registry.columnsNear(OVERWORLD, new ChunkPos(0, 0)).isEmpty());
        assertFalse(registry.columnsNear(OVERWORLD, new ChunkPos(1, 0)).isEmpty());
    }

    @Test
    void allInReturnsEveryRegisteredColumnForThatDimension()
    {
        final ColumnRegistry registry = new ColumnRegistry();
        registry.register(OVERWORLD, new BlockPos(0, 64, 0));
        registry.register(OVERWORLD, new BlockPos(500, 64, 500));
        registry.register(NETHER, new BlockPos(0, 64, 0));

        assertEquals(2, registry.allIn(OVERWORLD).size());
        assertEquals(1, registry.allIn(NETHER).size());
    }
}

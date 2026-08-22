package org.pixelfire.nationwars.world.block;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import org.pixelfire.nationwars.NationWarsMod;

public final class NationWarsBlockEntities
{
    public static final RegistryObject<BlockEntityType<CityCoreBlockEntity>> CITY_CORE = NationWarsMod.BLOCK_ENTITY_TYPES.register(
            "city_core", () -> BlockEntityType.Builder.of(CityCoreBlockEntity::new, NationWarsBlocks.CITY_CORE.get()).build(null));

    public static final RegistryObject<BlockEntityType<CheckpointBlockEntity>> CHECKPOINT = NationWarsMod.BLOCK_ENTITY_TYPES.register(
            "checkpoint", () -> BlockEntityType.Builder.of(CheckpointBlockEntity::new, NationWarsBlocks.CHECKPOINT.get()).build(null));

    private NationWarsBlockEntities()
    {
    }

    public static void bootstrap()
    {
    }
}

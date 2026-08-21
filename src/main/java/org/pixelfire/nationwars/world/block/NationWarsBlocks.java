package org.pixelfire.nationwars.world.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;
import org.pixelfire.nationwars.NationWarsMod;

public final class NationWarsBlocks
{
    public static final RegistryObject<Block> CITY_CORE = NationWarsMod.BLOCKS.register("city_core",
            () -> new CityCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .lightLevel(state -> 10)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<Item> CITY_CORE_ITEM = NationWarsMod.ITEMS.register("city_core",
            () -> new BlockItem(CITY_CORE.get(), new Item.Properties()));

    public static final RegistryObject<Block> CHECKPOINT = NationWarsMod.BLOCKS.register("checkpoint",
            () -> new CheckpointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<Item> CHECKPOINT_ITEM = NationWarsMod.ITEMS.register("checkpoint",
            () -> new BlockItem(CHECKPOINT.get(), new Item.Properties()));

    private NationWarsBlocks()
    {
    }

    /** Forces this class to load (and its {@code RegistryObject}s to be created) before registration fires. */
    public static void bootstrap()
    {
    }
}

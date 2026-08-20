package org.pixelfire.nationwars;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.world.OpacIntegration;
import org.slf4j.Logger;

import java.util.function.Supplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(NationWarsMod.MODID)
public class NationWarsMod
{
    public static final String MODID = "nationwars";

    private static final Logger LOGGER = LogUtils.getLogger();

    // Vanilla-registry deferred registers. Empty for now — later stages register the City Core and
    // Checkpoint blocks/items/block entities and the City GUI's menu type here.
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    // A brand-new, mod-owned registry for peace settlement clauses (state.PeaceClause). New clause
    // types register into this with no change needed to the settlement pipeline that applies them.
    // No clause types are registered yet.
    public static final DeferredRegister<PeaceClause> PEACE_CLAUSES =
            DeferredRegister.create(ResourceLocation.tryBuild(MODID, "peace_clause"), MODID);
    public static final Supplier<IForgeRegistry<PeaceClause>> PEACE_CLAUSE_REGISTRY = PEACE_CLAUSES.makeRegistry(RegistryBuilder::new);

    public NationWarsMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        PEACE_CLAUSES.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        // Type.SERVER since these are gameplay constants synced from the server, not client preferences.
        context.registerConfig(ModConfig.Type.SERVER, NationWarsConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        OpacIntegration.verifyAvailable();
        LOGGER.info("nationwars common setup complete; Open Parties and Claims found on the classpath");
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event)
    {
        LOGGER.info("nationwars starting");
    }
}

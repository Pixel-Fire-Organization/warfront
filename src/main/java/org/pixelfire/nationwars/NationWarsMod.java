package org.pixelfire.nationwars;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
import org.apache.logging.log4j.Level;
import org.pixelfire.nationwars.compute.WorkerPool;
import org.pixelfire.nationwars.config.NationWarsConfig;
import org.pixelfire.nationwars.io.NationWarsLogging;
import org.pixelfire.nationwars.io.NationWarsSavedData;
import org.pixelfire.nationwars.io.WriterThread;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.world.OpacIntegration;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
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

    // No config key exists yet for the I/O writer's queue depth (Appendix A only sizes the compute
    // worker pool's queue); this default carries it until audit logging (a later stage) needs to
    // make it tunable.
    private static final int WRITER_QUEUE_CAPACITY = 256;

    // Threading foundation: a fresh registry, worker pool, and writer thread per server lifecycle,
    // created before any feature that needs them and torn down cleanly when the server stops.
    private NationRegistry nationRegistry;
    private WorkerPool workerPool;
    private WriterThread writerThread;

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

    private void registerDiagnosticLogging()
    {
        // ModConfig.Type.SERVER is per-world and is only loaded once a server actually starts, so
        // this can't run any earlier than ServerStartingEvent — FMLCommonSetupEvent fires before any
        // world exists and every NationWarsConfig getter below would throw.
        final Level defaultLevel = parseLevel(NationWarsConfig.LOGGING_DEFAULT.get(), Level.INFO);
        final Level consoleLevel = parseLevel(NationWarsConfig.LOG_TO_SERVER_CONSOLE.get(), Level.WARN);

        final Map<String, Level> categoryLevels = new LinkedHashMap<>();
        NationWarsConfig.loggingCategories.forEach((category, levelName) -> categoryLevels.put(category, parseLevel(levelName, defaultLevel)));

        NationWarsLogging.register(
                NationWarsConfig.LOG_FILE_SIZE_MB.get(),
                NationWarsConfig.LOG_FILE_HISTORY.get(),
                consoleLevel,
                categoryLevels,
                defaultLevel);
    }

    private static Level parseLevel(final String name, final Level fallback)
    {
        final Level level = Level.toLevel(name, null);
        if (level == null)
        {
            LOGGER.warn("nationwars config has an invalid log level '{}'; using {} instead", name, fallback);
            return fallback;
        }
        return level;
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event)
    {
        registerDiagnosticLogging();

        final int lockStripes = NationWarsConfig.LOCK_STRIPES.get();
        final int workerThreads = WorkerPool.resolveThreadCount(NationWarsConfig.WORKER_THREADS.get());
        final int workerQueueCapacity = NationWarsConfig.WORKER_QUEUE_CAPACITY.get();

        nationRegistry = new NationRegistry(lockStripes);
        workerPool = new WorkerPool(workerThreads, workerQueueCapacity);
        writerThread = new WriterThread(WRITER_QUEUE_CAPACITY);

        // Attaches (or creates) this mod's save data on the Overworld, proving the persistence
        // skeleton round-trips through a real world folder before any real state lives in it.
        final NationWarsSavedData savedData = NationWarsSavedData.get(event.getServer());

        LOGGER.info("nationwars starting; lockStripes={}, workerThreads={}, workerQueueCapacity={}, "
                        + "savedDataSchemaVersion={}",
                lockStripes, workerThreads, workerQueueCapacity, NationWarsSavedData.CURRENT_SCHEMA_VERSION);
        LOGGER.debug("nationwars save data attached: dummyPayload='{}'", savedData.dummyPayload());
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        if (writerThread != null)
        {
            writerThread.close();
            writerThread = null;
        }
        if (workerPool != null)
        {
            workerPool.close();
            workerPool = null;
        }
        nationRegistry = null;
    }
}

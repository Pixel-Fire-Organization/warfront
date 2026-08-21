package org.pixelfire.nationwars;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.storage.LevelResource;
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
import org.pixelfire.nationwars.io.audit.ActorRole;
import org.pixelfire.nationwars.io.audit.AuditEntry;
import org.pixelfire.nationwars.io.audit.AuditIndex;
import org.pixelfire.nationwars.io.audit.AuditSource;
import org.pixelfire.nationwars.io.audit.AuditWriter;
import org.pixelfire.nationwars.activity.ActivityEventListener;
import org.pixelfire.nationwars.activity.ActivityTracker;
import org.pixelfire.nationwars.activity.CombatLogListener;
import org.pixelfire.nationwars.activity.CombatTagTracker;
import org.pixelfire.nationwars.activity.LoginShieldListener;
import org.pixelfire.nationwars.capture.CaptureTickListener;
import org.pixelfire.nationwars.state.NationRegistry;
import org.pixelfire.nationwars.state.PeaceClause;
import org.pixelfire.nationwars.world.CheckpointBreakListener;
import org.pixelfire.nationwars.world.CheckpointMoveGrace;
import org.pixelfire.nationwars.world.CheckpointPlacementListener;
import org.pixelfire.nationwars.world.CityDormancyListener;
import org.pixelfire.nationwars.world.CityFoundingListener;
import org.pixelfire.nationwars.world.CityRenderEffectsListener;
import org.pixelfire.nationwars.world.ValidationSweepListener;
import org.pixelfire.nationwars.world.ColumnProtectionListener;
import org.pixelfire.nationwars.world.ColumnRegistry;
import org.pixelfire.nationwars.world.OpacIntegration;
import org.pixelfire.nationwars.world.block.NationWarsBlockEntities;
import org.pixelfire.nationwars.world.block.NationWarsBlocks;
import org.pixelfire.nationwars.world.block.NationWarsMenus;
import org.pixelfire.nationwars.network.NationWarsNetwork;
import org.pixelfire.nationwars.settlement.NationWarsPeaceClauses;
import org.pixelfire.nationwars.settlement.NegotiationDraftTracker;
import org.pixelfire.nationwars.settlement.SettlementBackstopListener;
import org.pixelfire.nationwars.settlement.SettlementReverter;
import org.pixelfire.nationwars.world.CheckpointReverters;
import org.pixelfire.nationwars.war.EvasionTickListener;
import org.pixelfire.nationwars.war.WarLifecycleListener;
import org.pixelfire.nationwars.war.WarProtectionListener;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(NationWarsMod.MODID)
public class NationWarsMod
{
    public static final String MODID = "nationwars";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final DeferredRegister<PeaceClause> PEACE_CLAUSES =
            DeferredRegister.create(ResourceLocation.tryBuild(MODID, "peace_clause"), MODID);
    public static final Supplier<IForgeRegistry<PeaceClause>> PEACE_CLAUSE_REGISTRY = PEACE_CLAUSES.makeRegistry(RegistryBuilder::new);

    // No config key exists yet for the I/O writer's queue depth (Appendix A only sizes the compute
    // worker pool's queue); this default carries it until audit logging (a later stage) needs to
    // make it tunable.
    private static final int WRITER_QUEUE_CAPACITY = 256;

    private NationRegistry nationRegistry;
    private WorkerPool workerPool;
    private WriterThread writerThread;
    private AuditWriter auditWriter;
    private AuditIndex auditIndex;
    private Path auditDir;
    private ColumnRegistry columnRegistry;
    private ColumnProtectionListener columnProtectionListener;
    private CityFoundingListener cityFoundingListener;
    private CheckpointPlacementListener checkpointPlacementListener;
    private CheckpointBreakListener checkpointBreakListener;
    private CityDormancyListener cityDormancyListener;
    private ActivityTracker activityTracker;
    private CombatTagTracker combatTagTracker;
    private ActivityEventListener activityEventListener;
    private CombatLogListener combatLogListener;
    private LoginShieldListener loginShieldListener;
    private WarLifecycleListener warLifecycleListener;
    private WarProtectionListener warProtectionListener;
    private CaptureTickListener captureTickListener;
    private EvasionTickListener evasionTickListener;
    private CityRenderEffectsListener cityRenderEffectsListener;
    private ValidationSweepListener validationSweepListener;
    private SettlementBackstopListener settlementBackstopListener;
    private NegotiationDraftTracker negotiationDraftTracker;
    private volatile boolean serverStopping;

    // Forge only ever constructs one instance of a mod's main class; command handlers (which have no
    // other way to reach this instance) resolve it through here.
    private static NationWarsMod instance;

    public NationWarsMod(FMLJavaModLoadingContext context)
    {
        instance = this;

        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        NationWarsBlocks.bootstrap();
        NationWarsBlockEntities.bootstrap();
        NationWarsMenus.bootstrap();
        NationWarsPeaceClauses.bootstrap();
        CheckpointReverters.bootstrap();
        SettlementReverter.bootstrap();
        NationWarsNetwork.register();

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

        columnRegistry = new ColumnRegistry();
        columnProtectionListener = new ColumnProtectionListener(columnRegistry);
        MinecraftForge.EVENT_BUS.register(columnProtectionListener);

        cityFoundingListener = new CityFoundingListener();
        MinecraftForge.EVENT_BUS.register(cityFoundingListener);

        captureTickListener = new CaptureTickListener();
        MinecraftForge.EVENT_BUS.register(captureTickListener);

        settlementBackstopListener = new SettlementBackstopListener();
        MinecraftForge.EVENT_BUS.register(settlementBackstopListener);
        negotiationDraftTracker = new NegotiationDraftTracker();

        final CheckpointMoveGrace checkpointMoveGrace = new CheckpointMoveGrace();
        checkpointPlacementListener = new CheckpointPlacementListener(checkpointMoveGrace);
        MinecraftForge.EVENT_BUS.register(checkpointPlacementListener);
        checkpointBreakListener = new CheckpointBreakListener(checkpointMoveGrace, captureTickListener);
        MinecraftForge.EVENT_BUS.register(checkpointBreakListener);
        cityDormancyListener = new CityDormancyListener();
        MinecraftForge.EVENT_BUS.register(cityDormancyListener);

        serverStopping = false;
        activityTracker = new ActivityTracker();
        combatTagTracker = new CombatTagTracker();
        activityEventListener = new ActivityEventListener(activityTracker);
        MinecraftForge.EVENT_BUS.register(activityEventListener);
        combatLogListener = new CombatLogListener(combatTagTracker);
        MinecraftForge.EVENT_BUS.register(combatLogListener);
        loginShieldListener = new LoginShieldListener(activityTracker);
        MinecraftForge.EVENT_BUS.register(loginShieldListener);
        warLifecycleListener = new WarLifecycleListener();
        MinecraftForge.EVENT_BUS.register(warLifecycleListener);
        warProtectionListener = new WarProtectionListener();
        MinecraftForge.EVENT_BUS.register(warProtectionListener);
        evasionTickListener = new EvasionTickListener();
        MinecraftForge.EVENT_BUS.register(evasionTickListener);
        cityRenderEffectsListener = new CityRenderEffectsListener();
        MinecraftForge.EVENT_BUS.register(cityRenderEffectsListener);
        validationSweepListener = new ValidationSweepListener();
        MinecraftForge.EVENT_BUS.register(validationSweepListener);

        auditDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("nationwars-audit");
        auditWriter = new AuditWriter(auditDir, writerThread);
        auditIndex = new AuditIndex();
        final int auditRetentionDays = NationWarsConfig.AUDIT_RETENTION_DAYS.get();
        auditIndex.rebuildAsync(auditDir, auditRetentionDays, auditWriter.fileLock(), workerPool, event.getServer());

        auditWriter.append(AuditEntry.of(null, "SYSTEM", null, ActorRole.SYSTEM, AuditSource.AUTO,
                ResourceLocation.tryBuild(MODID, "diagnostic_synthetic_entry"), List.of(),
                new CompoundTag(), new CompoundTag(), false));

        final NationWarsSavedData savedData = NationWarsSavedData.get(event.getServer());

        LOGGER.info("nationwars starting; lockStripes={}, workerThreads={}, workerQueueCapacity={}, "
                        + "savedDataSchemaVersion={}, auditRetentionDays={}",
                lockStripes, workerThreads, workerQueueCapacity, NationWarsSavedData.CURRENT_SCHEMA_VERSION, auditRetentionDays);
        LOGGER.debug("nationwars save data attached: dummyPayload='{}'", savedData.dummyPayload());
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event)
    {
        // Set first: ServerStoppingEvent fires before players are kicked, so CombatLogListener's
        // logout handler can already see this when combatLogGraceOnServerStop applies.
        serverStopping = true;

        // AuditWriter has nothing to flush at shutdown: every append() already fully writes and
        // closes its day file synchronously on the writer thread by the time it returns.
        auditWriter = null;
        auditIndex = null;
        auditDir = null;
        if (columnProtectionListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(columnProtectionListener);
            columnProtectionListener = null;
        }
        if (cityFoundingListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(cityFoundingListener);
            cityFoundingListener = null;
        }
        if (checkpointPlacementListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(checkpointPlacementListener);
            checkpointPlacementListener = null;
        }
        if (checkpointBreakListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(checkpointBreakListener);
            checkpointBreakListener = null;
        }
        if (cityDormancyListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(cityDormancyListener);
            cityDormancyListener = null;
        }
        if (activityEventListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(activityEventListener);
            activityEventListener = null;
        }
        if (combatLogListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(combatLogListener);
            combatLogListener = null;
        }
        if (loginShieldListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(loginShieldListener);
            loginShieldListener = null;
        }
        if (warLifecycleListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(warLifecycleListener);
            warLifecycleListener = null;
        }
        if (warProtectionListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(warProtectionListener);
            warProtectionListener = null;
        }
        if (captureTickListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(captureTickListener);
            captureTickListener = null;
        }
        if (evasionTickListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(evasionTickListener);
            evasionTickListener = null;
        }
        if (cityRenderEffectsListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(cityRenderEffectsListener);
            cityRenderEffectsListener = null;
        }
        if (validationSweepListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(validationSweepListener);
            validationSweepListener = null;
        }
        if (settlementBackstopListener != null)
        {
            MinecraftForge.EVENT_BUS.unregister(settlementBackstopListener);
            settlementBackstopListener = null;
        }
        negotiationDraftTracker = null;
        activityTracker = null;
        combatTagTracker = null;
        columnRegistry = null;
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

    public static NationWarsMod get()
    {
        return instance;
    }

    public AuditIndex getAuditIndex()
    {
        return auditIndex;
    }

    public AuditWriter getAuditWriter()
    {
        return auditWriter;
    }

    public Path getAuditDir()
    {
        return auditDir;
    }

    public WorkerPool getWorkerPool()
    {
        return workerPool;
    }

    public WriterThread getWriterThread()
    {
        return writerThread;
    }

    public ColumnRegistry getColumnRegistry()
    {
        return columnRegistry;
    }

    public NationRegistry getNationRegistry()
    {
        return nationRegistry;
    }

    public ActivityTracker getActivityTracker()
    {
        return activityTracker;
    }

    public CombatTagTracker getCombatTagTracker()
    {
        return combatTagTracker;
    }

    public boolean isServerStopping()
    {
        return serverStopping;
    }

    public NegotiationDraftTracker getNegotiationDraftTracker()
    {
        return negotiationDraftTracker;
    }
}

package emaki.jiuwu.craft.dungeon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.apiimpl.ServiceBackedDungeonBridge;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandAdapter;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandRouter;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.orchestration.DestroyArbiter;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldCloner;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldNames;
import emaki.jiuwu.craft.dungeon.world.WorldPool;

public class EmakiDungeonPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String MODULE = "dungeon";
    private static final String ROOT_COMMAND = "edg";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);
    private static final String API_VERSION = "1.0.0";
    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private MessageService messageService;
    private LanguageLoader languageLoader;
    private ServiceBackedDungeonBridge bridge;
    private DebugCommand debugCommand;
    private InstanceCoordinator coordinator;
    private boolean runtimeInitialized;

    public EmakiDungeonPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        languageLoader = new LanguageLoader(this);
        messageService = new MessageService(this, languageLoader, "EmakiDungeon");
        appConfigLoader = new YamlConfigLoader<>(this, "config.yml", AppConfig::defaults, AppConfig::parse);
        appConfigLoader.load();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(EmakiCoreLibPlugin.lookup().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerCommandHandler();
        installPublicApi();
        coordinator = bootstrapInstanceRuntime();
        runtimeInitialized = true;
        messageService.info("console.plugin_starting");
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        if (coordinator != null) {
            coordinator.shutdown();
            coordinator = null;
        }
        if (bridge != null) {
            EmakiDungeonApi.uninstall(bridge);
            bridge = null;
        }
        debugCommand = null;
        runtimeInitialized = false;
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public InstanceCoordinator instanceCoordinator() {
        return coordinator;
    }

    public boolean runtimeInitialized() {
        return runtimeInitialized;
    }

    private void registerCommandHandler() {
        DungeonCommandRouter router = new DungeonCommandRouter(this);
        registerCommand(ROOT_COMMAND, "EmakiDungeon 命令", List.of("dungeon", "edungeon"),
                new DungeonCommandAdapter(router, "emakidungeon.play"));
    }

    private void installPublicApi() {
        String version = getPluginMeta().getVersion();
        bridge = new ServiceBackedDungeonBridge(ApiStatus.ready(getName(), version, API_VERSION));
        EmakiDungeonApi.install(bridge);
    }

    private InstanceCoordinator bootstrapInstanceRuntime() {
        AppConfig appConfig = appConfigLoader.current();
        if (appConfig.debugEnabled()) {
            debugLogger().enableAll();
        }
        ExecutionDispatcher dispatcher = EmakiCoreLibPlugin.lookup().executionDispatcher();
        File worldContainer = Bukkit.getWorldContainer();
        TemplateRepository templates = new TemplateRepository(getDataFolder());
        Map<String, Integer> poolMaxCache = new ConcurrentHashMap<>();
        List<String> dungeons = new ArrayList<>();
        for (String dungeonId : templates.discoverDungeons()) {
            try {
                poolMaxCache.put(dungeonId, templates.loadConfig(dungeonId).poolMaxWorlds());
                dungeons.add(dungeonId);
            } catch (RuntimeException exception) {
                messageService.warning("console.dungeon_config_invalid", Map.of(
                        "dungeon", dungeonId,
                        "error", String.valueOf(exception.getMessage())
                ));
            }
        }
        Function<String, Integer> poolMaxOf = poolMaxCache::get;
        InstanceSlotAllocator allocator = new InstanceSlotAllocator(
                appConfig.maxInstances(),
                foliaDetected(),
                appConfig.foliaHeldWorldsMax(),
                poolMaxOf
        );
        InstanceRegistry registry = new InstanceRegistry();
        DestroyArbiter arbiter = new DestroyArbiter();
        InstancesStore store = new InstancesStore(this, dispatcher, getDataFolder());
        WorldNames names = new WorldNames(worldContainer);
        WorldCloner cloner = new WorldCloner(this, dispatcher, worldContainer);
        WorldDisposer disposer = new WorldDisposer(this, dispatcher, worldContainer);
        WorldPool pool = new WorldPool(this, dispatcher, worldContainer, templates, names, cloner, disposer,
                allocator, registry, store, poolMaxOf, messageService);
        InstanceCoordinator instanceCoordinator = new InstanceCoordinator(this, dispatcher, pool, disposer,
                allocator, registry, arbiter, store, messageService, debugLogger());
        instanceCoordinator.startupScan();
        instanceCoordinator.warmupAll(dungeons);
        instanceCoordinator.startInspection();
        return instanceCoordinator;
    }

    private static boolean foliaDetected() {
        try {
            Class.forName(FOLIA_MARKER, false, EmakiDungeonPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }
}

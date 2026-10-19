package emaki.jiuwu.craft.dungeon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.apiimpl.ServiceBackedDungeonBridge;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeGuiService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandAdapter;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandRouter;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.guard.BoundaryGuard;
import emaki.jiuwu.craft.dungeon.guard.CommandGuard;
import emaki.jiuwu.craft.dungeon.guard.TeleportGuard;
import emaki.jiuwu.craft.dungeon.listener.DungeonChatListener;
import emaki.jiuwu.craft.dungeon.listener.DungeonLifecycleListener;
import emaki.jiuwu.craft.dungeon.orchestration.DestroyArbiter;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.party.PartiesStore;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotsStore;
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
    private ChallengeService challenge;
    private PartyService parties;
    private RecoveryService recovery;
    private SnapshotManager snapshots;
    private CommandGuard commandGuard;
    private BoundaryGuard boundaryGuard;
    private TeleportGuard teleportGuard;
    private ChatInputService chatInputService;
    private GuiService guiService;
    private DungeonCommandRouter router;
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
        bootstrapInstanceRuntime();
        registerCommandHandler();
        installPublicApi();
        runtimeInitialized = true;
        messageService.info("console.plugin_starting");
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        if (challenge != null) {
            challenge.shutdown();
            challenge = null;
        }
        if (boundaryGuard != null) {
            boundaryGuard.shutdown();
            boundaryGuard = null;
        }
        if (chatInputService != null) {
            chatInputService.close();
            chatInputService = null;
        }
        if (guiService != null) {
            guiService.closeAll();
            guiService = null;
        }
        if (parties != null) {
            parties.shutdown();
            parties = null;
        }
        if (commandGuard != null) {
            commandGuard.restoreCommandMap();
            commandGuard = null;
        }
        if (coordinator != null) {
            coordinator.shutdown();
            coordinator = null;
        }
        if (teleportGuard != null) {
            teleportGuard = null;
        }
        recovery = null;
        snapshots = null;
        router = null;
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

    public ChallengeService challengeService() {
        return challenge;
    }

    public PartyService partyService() {
        return parties;
    }

    public RecoveryService recoveryService() {
        return recovery;
    }

    public boolean runtimeInitialized() {
        return runtimeInitialized;
    }

    private void registerCommandHandler() {
        registerCommand(ROOT_COMMAND, "EmakiDungeon 命令", List.of("dungeon", "edungeon"),
                new DungeonCommandAdapter(router, "emakidungeon.play"));
    }

    private void installPublicApi() {
        String version = getPluginMeta().getVersion();
        bridge = new ServiceBackedDungeonBridge(ApiStatus.ready(getName(), version, API_VERSION));
        EmakiDungeonApi.install(bridge);
    }

    private void bootstrapInstanceRuntime() {
        AppConfig appConfig = appConfigLoader.current();
        if (appConfig.debugEnabled()) {
            debugLogger().enableAll();
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.lookup();
        ExecutionDispatcher dispatcher = coreLib.executionDispatcher();
        File worldContainer = Bukkit.getWorldContainer();
        TemplateRepository templates = new TemplateRepository(getDataFolder());

        Map<String, DungeonConfig> configCache = new ConcurrentHashMap<>();
        List<String> dungeons = new ArrayList<>();
        for (String dungeonId : templates.discoverDungeons()) {
            try {
                configCache.put(dungeonId, templates.loadConfig(dungeonId));
                dungeons.add(dungeonId);
            } catch (RuntimeException exception) {
                messageService.warning("console.dungeon_config_invalid", Map.of(
                        "dungeon", dungeonId,
                        "error", String.valueOf(exception.getMessage())
                ));
            }
        }
        Function<String, DungeonConfig> configOf = configCache::get;
        Function<String, Integer> poolMaxOf = dungeonId -> {
            DungeonConfig config = configCache.get(dungeonId);
            return config == null ? null : config.poolMaxWorlds();
        };
        Supplier<AppConfig> appConfigSupplier = appConfigLoader::current;

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

        teleportGuard = new TeleportGuard(messageService, registry);
        SnapshotsStore snapshotsStore = new SnapshotsStore(this, dispatcher, getDataFolder());
        PlayersDataStore playersDataStore = new PlayersDataStore(this, dispatcher, getDataFolder(),
                appConfig.globalChatDefault());
        snapshots = new SnapshotManager(snapshotsStore, playersDataStore, messageService);

        PartiesStore partiesStore = new PartiesStore(this, dispatcher, getDataFolder());
        parties = new PartyService(this, dispatcher, messageService, debugLogger(), partiesStore,
                appConfig.partyMaxSize(), appConfig.partyInviteMode(), allocator::isPartyBusy);
        parties.start();

        recovery = new RecoveryService(snapshots, registry, parties, messageService, debugLogger(),
                configOf, teleportGuard::permit);

        coordinator = new InstanceCoordinator(this, dispatcher, pool, disposer, allocator, registry, arbiter,
                store, messageService, debugLogger(), recovery::evacuate);

        guiService = new GuiService(this, dispatcher, coreLib.asyncTaskScheduler(),
                coreLib.performanceMonitor(), coreLib.guiBackend());
        chatInputService = new ChatInputService(this, dispatcher);
        ChallengeGuiService challengeGui = new ChallengeGuiService(this, guiService, languageLoader);
        challenge = new ChallengeService(this, dispatcher, messageService, languageLoader, debugLogger(),
                parties, allocator, pool, registry, snapshots, challengeGui, configOf, teleportGuard::permit);
        challenge.start();

        commandGuard = new CommandGuard(this, messageService, debugLogger(), registry,
                appConfigSupplier, configOf);
        commandGuard.installCommandMap();

        boundaryGuard = new BoundaryGuard(this, dispatcher, registry, teleportGuard, messageService);
        boundaryGuard.start();

        router = new DungeonCommandRouter(this, messageService, challenge, parties, recovery, snapshots,
                chatInputService, templates, coordinator, registry);

        getServer().getPluginManager().registerEvents(
                new DungeonLifecycleListener(recovery, parties), this);
        getServer().getPluginManager().registerEvents(
                new DungeonChatListener(parties, languageLoader.getMessage("party.chat_format")), this);
        getServer().getPluginManager().registerEvents(commandGuard, this);
        getServer().getPluginManager().registerEvents(teleportGuard, this);

        coordinator.startupScan();
        coordinator.warmupAll(dungeons);
        coordinator.startInspection();
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

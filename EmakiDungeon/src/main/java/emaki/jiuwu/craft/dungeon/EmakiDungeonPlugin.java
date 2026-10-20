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
import org.bukkit.entity.Player;

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
import emaki.jiuwu.craft.dungeon.action.ActionBridge;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.apiimpl.ServiceBackedDungeonBridge;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeGuiService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandAdapter;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandRouter;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.FlowDefinition;
import emaki.jiuwu.craft.dungeon.flow.FlowLoader;
import emaki.jiuwu.craft.dungeon.flow.RegionTracker;
import emaki.jiuwu.craft.dungeon.guard.BoundaryGuard;
import emaki.jiuwu.craft.dungeon.guard.CommandGuard;
import emaki.jiuwu.craft.dungeon.guard.TeleportGuard;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.listener.DungeonChatListener;
import emaki.jiuwu.craft.dungeon.listener.DungeonLifecycleListener;
import emaki.jiuwu.craft.dungeon.listener.FlowEventListeners;
import emaki.jiuwu.craft.dungeon.listener.PlayerStateListener;
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
import emaki.jiuwu.craft.dungeon.state.PlayerStateService;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.tempitem.TempItemManager;
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
    private RegionTracker regionTracker;
    private PlayerStateService stateService;
    private ActionBridge actionBridge;
    private emaki.jiuwu.craft.dungeon.score.ScoreTracker scoreTracker;
    private emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore leaderboard;
    private emaki.jiuwu.craft.dungeon.settlement.SettlementService settlement;
    private emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService rewardBox;
    private emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui rewardBoxGui;
    private emaki.jiuwu.craft.dungeon.drop.RewardChestService rewardChests;
    private emaki.jiuwu.craft.dungeon.editor.EditorService editorService;
    private InstanceRegistry registry;
    private Function<String, DungeonConfig> configOf;
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
        if (regionTracker != null) {
            regionTracker.shutdown();
            regionTracker = null;
        }
        if (stateService != null) {
            stateService.shutdown();
            stateService = null;
        }
        if (actionBridge != null) {
            actionBridge.closeAll();
            actionBridge = null;
        }
        if (boundaryGuard != null) {
            boundaryGuard.shutdown();
            boundaryGuard = null;
        }
        if (editorService != null) {
            editorService.closeAll();
            editorService = null;
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
        bridge = new ServiceBackedDungeonBridge(
                ApiStatus.ready(getName(), version, API_VERSION),
                challenge, parties, registry, coordinator, configOf);
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
        TemplateRepository templates = new TemplateRepository(this, getDataFolder());

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
        this.configOf = configOf;
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
        registry = new InstanceRegistry();
        DestroyArbiter arbiter = new DestroyArbiter();
        InstancesStore store = new InstancesStore(this, dispatcher, getDataFolder());
        WorldNames names = new WorldNames(worldContainer);
        WorldCloner cloner = new WorldCloner(this, dispatcher, worldContainer);
        WorldDisposer disposer = new WorldDisposer(this, dispatcher, worldContainer);
        emaki.jiuwu.craft.dungeon.editor.EditorStateStore editorStates =
                new emaki.jiuwu.craft.dungeon.editor.EditorStateStore(this, getDataFolder());
        editorStates.loadAll(templates.discoverDungeons());
        WorldPool pool = new WorldPool(this, dispatcher, worldContainer, templates, names, cloner, disposer,
                allocator, registry, store, poolMaxOf, editorStates::generationOf, messageService);

        teleportGuard = new TeleportGuard(messageService, registry);
        SnapshotsStore snapshotsStore = new SnapshotsStore(this, dispatcher, getDataFolder());
        PlayersDataStore playersDataStore = new PlayersDataStore(this, dispatcher, getDataFolder(),
                appConfig.globalChatDefault());
        snapshots = new SnapshotManager(snapshotsStore, playersDataStore, messageService);

        emaki.jiuwu.craft.dungeon.limit.LimitService limitService =
                new emaki.jiuwu.craft.dungeon.limit.LimitService(playersDataStore, configOf,
                        appConfigSupplier, debugLogger(), (dungeonId, period) ->
                                debugLogger().logRaw("dungeon", (java.util.UUID) null,
                                        "daily_reset | dungeon=" + dungeonId + " | period=" + period));
        leaderboard = new emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore(
                this, dispatcher, getDataFolder());

        PartiesStore partiesStore = new PartiesStore(this, dispatcher, getDataFolder());
        parties = new PartyService(this, dispatcher, messageService, debugLogger(), partiesStore,
                appConfig.partyMaxSize(), appConfig.partyInviteMode(), allocator::isPartyBusy);
        parties.start();

        recovery = new RecoveryService(snapshots, registry, parties, messageService, debugLogger(),
                teleportGuard::permit);

        coordinator = new InstanceCoordinator(this, dispatcher, pool, disposer, allocator, registry, arbiter,
                store, messageService, debugLogger(), recovery::evacuate);

        guiService = new GuiService(this, dispatcher, coreLib.asyncTaskScheduler(),
                coreLib.performanceMonitor(), coreLib.guiBackend());
        chatInputService = new ChatInputService(this, dispatcher);
        editorService = new emaki.jiuwu.craft.dungeon.editor.EditorService(this, dispatcher, messageService,
                chatInputService, cloner, disposer, templates, editorStates, worldContainer, getDataFolder());
        editorService.bindPool(pool);
        editorService.bindConfigCache(configCache);
        emaki.jiuwu.craft.dungeon.editor.EditorGui editorGui =
                new emaki.jiuwu.craft.dungeon.editor.EditorGui(this, editorService, messageService,
                        chatInputService);
        editorService.bindGui(editorGui);
        ChallengeGuiService challengeGui = new ChallengeGuiService(this, guiService, languageLoader);
        challenge = new ChallengeService(this, dispatcher, messageService, languageLoader, debugLogger(),
                parties, allocator, pool, registry, snapshots, challengeGui, configOf, teleportGuard::permit);
        challenge.bindLimitService(limitService);
        challenge.start();
        editorService.bindChallenge(challenge);

        emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge =
                new ActionBridge(this, appConfigSupplier, registry, messageService);
        emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItemManager =
                new TempItemManager(messageService, debugLogger());
        emaki.jiuwu.craft.dungeon.drop.RewardChestService rewardChests =
                new emaki.jiuwu.craft.dungeon.drop.RewardChestService(this, registry, messageService);
        this.rewardChests = rewardChests;
        rewardBox = new emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService(
                new emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxStore(this, dispatcher, getDataFolder()),
                messageService, appConfigSupplier);
        rewardBox.loadGuiConfig(this);
        rewardBoxGui = new emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui(rewardBox, messageService);
        emaki.jiuwu.craft.dungeon.drop.DropRouter dropRouter =
                new emaki.jiuwu.craft.dungeon.drop.DropRouter(registry, parties, messageService,
                        rewardChests, rewardBox, configOf, this, dispatcher);
        emaki.jiuwu.craft.dungeon.isolation.MessageIsolation isolation =
                new emaki.jiuwu.craft.dungeon.isolation.MessageIsolation(registry, playersDataStore, messageService);
        stateService = new PlayerStateService(this, dispatcher, coordinator, registry,
                        parties, recovery, messageService, debugLogger(), configOf, languageLoader);
        stateService.start();
        regionTracker = new RegionTracker(this, dispatcher, registry);
        scoreTracker = new emaki.jiuwu.craft.dungeon.score.ScoreTracker();
        coordinator.bindScoreTracker(scoreTracker);
        coordinator.bindDropCleanup(instanceId -> rewardChests.remove(instanceId));
        recovery.bindStaging(rewardChests::clearStaging);
        settlement = new emaki.jiuwu.craft.dungeon.settlement.SettlementService(
                this, dispatcher, registry, parties, scoreTracker, snapshots, recovery,
                rewardChests, leaderboard, coordinator,
                new emaki.jiuwu.craft.dungeon.settlement.SettlementCommandRunner(debugLogger()),
                rewardBox, messageService, debugLogger(), configOf);
        settlement.bindActionBridge(actionBridge);
        this.actionBridge = actionBridge;

        recovery.bindActionBridge(actionBridge);
        recovery.bindTempItems(tempItemManager);
        stateService.bindActionBridge(actionBridge);
        coordinator.bindActionBridge(actionBridge);
        challenge.bindActionBridge(actionBridge);
        challenge.bindInstanceHooks(
                instance -> buildFlowRuntime(instance, stateService, actionBridge, tempItemManager),
                stateService, tempItemManager);

        commandGuard = new CommandGuard(this, messageService, debugLogger(), registry,
                appConfigSupplier, configOf);
        commandGuard.installCommandMap();

        boundaryGuard = new BoundaryGuard(this, dispatcher, registry, teleportGuard, messageService);
        boundaryGuard.start();
        regionTracker.start();

        router = new DungeonCommandRouter(this, messageService, challenge, parties, recovery, snapshots,
                chatInputService, templates, coordinator, registry, isolation);
        router.bindOperations(limitService, leaderboard, configOf);
        router.bindRewardBox(rewardBoxGui);
        router.bindEditor(editorService);

        getServer().getPluginManager().registerEvents(
                new DungeonLifecycleListener(recovery, parties), this);
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(editorService, this);
        getServer().getPluginManager().registerEvents(editorGui, this);
        getServer().getPluginManager().registerEvents(
                new DungeonChatListener(parties, isolation, languageLoader.getMessage("party.chat_format")), this);
        getServer().getPluginManager().registerEvents(commandGuard, this);
        getServer().getPluginManager().registerEvents(teleportGuard, this);
        getServer().getPluginManager().registerEvents(
                new PlayerStateListener(registry, stateService), this);
        getServer().getPluginManager().registerEvents(
                new FlowEventListeners(registry), this);
        getServer().getPluginManager().registerEvents(
                new emaki.jiuwu.craft.dungeon.score.CombatDataListener(registry, scoreTracker), this);
        getServer().getPluginManager().registerEvents(dropRouter, this);
        getServer().getPluginManager().registerEvents(rewardChests, this);
        getServer().getPluginManager().registerEvents(rewardBoxGui, this);
        getServer().getPluginManager().registerEvents(isolation, this);

        actionBridge.registerAll();

        for (String dungeonId : dungeons) {
            try {
                templates.ensureFlowFile(dungeonId);
            } catch (java.io.IOException exception) {
                messageService.warning("console.flow_file_failed", Map.of(
                        "dungeon", dungeonId,
                        "error", String.valueOf(exception.getMessage())
                ));
            }
        }

        editorService.sweepLeftoverWorlds();
        coordinator.startupScan();
        coordinator.warmupAll(dungeons);
        coordinator.startInspection();
    }

    private emaki.jiuwu.craft.dungeon.flow.FlowRuntime buildFlowRuntime(
            DungeonInstance instance,
            emaki.jiuwu.craft.dungeon.state.PlayerStateService stateService,
            emaki.jiuwu.craft.dungeon.action.ActionBridge actionBridge,
            emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItemManager) {
        var flowLoader = new emaki.jiuwu.craft.dungeon.flow.FlowLoader();
        emaki.jiuwu.craft.dungeon.flow.FlowDefinition definition;
        java.io.File flowFile = new File(new File(worldContainerOf(), instance.instanceId()), "flow.yml");
        java.io.File templateFlow = new File(new File(getDataFolder(), "world"),
                instance.dungeonId() + File.separator + "flow.yml");
        try {
            definition = flowLoader.load(flowFile.isFile() ? flowFile : templateFlow);
        } catch (RuntimeException exception) {
            messageService.warning("console.flow_load_failed", Map.of(
                    "instance", instance.instanceId(),
                    "error", String.valueOf(exception.getMessage())
            ));
            definition = new emaki.jiuwu.craft.dungeon.flow.FlowDefinition();
        }
        var adjudicator = new emaki.jiuwu.craft.dungeon.flow.Adjudicator(
                new emaki.jiuwu.craft.dungeon.flow.ConditionEvaluator(),
                new emaki.jiuwu.craft.dungeon.flow.TriggerCounter());
        var runtime = new emaki.jiuwu.craft.dungeon.flow.FlowRuntime(
                instance, definition, adjudicator, messageService, teleportGuard::permit);
        var gateController = new emaki.jiuwu.craft.dungeon.flow.GateController(this, instance, debugLogger());
        var waveController = new emaki.jiuwu.craft.dungeon.flow.WaveController(
                this, EmakiCoreLibPlugin.lookup().executionDispatcher(), instance,
                new emaki.jiuwu.craft.dungeon.flow.MobSource(), debugLogger(), configOf);
        gateController.bindWaveController(waveController);
        var controllers = new emaki.jiuwu.craft.dungeon.flow.GameplayControllers(
                this, instance, messageService, chatInputService, debugLogger(),
                gateController, waveController);
        controllers.bindHandlers(
                reason -> stateService.fail(instance, reason),
                (itemId, amount) -> dispatchTempItemGrant(instance, itemId, amount, tempItemManager));
        controllers.bindSettlementHandlers(
                cause -> settlement.beginComplete(instance, cause),
                (optionCase, endingId) -> settlement.onEnding(instance, optionCase, endingId));
        controllers.bindRewardChests(rewardChests);
        waveController.bindListeners(
                (spawnId, wave) -> actionBridge.dispatch("dungeon_wave_start", null, Map.of(
                        "instanceId", instance.instanceId(),
                        "spawnId", spawnId,
                        "wave", String.valueOf(wave)
                )),
                (gateId, condition) -> {
                    actionBridge.dispatch("dungeon_gate_open", null, Map.of(
                            "instanceId", instance.instanceId(),
                            "gateId", gateId,
                            "condition", condition
                    ));
                    Bukkit.getPluginManager().callEvent(
                            new emaki.jiuwu.craft.dungeon.api.event.DungeonGateOpenEvent(
                                    instance.instanceId(), instance.dungeonId(), gateId, condition));
                },
                roomId -> {
                    actionBridge.dispatch("dungeon_room_clear", null, Map.of(
                            "instanceId", instance.instanceId(),
                            "roomId", roomId
                    ));
                    Bukkit.getPluginManager().callEvent(
                            new emaki.jiuwu.craft.dungeon.api.event.DungeonRoomClearEvent(
                                    instance.instanceId(), instance.dungeonId(), roomId));
                });
        runtime.bindControllers(controllers);
        runtime.bindStateHook(stateService::onPlayerDeath);
        return runtime;
    }

    private void dispatchTempItemGrant(DungeonInstance instance, String itemId, int amount,
            emaki.jiuwu.craft.dungeon.tempitem.TempItemManager tempItemManager) {
        EmakiCoreLibPlugin.lookup().executionDispatcher().runGlobal(this, () -> {
            var world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                return;
            }
            for (Player member : world.getPlayers()) {
                tempItemManager.grant(instance.instanceId(), member, itemId, amount);
            }
        });
    }

    private File worldContainerOf() {
        return Bukkit.getWorldContainer();
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

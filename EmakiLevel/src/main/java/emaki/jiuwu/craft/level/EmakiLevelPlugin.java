package emaki.jiuwu.craft.level;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.action.LevelActionRegistrar;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperationType;
import emaki.jiuwu.craft.level.api.LevelTypeView;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.api.PlayerLevelEntryView;
import emaki.jiuwu.craft.level.api.PlayerLevelView;
import emaki.jiuwu.craft.level.bridge.MythicLevelDropBridge;
import emaki.jiuwu.craft.level.command.LevelCommand;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelConfigPrecheckContributor;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.listener.LevelGameplaySubscriber;
import emaki.jiuwu.craft.level.listener.PlayerDataListener;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.loader.LevelTypeLoader;
import emaki.jiuwu.craft.level.loader.RequirementLoader;
import emaki.jiuwu.craft.level.loader.SourceRuleLoader;
import emaki.jiuwu.craft.level.papi.LevelPlaceholderExpansion;
import emaki.jiuwu.craft.level.placeholder.LevelCorePlaceholderResolver;
import emaki.jiuwu.craft.level.service.LevelAntiAbuseService;
import emaki.jiuwu.craft.level.service.LevelAttributeBridge;
import emaki.jiuwu.craft.level.service.LevelExperienceRuleService;
import emaki.jiuwu.craft.level.service.LevelGuiService;
import emaki.jiuwu.craft.level.service.LevelMessageService;
import emaki.jiuwu.craft.level.service.LevelPdcService;
import emaki.jiuwu.craft.level.service.LevelTopGuiService;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.LevelTypeRegistry;
import emaki.jiuwu.craft.level.service.PlayerLevelDataStore;
import emaki.jiuwu.craft.level.service.PlayerLevelService;
import emaki.jiuwu.craft.level.service.RequirementService;
import emaki.jiuwu.craft.level.script.ScriptLevelModuleApi;
import emaki.jiuwu.craft.level.script.js.JavaScriptLevelExpRuleRegistry;
import emaki.jiuwu.craft.level.script.js.JavaScriptLevelUpHookRegistry;

public final class EmakiLevelPlugin extends JavaPlugin implements DebugLoggerProvider {

    private static final int BSTATS_PLUGIN_ID = 31794;
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __      ______  __   ________  __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\    /\\  ___\\/\\ \\ / /\\  ___\\/\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\  __\\\\ \\ \\'/\\ \\  __\\\\ \\ \\____
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\__| \\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/   \\/_____/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x7DD3FC;
    private static final int STARTUP_ASCII_END_COLOR = 0xC084FC;
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of(
            "requirements.yml",
            "gui/level_gui.yml",
            "gui/top_gui.yml",
            "types/main.yml",
            "types/combat.yml",
            "types/mining.yml",
            "types/logging.yml",
            "types/farming.yml",
            "types/fishing.yml",
            "types/gathering.yml",
            "types/crafting.yml",
            "types/brewing.yml",
            "types/cooking.yml",
            "types/forging.yml",
            "types/taming.yml",
            "types/smelting.yml",
            "sources/combat.yml",
            "sources/block_actions.yml",
            "sources/fishing.yml",
            "sources/crafting.yml",
            "sources/brewing.yml",
            "sources/taming.yml",
            "sources/smelting.yml",
            "sources/mythicmobs.yml"
    );
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");
    private static final Set<String> DEBUG_MODULES = Set.of("script");

    private EmakiCoreLibPlugin coreLib;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private AppConfig appConfig = AppConfig.defaults();
    private LevelMessageService messages;
    private LanguageLoader debugLanguageLoader;
    private DebugLogger debugLogger;
    private DebugCommand debugCommand;
    private AbstractMessageService debugMessageService;
    private BootstrapService bootstrapService;
    private LevelTypeLoader typeLoader;
    private RequirementLoader requirementLoader;
    private SourceRuleLoader sourceRuleLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private GuiService guiService;
    private LevelTypeRegistry typeRegistry;
    private RequirementService requirementService;
    private PlayerLevelDataStore dataStore;
    private LevelPdcService pdcService;
    private LevelExperienceRuleService experienceRuleService;
    private JavaScriptLevelExpRuleRegistry javaScriptExpRuleRegistry;
    private JavaScriptLevelUpHookRegistry javaScriptLevelUpHookRegistry;
    private LevelAntiAbuseService antiAbuseService;
    private PlayerLevelService levelService;
    private LevelTopService topService;
    private LevelGuiService levelGuiService;
    private LevelTopGuiService levelTopGuiService;
    private LevelAttributeBridge attributeBridge;
    private MythicLevelDropBridge mythicDropBridge;
    private LevelGameplaySubscriber gameplaySubscriber;
    private PlayerDataListener playerDataListener;
    private LevelCorePlaceholderResolver corePlaceholderResolver;
    private LevelPlaceholderExpansion placeholderExpansion;
    private BStatsRegistration metrics;
    private final EmakiLevelApi.Bridge levelApiBridge = new EmakiLevelApi.Bridge() {
        @Override
        public Optional<LevelTypeView> type(String typeId) {
            return typeRegistry.type(typeId).map(EmakiLevelPlugin.this::view);
        }

        @Override
        public Collection<LevelTypeView> types() {
            return typeRegistry.all().stream().map(EmakiLevelPlugin.this::view).toList();
        }

        @Override
        public CompletableFuture<PlayerLevelView> getPlayerData(UUID uuid) {
            return dataStore.getOrLoadAsync(uuid, typeRegistry.asMap())
                    .thenApply(data -> data == null
                            ? new PlayerLevelView(uuid, "", Map.of())
                            : playerView(data));
        }

        @Override
        public int getLevel(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0 : entry.level();
        }

        @Override
        public double getExp(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0D : entry.exp();
        }

        @Override
        public double getTotalExp(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0D : entry.totalExp();
        }

        @Override
        public double getRequiredExp(UUID uuid, String typeId, int targetLevel) {
            LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
            if (type == null) {
                return 0D;
            }
            return requirementService.requiredExp(type, entry(uuid, typeId), targetLevel);
        }

        @Override
        public LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.addExp(uuid, typeId, amount, reason)
                    : ownerFailure(LevelOperationType.ADD_EXP, typeId);
        }

        @Override
        public LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.removeExp(uuid, typeId, amount, reason)
                    : ownerFailure(LevelOperationType.REMOVE_EXP, typeId);
        }

        @Override
        public LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.setExp(uuid, typeId, amount, reason)
                    : ownerFailure(LevelOperationType.SET_EXP, typeId);
        }

        @Override
        public LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.addLevel(uuid, typeId, amount, reason)
                    : ownerFailure(LevelOperationType.ADD_LEVEL, typeId);
        }

        @Override
        public LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.removeLevel(uuid, typeId, amount, reason)
                    : ownerFailure(LevelOperationType.REMOVE_LEVEL, typeId);
        }

        @Override
        public LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason) {
            return ownsWriteTarget(uuid)
                    ? levelService.setLevel(uuid, typeId, level, reason)
                    : ownerFailure(LevelOperationType.SET_LEVEL, typeId);
        }

        @Override
        public LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause) {
            return ownsWriteTarget(uuid)
                    ? levelService.levelUp(uuid, typeId, cause)
                    : ownerFailure(LevelOperationType.LEVEL_UP, typeId);
        }

        @Override
        public CompletableFuture<LevelOperationResult> addExpAsync(UUID uuid, String typeId, double amount, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.ADD_EXP, typeId,
                    () -> levelService.addExp(uuid, typeId, amount, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> removeExpAsync(UUID uuid, String typeId, double amount, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.REMOVE_EXP, typeId,
                    () -> levelService.removeExp(uuid, typeId, amount, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> setExpAsync(UUID uuid, String typeId, double amount, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.SET_EXP, typeId,
                    () -> levelService.setExp(uuid, typeId, amount, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> addLevelAsync(UUID uuid, String typeId, int amount, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.ADD_LEVEL, typeId,
                    () -> levelService.addLevel(uuid, typeId, amount, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> removeLevelAsync(UUID uuid, String typeId, int amount, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.REMOVE_LEVEL, typeId,
                    () -> levelService.removeLevel(uuid, typeId, amount, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> setLevelAsync(UUID uuid, String typeId, int level, String reason) {
            return runOwnerWriteAsync(uuid, LevelOperationType.SET_LEVEL, typeId,
                    () -> levelService.setLevel(uuid, typeId, level, reason));
        }

        @Override
        public CompletableFuture<LevelOperationResult> levelUpAsync(UUID uuid, String typeId, LevelUpCause cause) {
            return runOwnerWriteAsync(uuid, LevelOperationType.LEVEL_UP, typeId,
                    () -> levelService.levelUp(uuid, typeId, cause));
        }
    };

    private boolean ownsWriteTarget(UUID uuid) {
        Player target = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (target == null || !target.isOnline()) {
            return false;
        }
        return threadOwnership != null && threadOwnership.isEntityOwned(target);
    }

    private LevelOperationResult ownerFailure(LevelOperationType operationType, String typeId) {
        return LevelOperationResult.failure("not_entity_owner", operationType, typeId);
    }

    private CompletableFuture<LevelOperationResult> runOwnerWriteAsync(
            UUID uuid,
            LevelOperationType operationType,
            String typeId,
            Supplier<LevelOperationResult> operation) {
        Player target = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (target == null || !target.isOnline()) {
            return CompletableFuture.completedFuture(LevelOperationResult.failure("player_offline", operationType, typeId));
        }
        if (ownsWriteTarget(uuid)) {
            return CompletableFuture.completedFuture(operation.get());
        }
        CompletableFuture<LevelOperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle scheduled = executionDispatcher.runEntity(
                    EmakiLevelPlugin.this,
                    target,
                    () -> {
                        try {
                            future.complete(operation.get());
                        } catch (Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }
                    },
                    () -> future.complete(LevelOperationResult.failure("owner_schedule_retired", operationType, typeId))
            );
            if (scheduled == null) {
                future.complete(LevelOperationResult.failure("owner_schedule_rejected", operationType, typeId));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        executionDispatcher = coreLib.executionDispatcher();
        threadOwnership = coreLib.threadOwnership();
        initializeServices();
        registerConfigPrecheckContributor();
        messages.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        registerCommand();
        registerListeners();
        registerApi();
        registerScriptModule();
        releaseBundledScripts();
        registerActions();
        registerCorePlaceholders();
        registerPlaceholderExpansion();
        registerAttributeBridge();
        registerMythicDrops();
        metrics = coreLib.registerBStats(this, BSTATS_PLUGIN_ID);
        messages.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (coreLib != null) {
            ConfigPrecheckLifecycleSupport.unregister("level");
            coreLib.scriptModuleRegistry().unregister("level");
            if (coreLib.javaScriptRegistrationTracker() != null) {
                coreLib.javaScriptRegistrationTracker().unregisterOwner(this);
            }
        }
        if (javaScriptExpRuleRegistry != null) {
            javaScriptExpRuleRegistry.clear();
        }
        if (javaScriptLevelUpHookRegistry != null) {
            javaScriptLevelUpHookRegistry.clear();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (corePlaceholderResolver != null && coreLib != null && coreLib.placeholderRegistry() != null) {
            coreLib.placeholderRegistry().unregister(corePlaceholderResolver);
            corePlaceholderResolver = null;
        }
        if (attributeBridge != null) {
            attributeBridge.unregister();
        }
        if (mythicDropBridge != null) {
            HandlerList.unregisterAll(mythicDropBridge);
            mythicDropBridge = null;
        }
        if (gameplaySubscriber != null) {
            gameplaySubscriber.unsubscribe();
            gameplaySubscriber = null;
        }
        EmakiLevelApi.uninstall(levelApiBridge);
        if (dataStore != null) {
            PlayerLevelDataStore.FlushResult flushResult = dataStore.flushAndSeal(5L, TimeUnit.SECONDS);
            if (!flushResult.clean()) {
                getLogger().warning("[Shutdown] Level data drain incomplete: pending="
                        + flushResult.drainResult().pendingOperations()
                        + ", ioFailures=" + flushResult.drainResult().failures().size()
                        + ", saveFailures=" + flushResult.failedEntries()
                        + ", remainingDirty=" + flushResult.remainingDirtyEntries());
            }
        }
        if (coreLib != null && coreLib.actionRegistry() != null) {
            coreLib.actionRegistry().unregisterAll(this);
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (messages != null) {
            messages.info("console.plugin_stopped");
        }
    }

    public void reloadPluginState() {
        appConfig = AppConfig.parse(YamlFiles.load(getDataFolder().toPath().resolve("config.yml").toFile()));
        messages.load(appConfig.language());
        debugLanguageLoader.load();
        debugLanguageLoader.setLanguage(appConfig.language());
        typeLoader.load(appConfig);
        requirementLoader.load();
        sourceRuleLoader.load();
        guiTemplateLoader.load();
        typeRegistry.reload(typeLoader.types());
        requirementService.reload(requirementLoader.config());
        pdcService.enabled(appConfig.pdcEnabled());
        antiAbuseService.config(appConfig);
        experienceRuleService.config(appConfig);
        experienceRuleService.clearExpired();
        levelService.config(appConfig);
        if (attributeBridge != null) {
            attributeBridge.config(appConfig);
        }
        dataStore.ensureTypesForCached(typeRegistry.asMap());
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            playerDataListener.ensureSession(player);
        }
        topService.rebuildAsync().exceptionally(throwable -> {
            getLogger().log(java.util.logging.Level.WARNING, "Failed to rebuild level leaderboard", throwable);
            return null;
        });
        levelService.syncAllOnline();
        messages.info("console.types_loaded", Map.of("count", String.valueOf(typeRegistry.all().size())));
        messages.info("console.sources_loaded", Map.of("count", String.valueOf(sourceRuleLoader.rules().size())));
        logConfigPrecheckReport();
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messages, "level");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new LevelConfigPrecheckContributor(this));
    }

    private void initializeServices() {
        messages = new LevelMessageService(this);
        messages.load(appConfig.language());
        debugLanguageLoader = new LanguageLoader(this);
        debugLogger = new DebugLogger(this, debugLanguageLoader);
        debugMessageService = new AbstractMessageService(this, messages.message("general.prefix"),
                messages::message, messages::message);
        debugCommand = new DebugCommand(debugLogger, DEBUG_MODULES);
        bootstrapService = new BootstrapService(
                this,
                messages,
                VERSIONED_FILES,
                List.of(),
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig runtimeConfig = AppConfig.parse(YamlFiles.load(getDataFolder().toPath().resolve("config.yml").toFile()));
                        return runtimeConfig.releaseDefaultData();
                    }

                    @Override
                    public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                        if (relativePath == null || !relativePath.startsWith("lang/") || runtime == null || bundled == null) {
                            return;
                        }
                        YamlSection consoleDefaults = bundled.getSection("console");
                        if (consoleDefaults != null) {
                            runtime.set("console", consoleDefaults.asMap());
                        }
                    }
                }
        );
        typeLoader = new LevelTypeLoader(this);
        requirementLoader = new RequirementLoader(this);
        sourceRuleLoader = new SourceRuleLoader(this);
        guiTemplateLoader = new GuiTemplateLoader(this);
        guiService = new GuiService(this, executionDispatcher, coreLib.asyncTaskScheduler(), coreLib.performanceMonitor(), coreLib.guiBackend());
        typeRegistry = new LevelTypeRegistry();
        requirementService = new RequirementService();
        var playerDataFiles = coreLib.asyncYamlFiles(this);
        dataStore = new PlayerLevelDataStore(this, () -> playerDataFiles);
        pdcService = new LevelPdcService(appConfig.pdcNamespace(), appConfig.pdcEnabled());
        javaScriptExpRuleRegistry = new JavaScriptLevelExpRuleRegistry(this);
        javaScriptLevelUpHookRegistry = new JavaScriptLevelUpHookRegistry(this);
        experienceRuleService = new LevelExperienceRuleService();
        experienceRuleService.config(appConfig);
        experienceRuleService.javaScriptRules(javaScriptExpRuleRegistry);
        antiAbuseService = new LevelAntiAbuseService(appConfig);
        attributeBridge = new LevelAttributeBridge(this, typeRegistry, dataStore, appConfig);
        topService = new LevelTopService(dataStore, typeRegistry);
        levelService = new PlayerLevelService(
                this,
                typeRegistry,
                requirementService,
                dataStore,
                pdcService,
                experienceRuleService,
                javaScriptLevelUpHookRegistry,
                coreLib.itemSourceService(),
                coreLib.economyManager(),
                coreLib.actionExecutor(),
                executionDispatcher,
                threadOwnership,
                appConfig,
                () -> attributeBridge.resyncAll(),
                player -> attributeBridge.resync(player),
                data -> topService.update(data)
        );
        playerDataListener = new PlayerDataListener(this, executionDispatcher);
        levelGuiService = new LevelGuiService(this, guiService, guiTemplateLoader);
        levelTopGuiService = new LevelTopGuiService(this, guiService, guiTemplateLoader);
    }

    private void registerCommand() {
        LevelCommand command = new LevelCommand(this);
        registerCommand(
                "emakilevel",
                "emakilevel command",
                java.util.List.of("elv", "elevel"),
                new PaperCommandAdapter("emakilevel", "emakilevel.use", command, command)
        );
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        gameplaySubscriber = new LevelGameplaySubscriber(this);
        gameplaySubscriber.subscribe(coreLib.eventBus());
    }

    private void registerApi() {
        EmakiLevelApi.install(levelApiBridge);
    }

    private void registerScriptModule() {
        coreLib.scriptModuleRegistry().register("level", context -> new ScriptLevelModuleApi(this, context));
    }

    private void releaseBundledScripts() {
        coreLib.releaseBundledScripts(this, "examples", false, List.of("level_status.js", "level_exp_rule.js"));
    }

    private void registerActions() {
        new LevelActionRegistrar(this).register(coreLib.actionRegistry());
        messages.info("console.actions_registered");
    }

    private void registerCorePlaceholders() {
        if (coreLib == null || coreLib.placeholderRegistry() == null) {
            return;
        }
        if (corePlaceholderResolver != null) {
            coreLib.placeholderRegistry().unregister(corePlaceholderResolver);
        }
        corePlaceholderResolver = new LevelCorePlaceholderResolver(this);
        coreLib.placeholderRegistry().register(corePlaceholderResolver);
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new LevelPlaceholderExpansion(this);
        placeholderExpansion.register();
        messages.info("console.papi_registered");
    }

    private void registerAttributeBridge() {
        if (attributeBridge.register()) {
            messages.info("console.attribute_bridge_ready");
        } else if (appConfig.attributeEnabled()) {
            messages.info("console.attribute_bridge_unavailable");
        }
    }

    private void registerMythicDrops() {
        if (!appConfig.mythicEnabled() || !appConfig.mythicDropsEnabled() || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicDropBridge = new MythicLevelDropBridge(this);
        getServer().getPluginManager().registerEvents(mythicDropBridge, this);
        messages.info("console.mythic_drops_registered");
    }

    private PlayerLevelEntry entry(UUID uuid, String typeId) {
        if (uuid == null) {
            return null;
        }
        PlayerLevelData data = dataStore.cached(uuid);
        return data == null ? null : data.entry(emaki.jiuwu.craft.corelib.text.Texts.normalizeId(typeId));
    }

    private LevelTypeView view(LevelTypeConfig type) {
        return new LevelTypeView(type.id(), type.displayName(), type.description(), type.primary(), type.enabled(), type.startLevel(), type.maxLevel(), type.upgrade().autoUpgrade(), type.upgrade().manualUpgrade(), type.attributes());
    }

    private PlayerLevelView playerView(PlayerLevelData data) {
        Map<String, PlayerLevelEntryView> entries = new LinkedHashMap<>();
        for (LevelTypeConfig type : typeRegistry.all()) {
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry == null) {
                continue;
            }
            double required = requirementService.requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
            double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
            entries.put(type.id(), new PlayerLevelEntryView(type.id(), entry.level(), entry.exp(), entry.totalExp(), required, progress));
        }
        return new PlayerLevelView(data.uuid(), data.name(), entries);
    }

    public AppConfig appConfig() {
        return appConfig;
    }

    public LevelMessageService messages() {
        return messages;
    }

    @Override
    public DebugLogger debugLogger() {
        return debugLogger;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public AbstractMessageService debugMessageService() {
        return debugMessageService;
    }

    public LevelTypeRegistry typeRegistry() {
        return typeRegistry;
    }

    public RequirementService requirementService() {
        return requirementService;
    }

    public SourceRuleLoader sourceRuleLoader() {
        return sourceRuleLoader;
    }

    public PlayerLevelDataStore dataStore() {
        return dataStore;
    }

    public PlayerLevelService levelService() {
        return levelService;
    }

    public JavaScriptLevelExpRuleRegistry javaScriptExpRuleRegistry() {
        return javaScriptExpRuleRegistry;
    }

    public JavaScriptLevelUpHookRegistry javaScriptLevelUpHookRegistry() {
        return javaScriptLevelUpHookRegistry;
    }

    public EmakiCoreLibPlugin coreLib() {
        return coreLib;
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public LevelTopService topService() {
        return topService;
    }

    public LevelAntiAbuseService antiAbuseService() {
        return antiAbuseService;
    }

    public LevelGuiService levelGuiService() {
        return levelGuiService;
    }

    public LevelTopGuiService levelTopGuiService() {
        return levelTopGuiService;
    }

    private static final class PaperCommandAdapter implements BasicCommand {

        private final String rootLabel;
        private final String permission;
        private final org.bukkit.command.CommandExecutor executor;
        private final org.bukkit.command.TabCompleter tabCompleter;

        private PaperCommandAdapter(String rootLabel,
                String permission,
                org.bukkit.command.CommandExecutor executor,
                org.bukkit.command.TabCompleter tabCompleter) {
            this.rootLabel = rootLabel;
            this.permission = permission;
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            executor.onCommand(source.getSender(), null, rootLabel, args);
        }

        @Override
        public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
            String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
            java.util.List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? java.util.List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

package emaki.jiuwu.craft.level;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.level.action.LevelStageRegistrar;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.apiimpl.DefaultEmakiLevelApi;
import emaki.jiuwu.craft.level.bridge.MythicLevelDropBridge;
import emaki.jiuwu.craft.level.command.LevelCommand;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelConfigPrecheckContributor;
import emaki.jiuwu.craft.level.listener.LevelGameplaySubscriber;
import emaki.jiuwu.craft.level.listener.PlayerDataListener;
import emaki.jiuwu.craft.level.loader.LevelTypeLoader;
import emaki.jiuwu.craft.level.loader.RequirementLoader;
import emaki.jiuwu.craft.level.loader.SourceRuleLoader;
import emaki.jiuwu.craft.level.papi.LevelPlaceholderExpansion;
import emaki.jiuwu.craft.level.placeholder.LevelCorePlaceholderResolver;
import emaki.jiuwu.craft.level.service.ExpSourceProviderRegistry;
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

public final class EmakiLevelPlugin extends JavaPlugin implements DebugLoggerProvider {

    private static final int BSTATS_PLUGIN_ID = 31794;
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __      ______  __   ________  __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\    /\\  ___\\/\\ \\ / /\\  ___\\/\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\  __\\\\ \\ \\'/\\ \\  __\\\\ \\ \\____
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\__| \\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/   \\/_____/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xA855F7;
    private static final int STARTUP_ASCII_END_COLOR = 0xF472B6;
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
    private static final Set<String> DEBUG_MODULES = Set.of("common");

    private EmakiCoreLibPlugin coreLib;
    private EmakiScheduling scheduling;
    private AppConfig appConfig = AppConfig.defaults();
    private LevelMessageService messages;
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
    private LevelAntiAbuseService antiAbuseService;
    private PlayerLevelService levelService;
    private LevelTopService topService;
    private LevelGuiService levelGuiService;
    private LevelTopGuiService levelTopGuiService;
    private MythicLevelDropBridge mythicDropBridge;
    private LevelGameplaySubscriber gameplaySubscriber;
    private PlayerDataListener playerDataListener;
    private LevelCorePlaceholderResolver corePlaceholderResolver;
    private LevelPlaceholderExpansion placeholderExpansion;
    private LevelStageRegistrar stageRegistrar;
    private BStatsRegistration metrics;

    private volatile boolean contentReady;
    private EmakiLevelApi.Bridge levelApiBridge;
    private ExpSourceProviderRegistry expSourceRegistry;
    private Runnable attributeBridgeClose = () -> {
    };
    private Runnable attributeRefreshAll = () -> {
    };
    private Consumer<Player> attributeRefreshPlayer = player -> {
    };

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        scheduling = EmakiCoreLibApi.scheduling();
        initializeServices();
        registerConfigPrecheckContributor();
        messages.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        registerCommand();
        registerListeners();
        registerApi();
        registerActions();
        registerCorePlaceholders();
        registerPlaceholderExpansion();
        registerMythicDrops();
        metrics = coreLib.registerBStats(this, BSTATS_PLUGIN_ID);
        messages.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        contentReady = false;
        publishAbsent();
        if (coreLib != null) {
            ConfigPrecheckLifecycleSupport.unregister("level");
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (corePlaceholderResolver != null && coreLib != null && coreLib.placeholderRegistry() != null) {
            coreLib.placeholderRegistry().unregister(corePlaceholderResolver);
            corePlaceholderResolver = null;
        }
        closeAttributeBridge();
        if (expSourceRegistry != null) {
            expSourceRegistry.close();
            expSourceRegistry = null;
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
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
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

        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                messages,
                "level",
                () -> appConfig,
                () -> appConfig = AppConfig.parse(YamlFiles.load(getDataFolder().toPath().resolve("config.yml").toFile())),
                restored -> appConfig = restored);
        if (gate.rejected()) {
            return;
        }

        contentReady = false;
        publishLoading();
        closeAttributeBridge();
        messages.load(appConfig.language());
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
        dataStore.ensureTypesForCached(typeRegistry.asMap());
        registerAttributeBridge();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataListener.ensureSession(player);
        }
        topService.rebuildAsync().exceptionally(throwable -> {
            getLogger().log(Level.WARNING, "Failed to rebuild level leaderboard", throwable);
            return null;
        });
        levelService.syncAllOnline();
        messages.info("console.types_loaded", Map.of("count", String.valueOf(typeRegistry.all().size())));
        messages.info("console.sources_loaded", Map.of("count", String.valueOf(sourceRuleLoader.rules().size())));
        contentReady = true;
        publishReady();
    }

    public boolean contentReady() {
        return contentReady;
    }

    private void publishReady() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleReady(getName()));
    }

    private void publishLoading() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleLoading(getName()));
    }

    private void publishAbsent() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleAbsent(getName()));
    }

    private void publishReadiness(Consumer<EmakiCoreLibPlugin> action) {
        try {
            action.accept(coreLib());
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiLevel readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new LevelConfigPrecheckContributor(this));
    }

    private void initializeServices() {
        messages = new LevelMessageService(this);
        messages.load(appConfig.language());
        debugLogger = new DebugLogger(this, coreLib.languageLoader());
        debugMessageService = new AbstractMessageService(this, messages.message("general.prefix"),
                messages::message, messages::message);
        debugCommand = new DebugCommand(debugLogger, DEBUG_MODULES, getName());
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
        var executionDispatcher = coreLib.executionDispatcher();
        guiService = new GuiService(this, executionDispatcher, coreLib.asyncTaskScheduler(), coreLib.performanceMonitor(), coreLib.guiBackend());
        typeRegistry = new LevelTypeRegistry();
        requirementService = new RequirementService();
        var playerDataFiles = coreLib.asyncYamlFiles(this);
        dataStore = new PlayerLevelDataStore(this, () -> playerDataFiles);
        pdcService = new LevelPdcService(appConfig.pdcNamespace(), appConfig.pdcEnabled());
        experienceRuleService = new LevelExperienceRuleService();
        experienceRuleService.config(appConfig);
        antiAbuseService = new LevelAntiAbuseService(appConfig);
        expSourceRegistry = new ExpSourceProviderRegistry(this);
        topService = new LevelTopService(dataStore, typeRegistry);
        levelService = new PlayerLevelService(
                this,
                typeRegistry,
                requirementService,
                dataStore,
                pdcService,
                experienceRuleService,
                coreLib.itemSourceService(),
                coreLib.economyManager(),
                actionLines(),
                scheduling,
                appConfig,
                this::resyncAllAttributes,
                this::resyncAttributes,
                data -> topService.update(data)
        );
        playerDataListener = new PlayerDataListener(this, scheduling);
        levelGuiService = new LevelGuiService(this, guiService, guiTemplateLoader);
        levelTopGuiService = new LevelTopGuiService(this, guiService, guiTemplateLoader);
    }

    private void registerCommand() {
        LevelCommand command = new LevelCommand(this);
        registerCommand(
                "emakilevel",
                "emakilevel command",
                List.of("elv", "elevel"),
                new PaperCommandAdapter("emakilevel", "emakilevel.use", command, command)
        );
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        getServer().getPluginManager().registerEvents(expSourceRegistry, this);
        gameplaySubscriber = new LevelGameplaySubscriber(this);
        gameplaySubscriber.subscribe(coreLib.eventBus());
    }

    private void registerApi() {
        levelApiBridge = new DefaultEmakiLevelApi(this);
        EmakiLevelApi.install(levelApiBridge);
    }

    private void registerActions() {
        stageRegistrar = new LevelStageRegistrar(this);
        stageRegistrar.register();
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
        closeAttributeBridge();
        if (!appConfig.attributeEnabled()) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("EmakiAttribute")) {
            messages.info("console.attribute_bridge_unavailable");
            return;
        }
        try {
            LevelAttributeBridge bridge = new LevelAttributeBridge(
                    this,
                    typeRegistry,
                    dataStore,
                    scheduling,
                    appConfig);
            if (!bridge.register()) {
                bridge.close();
                getLogger().warning("EmakiAttribute bridge registration failed: provider=EmakiAttribute,"
                        + " operation=register_attribute_bridge, cause=bridge.register() returned false");
                messages.info("console.attribute_bridge_unavailable");
                return;
            }
            attributeBridgeClose = bridge::close;
            attributeRefreshAll = bridge::resyncAll;
            attributeRefreshPlayer = bridge::resync;
            messages.info("console.attribute_bridge_ready");
        } catch (RuntimeException | LinkageError exception) {
            getLogger().log(Level.WARNING,
                    "EmakiAttribute bridge registration failed: provider=EmakiAttribute,"
                            + " operation=register_attribute_bridge, cause=" + exception,
                    exception);
            messages.info("console.attribute_bridge_unavailable");
        }
    }

    private void closeAttributeBridge() {
        attributeBridgeClose.run();
        attributeBridgeClose = () -> {
        };
        attributeRefreshAll = () -> {
        };
        attributeRefreshPlayer = player -> {
        };
    }

    private void resyncAllAttributes() {
        attributeRefreshAll.run();
    }

    private void resyncAttributes(Player player) {
        attributeRefreshPlayer.accept(player);
    }

    private void registerMythicDrops() {
        if (!appConfig.mythicEnabled() || !appConfig.mythicDropsEnabled() || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicDropBridge = new MythicLevelDropBridge(this);
        getServer().getPluginManager().registerEvents(mythicDropBridge, this);
        messages.info("console.mythic_drops_registered");
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

    public LevelExperienceRuleService experienceRuleService() {
        return experienceRuleService;
    }

    public ExpSourceProviderRegistry expSourceRegistry() {
        return expSourceRegistry;
    }

    public EmakiCoreLibPlugin coreLib() {
        return coreLib;
    }

    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public EmakiScheduling scheduling() {
        return scheduling;
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
        private final CommandExecutor executor;
        private final TabCompleter tabCompleter;

        private PaperCommandAdapter(String rootLabel,
                String permission,
                CommandExecutor executor,
                TabCompleter tabCompleter) {
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
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
            List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

package emaki.jiuwu.craft.station;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.station.command.StationCommandRouter;
import emaki.jiuwu.craft.station.config.AppConfig;
import emaki.jiuwu.craft.station.config.StationConfigPrecheckContributor;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.dismantle.DismantleStationRegistry;
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.listener.StationPlayerListener;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.api.EmakiStationApi;
import emaki.jiuwu.craft.station.apiimpl.DefaultStationBridge;
import emaki.jiuwu.craft.station.config.QueueCostConfig;
import emaki.jiuwu.craft.station.config.QueueCostLoader;
import emaki.jiuwu.craft.station.definition.StationLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleService;
import emaki.jiuwu.craft.station.dismantle.DismantleStationLoader;
import emaki.jiuwu.craft.station.material.StationCapabilities;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.QueueUnlockService;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

public final class EmakiStationPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider {

    private static final String MODULE = "station";
    private static final String ROOT_COMMAND = "emakistation";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __  ______  __   __    \s
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  __ \\/\\__  _\\/\\ \\/\\  __ \\/\\ "-.\\  \\ \s
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\  __ \\/_/\\ \\/\\ \\ \\ \\ \\/\\ \\ \\ \\-.  \\ \s
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\ \\_\\ \\ \\_\\ \\_____\\ \\_\\\\"\\._\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/\\/_/  \\/_/  \\/_/\\/_____/\\/_/ \\/_/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xA3E635;
    private static final int STARTUP_ASCII_END_COLOR = 0x22D3EE;
    private static final int BSTATS_PLUGIN_ID = 33430;

    private final StationLifecycleCoordinator lifecycleCoordinator = new StationLifecycleCoordinator();
    private final AtomicReference<StationRegistry> registry =
            new AtomicReference<>(StationRegistry.empty());
    private final AtomicReference<DismantleStationRegistry> dismantleRegistry =
            new AtomicReference<>(DismantleStationRegistry.empty());
    private final AtomicReference<QueueCostConfig> queueCosts =
            new AtomicReference<>(QueueCostConfig.empty());
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean apiInstalled = new AtomicBoolean();

    private StationRuntimeComponents components;
    private StationCommandRouter commandRouter;
    private StationPlayerListener playerListener;
    private DebugCommand debugCommand;
    private TaskToken autoSaveTask;
    private boolean runtimeInitialized;

    private volatile boolean contentReady;
    private BStatsRegistration metrics;

    public EmakiStationPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return components == null ? null : components.appConfigLoader();
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII, STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        shutdownStarted.set(false);
        components = lifecycleCoordinator.initialize(this);
        runtimeInitialized = true;
        setDebugLogger(new DebugLogger(this, components.languageLoader()));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
        ConfigPrecheckLifecycleSupport.register(new StationConfigPrecheckContributor(this));
        components.messageService().info("console.plugin_starting");
        components.bootstrapService().bootstrap();
        reloadContent();
        registerCommandHandler();
        registerEventHandlers();
        installPublicApi();
        components.queueTicker().start(appConfig().queueSettings().tickIntervalTicks());
        scheduleAutoSave();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        contentReady = false;
        publishAbsent();
        if (!runtimeInitialized || components == null) {
            return;
        }

        CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        coreLib().registerDependentShutdown(MODULE, shutdownFuture);
        uninstallPublicApi();
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        ConfigPrecheckLifecycleSupport.unregister(MODULE);
        HandlerList.unregisterAll(this);
        components.queueTicker().stop();

        components.stationGuiService().closeAll();
        components.guiService().closeAll();

        components.unlockService().saveAllAsync().whenComplete((ignoredUnlocks, unlockFailure) -> {
            if (unlockFailure != null) {
                getLogger().warning("Queue unlock flush did not finish cleanly: "
                        + unlockFailure.getMessage());
            }
            components.queueService().flushAllAsync().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    getLogger().warning("Queue flush did not finish cleanly: " + failure.getMessage());
                }
                registry.set(StationRegistry.empty());
                dismantleRegistry.set(DismantleStationRegistry.empty());
                runtimeInitialized = false;
                components.messageService().info("console.plugin_stopped");
                shutdownFuture.complete(null);
            });
        });
    }

    public ReloadSummary reloadContent() {
        contentReady = false;
        publishLoading();
        ReloadSummary summary = executeReload();
        contentReady = true;
        publishReady();
        return summary;
    }

    private ReloadSummary executeReload() {
        components.appConfigLoader().load();
        components.languageLoader().load();
        components.languageLoader().setLanguage(appConfig().language());
        components.layoutLoader().load();
        components.stationLoader().load();
        components.recipeLoader().load();
        components.dismantleStationLoader().load();
        components.dismantleRecipeLoader().load();
        components.dismantleService().reload(
                List.copyOf(components.dismantleRecipeLoader().all().values()));
        dismantleRegistry.set(DismantleStationRegistry.build(
                components.dismantleStationLoader().all().values()));

        queueCosts.set(QueueCostLoader.load(
                dataPath(appConfig().purchaseSettings().costFile()).toFile(), getLogger(), true));
        StationRegistry resolved = StationRegistry.resolve(components.stationLoader().all(),
                components.recipeLoader().all());
        registry.set(resolved);
        ConfigCommitGate.evaluate(components.messageService(), MODULE);
        return new ReloadSummary(resolved.stationCount(), resolved.recipeCount(),
                components.stationLoader().issues().size() + components.recipeLoader().issues().size());
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
            action.accept(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class));
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiStation readiness publication skipped: " + exception);
        }
    }

    public record ReloadSummary(int stations, int recipes, int issues) {
    }

    public StationRegistry registry() {
        return registry.get();
    }

    public DismantleStationRegistry dismantleRegistry() {
        return dismantleRegistry.get();
    }

    public QueueCostConfig queueCosts() {
        return queueCosts.get();
    }

    public QueueUnlockService queueUnlockService() {
        return components == null ? null : components.unlockService();
    }

    public MessageService messageService() {
        return components == null ? null : components.messageService();
    }

    public LanguageLoader languageLoader() {
        return components == null ? null : components.languageLoader();
    }

    public BootstrapService bootstrapService() {
        return components == null ? null : components.bootstrapService();
    }

    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    public GuiService guiService() {
        return components == null ? null : components.guiService();
    }

    public GuiTemplateLoader layoutLoader() {
        return components == null ? null : components.layoutLoader();
    }

    public StationGuiService stationGuiService() {
        return components == null ? null : components.stationGuiService();
    }

    public QueueService queueService() {
        return components == null ? null : components.queueService();
    }

    public StationCraftService craftService() {
        return components == null ? null : components.craftService();
    }

    public StationLoader stationLoader() {
        return components == null ? null : components.stationLoader();
    }

    public RecipeLoader recipeLoader() {
        return components == null ? null : components.recipeLoader();
    }

    public DismantleStationLoader dismantleStationLoader() {
        return components == null ? null : components.dismantleStationLoader();
    }

    public DismantleRecipeLoader dismantleRecipeLoader() {
        return components == null ? null : components.dismantleRecipeLoader();
    }

    public DismantleService dismantleService() {
        return components == null ? null : components.dismantleService();
    }

    public StorageChannel storageChannel() {
        return components == null ? null : components.storageChannel();
    }

    public StationCapabilities capabilities() {
        return components == null
                ? StationCapabilities.none()
                : components.capabilities();
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    private void registerCommandHandler() {
        commandRouter = new StationCommandRouter(this);
        registerCommand(ROOT_COMMAND, "EmakiStation command", List.of("estation", "est"),
                new StationCommandAdapter(ROOT_COMMAND, "emakistation.use", commandRouter));
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(components.guiService(), this);
        playerListener = new StationPlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }

    private void scheduleAutoSave() {
        long intervalTicks = Math.max(100L,
                appConfig().persistenceSettings().autosaveIntervalSeconds() * 20L);
        autoSaveTask = components.executionDispatcher().runGlobalTimer(this, () -> {
            components.queueService().saveDirtyAsync();
            components.unlockService().saveAllAsync();
        }, intervalTicks, intervalTicks);
    }

    private void installPublicApi() {
        if (apiInstalled.compareAndSet(false, true)) {
            EmakiStationApi.install(
                    new DefaultStationBridge(this));
        }
    }

    private void uninstallPublicApi() {
        if (apiInstalled.compareAndSet(true, false)) {
            DefaultStationBridge.uninstallActive();
        }
    }
}

package emaki.jiuwu.craft.station;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.station.command.StationCommandRouter;
import emaki.jiuwu.craft.station.config.AppConfig;
import emaki.jiuwu.craft.station.config.StationConfigPrecheckContributor;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.listener.StationPlayerListener;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.StationCraftService;

/**
 * EmakiStation's entry point.
 *
 * <p>Orchestration only: the component graph is built by {@link StationLifecycleCoordinator} and the domain
 * logic lives in the services it produces.
 *
 * <p>Disable is the exact reverse of enable. Open windows are closed first so input items are returned before
 * anything they depend on is torn down, and queues are flushed before the file lane is released.
 */
public final class EmakiStationPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider {

    private static final String MODULE = "station";
    private static final String ROOT_COMMAND = "emakistation";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);

    private final StationLifecycleCoordinator lifecycleCoordinator = new StationLifecycleCoordinator();
    private final AtomicReference<StationRegistry> registry =
            new AtomicReference<>(StationRegistry.empty());
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean apiInstalled = new AtomicBoolean();

    private StationRuntimeComponents components;
    private StationCommandRouter commandRouter;
    private StationPlayerListener playerListener;
    private DebugCommand debugCommand;
    private TaskHandle autoSaveTask;
    private boolean runtimeInitialized;

    /** Creates the plugin with its shipped configuration defaults. */
    public EmakiStationPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return components == null ? null : components.appConfigLoader();
    }

    @Override
    public void onEnable() {
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
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        if (!runtimeInitialized || components == null) {
            return;
        }
        // Registering a barrier keeps CoreLib's own shutdown waiting for the queue flush instead of blocking
        // this thread on it. Blocking here would stall the server's disable sequence, and the flush needs
        // CoreLib's async file lane to still be alive.
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
        // Input slots hold player property, so windows close before anything else is released.
        components.stationGuiService().closeAll();
        components.guiService().closeAll();
        components.queueService().flushAllAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                getLogger().warning("Queue flush did not finish cleanly: " + failure.getMessage());
            }
            registry.set(StationRegistry.empty());
            runtimeInitialized = false;
            components.messageService().info("console.plugin_stopped");
            shutdownFuture.complete(null);
        });
    }

    /**
     * Reloads configuration, layouts, stations, and recipes.
     *
     * <p>The resolved registry is only swapped in once every loader succeeded, so a broken edit leaves the
     * previous working set active rather than replacing it with a partial one.
     *
     * @return how many stations and recipes are active after the reload
     */
    public ReloadSummary reloadContent() {
        components.appConfigLoader().load();
        components.languageLoader().load();
        components.languageLoader().setLanguage(appConfig().language());
        components.layoutLoader().load();
        components.stationLoader().load();
        components.recipeLoader().load();
        StationRegistry resolved = StationRegistry.resolve(components.stationLoader().all(),
                components.recipeLoader().all());
        registry.set(resolved);
        return new ReloadSummary(resolved.stationCount(), resolved.recipeCount(),
                components.stationLoader().issues().size() + components.recipeLoader().issues().size());
    }

    /**
     * How much content a reload produced.
     *
     * @param stations how many stations are active
     * @param recipes  how many recipes are active
     * @param issues   how many loader problems were recorded
     */
    public record ReloadSummary(int stations, int recipes, int issues) {
    }

    /** {@return the currently active resolved registry} */
    public StationRegistry registry() {
        return registry.get();
    }

    /** {@return the message service, or {@code null} before enable completes} */
    public MessageService messageService() {
        return components == null ? null : components.messageService();
    }

    /** {@return the language loader, or {@code null} before enable completes} */
    public LanguageLoader languageLoader() {
        return components == null ? null : components.languageLoader();
    }

    /** {@return the bootstrap service, or {@code null} before enable completes} */
    public BootstrapService bootstrapService() {
        return components == null ? null : components.bootstrapService();
    }

    /** {@return CoreLib's execution dispatcher, or {@code null} before enable completes} */
    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    /** {@return CoreLib's GUI service, or {@code null} before enable completes} */
    public GuiService guiService() {
        return components == null ? null : components.guiService();
    }

    /** {@return the layout loader, or {@code null} before enable completes} */
    public GuiTemplateLoader layoutLoader() {
        return components == null ? null : components.layoutLoader();
    }

    /** {@return the station window manager, or {@code null} before enable completes} */
    public StationGuiService stationGuiService() {
        return components == null ? null : components.stationGuiService();
    }

    /** {@return the queue cache, or {@code null} before enable completes} */
    public QueueService queueService() {
        return components == null ? null : components.queueService();
    }

    /** {@return the submission orchestrator, or {@code null} before enable completes} */
    public StationCraftService craftService() {
        return components == null ? null : components.craftService();
    }

    /** {@return the station loader, or {@code null} before enable completes} */
    public emaki.jiuwu.craft.station.definition.StationLoader stationLoader() {
        return components == null ? null : components.stationLoader();
    }

    /** {@return the recipe loader, or {@code null} before enable completes} */
    public emaki.jiuwu.craft.station.recipe.RecipeLoader recipeLoader() {
        return components == null ? null : components.recipeLoader();
    }

    /** {@return the warehouse channel, or {@code null} before enable completes} */
    public emaki.jiuwu.craft.station.material.StorageChannel storageChannel() {
        return components == null ? null : components.storageChannel();
    }

    /** {@return the capability probe result, or an empty set before enable completes} */
    public emaki.jiuwu.craft.station.material.StationCapabilities capabilities() {
        return components == null
                ? emaki.jiuwu.craft.station.material.StationCapabilities.none()
                : components.capabilities();
    }

    /** {@return the structured debug command handler, or {@code null} before enable completes} */
    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /**
     * {@return a freshly resolved action-line runner}
     *
     * <p>Resolved per call rather than cached so a CoreLib reload does not leave a stale engine behind.
     */
    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    /** {@return the CoreLib plugin instance} */
    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    /** {@return whether the disable path has begun} */
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
        autoSaveTask = components.executionDispatcher().runGlobalTimer(this,
                () -> components.queueService().saveDirtyAsync(), intervalTicks, intervalTicks);
    }

    private void installPublicApi() {
        if (apiInstalled.compareAndSet(false, true)) {
            emaki.jiuwu.craft.station.api.EmakiStationApi.install(
                    new emaki.jiuwu.craft.station.apiimpl.DefaultStationBridge(this));
        }
    }

    private void uninstallPublicApi() {
        if (apiInstalled.compareAndSet(true, false)) {
            emaki.jiuwu.craft.station.apiimpl.DefaultStationBridge.uninstallActive();
        }
    }
}

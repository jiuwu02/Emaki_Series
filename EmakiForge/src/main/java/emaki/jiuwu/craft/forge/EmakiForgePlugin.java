package emaki.jiuwu.craft.forge;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.action.ForgeActionRegistrar;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.config.ForgeConfigPrecheckContributor;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.papi.ForgePlaceholderExpansion;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

public class EmakiForgePlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakiforge";

    private static final String STARTUP_ASCII = """
             ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______
            /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\  ___\\
            \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __\\\\ \\ \\/\\ \\ \\  __<\\ \\ \\__ \\ \\  __\\
             \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\   \\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\
              \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/    \\/_____/\\/_/ /_/\\/_____/\\/_____/
            """;
    private static final int STARTUP_ASCII_START_COLOR = 0xF59E0B;
    private static final int STARTUP_ASCII_END_COLOR = 0xEF4444;
    private static final int BSTATS_PLUGIN_ID = 31766;

    private BStatsRegistration metrics;
    private volatile boolean runtimeInitialized;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicReference<ForgeRuntimeSnapshot> runtimeSnapshot = new AtomicReference<>(
            new ForgeRuntimeSnapshot(0L, ForgeRuntimeStatus.STARTING, null, null, null, null, null,
                    null, null, null, null, System.nanoTime()));
    private final ForgeRuntimeMetrics runtimeMetrics = new ForgeRuntimeMetrics();

    private final ForgeLifecycleCoordinator lifecycleCoordinator = new ForgeLifecycleCoordinator();
    private ForgeCommandRouter commandRouter;
    private final ForgePlayerDataListener playerDataListener = new ForgePlayerDataListener(this);
    private ForgeItemRefreshListener itemRefreshListener;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private LanguageLoader languageLoader;
    private RecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private PlayerDataStore playerDataStore;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemIdentifierService itemIdentifierService;
    private ForgeAttributeBridge pdcAttributeGateway;
    private ForgeItemRefreshService itemRefreshService;
    private ForgeService forgeService;
    private ForgeGuiService forgeGuiService;
    private RecipeBookGuiService recipeBookGuiService;
    private ForgePlaceholderExpansion placeholderExpansion;
    private TaskHandle autoSaveTask;
    private DebugCommand debugCommand;
    private final EmakiForgeApi.Bridge forgeApiBridge = new EmakiForgeApi.Bridge() {
        @Override
        public String apiVersion() {
            return getDescription().getVersion();
        }

        @Override
        public String pluginName() {
            return getName();
        }

        @Override
        public boolean isReady() {
            return isEnabled() && isRuntimeReady();
        }
    };

    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "forge", "gui", "pdc");

    public EmakiForgePlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        runtimeInitialized = true;
        registerConfigPrecheckContributor();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerCommandHandler();
        registerActions();
        registerEventHandlers();
        registerPublicApiService();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        EmakiCoreLibPlugin coreLibPlugin = resolveCoreLibForShutdown();
        registerCoreShutdownBarrier(coreLibPlugin, shutdownFuture);
        if (!runtimeInitialized) {
            try {
                transitionRuntime(ForgeRuntimeStatus.CLOSING, "plugin disabling before runtime initialization");
                transitionRuntime(ForgeRuntimeStatus.CLOSED, "plugin disabled before runtime initialization");
                shutdownFuture.complete(null);
            } catch (Throwable terminalFailure) {
                getLogger().warning("[Shutdown] Early runtime close failed: "
                        + String.valueOf(terminalFailure.getMessage()));
                shutdownFuture.completeExceptionally(terminalFailure);
            }
            return;
        }
        CompletableFuture<Void> shutdownPipeline;
        try {
            transitionRuntime(ForgeRuntimeStatus.CLOSING, "plugin disabling");
            autoSaveTask = lifecycleCoordinator.cancelAutoSave(autoSaveTask);
            ConfigPrecheckLifecycleSupport.unregister("forge");
            HandlerList.unregisterAll(this);
            if (placeholderExpansion != null) {
                placeholderExpansion.unregister();
                placeholderExpansion = null;
            }
            EmakiForgeApi.uninstall(forgeApiBridge);
            shutdownPipeline = lifecycleCoordinator
                    .quiesceAndCloseForShutdown(this, coreLibPlugin)
                    .thenCompose(ignored -> {
                        clearRuntimeRegistrationsForShutdown(coreLibPlugin);
                        return lifecycleCoordinator.shutdownAsync(this, coreLibPlugin);
                    });
        } catch (Throwable startupFailure) {
            shutdownPipeline = CompletableFuture.failedFuture(startupFailure);
        }
        shutdownPipeline.whenComplete((ignored, throwable) -> {
            Throwable terminalFailure = throwable;
            if (throwable != null) {
                Throwable cause = AsyncFailures.unwrapOnce(throwable);
                getLogger().warning("[Shutdown] Forge cleanup failed: "
                        + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            }
            if (metrics != null) {
                try {
                    metrics.close();
                } catch (Throwable metricsFailure) {
                    getLogger().warning("[Shutdown] Metrics close failed: "
                            + String.valueOf(metricsFailure.getMessage()));
                    if (terminalFailure == null) {
                        terminalFailure = metricsFailure;
                    } else {
                        terminalFailure.addSuppressed(metricsFailure);
                    }
                } finally {
                    metrics = null;
                }
            }
            runtimeInitialized = false;
            try {
                transitionRuntime(ForgeRuntimeStatus.CLOSED, "plugin disabled");
            } catch (Throwable closeFailure) {
                getLogger().warning("[Shutdown] Final runtime transition failed: "
                        + String.valueOf(closeFailure.getMessage()));
                if (terminalFailure == null) {
                    terminalFailure = closeFailure;
                } else {
                    terminalFailure.addSuppressed(closeFailure);
                }
            }
            if (terminalFailure == null) {
                shutdownFuture.complete(null);
            } else {
                shutdownFuture.completeExceptionally(terminalFailure);
            }
        });
    }

    private EmakiCoreLibPlugin resolveCoreLibForShutdown() {
        try {
            return coreLib();
        } catch (Throwable throwable) {
            getLogger().warning("[Shutdown] CoreLib lookup failed: " + String.valueOf(throwable.getMessage()));
            return null;
        }
    }

    private void registerCoreShutdownBarrier(EmakiCoreLibPlugin coreLibPlugin,
            CompletableFuture<Void> shutdownFuture) {
        if (coreLibPlugin == null) {
            getLogger().warning("[Shutdown] CoreLib dependent shutdown barrier is unavailable.");
            return;
        }
        try {
            if (!coreLibPlugin.registerDependentShutdown("forge", shutdownFuture)) {
                getLogger().warning("[Shutdown] CoreLib rejected the Forge dependent shutdown barrier.");
            }
        } catch (Throwable throwable) {
            getLogger().warning("[Shutdown] CoreLib dependent shutdown barrier registration failed: "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    private void clearRuntimeRegistrationsForShutdown(EmakiCoreLibPlugin coreLibPlugin) {
        try {
            playerDataListener.clearSessionsForShutdown();
        } catch (Throwable throwable) {
            getLogger().warning("[Shutdown] Player session cleanup failed: " + String.valueOf(throwable.getMessage()));
        }
        if (coreLibPlugin == null || coreLibPlugin.actionRegistry() == null) {
            return;
        }
        try {
            coreLibPlugin.actionRegistry().unregisterAll(this);
        } catch (Throwable throwable) {
            getLogger().warning("[Shutdown] Forge action cleanup failed: " + String.valueOf(throwable.getMessage()));
        }
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        reloadPluginStateAsync(closeOpenInventories);
    }

    public CompletableFuture<ForgeReloadResult> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null)
                .thenApply(result -> {
                    if (result.installed()) {
                        autoSaveTask = lifecycleCoordinator.rescheduleAutoSave(this, autoSaveTask);
                    }
                    logConfigPrecheckReport();
                    if (debugLogger() != null) {
                        debugLogger().log("forge", (java.util.UUID) null, "forge.runtime_metrics",
                                runtimeMetrics.snapshot().debugValues(runtimeStatus(), runtimeSnapshot().guiState()));
                    }
                    return result;
                });
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "forge");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new ForgeConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(ForgeRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        playerDataStore = components.playerDataStore();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        itemIdentifierService = components.itemIdentifierService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        itemRefreshService = components.itemRefreshService();
        forgeService = components.forgeService();
        forgeGuiService = components.forgeGuiService();
        recipeBookGuiService = components.recipeBookGuiService();
        commandRouter = new ForgeCommandRouter(this, executionDispatcher, threadOwnership);
        itemRefreshListener = new ForgeItemRefreshListener(this, executionDispatcher);
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
        runtimeSnapshot.set(ForgeRuntimeSnapshot.starting(components, appConfigLoader, languageLoader,
                messageService, bootstrapService, recipeLoader, guiTemplateLoader));
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakiforge command",
                java.util.List.of("eforge", "ef"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakiforge.use", commandRouter, commandRouter)
        );
    }

    private void registerActions() {
        new ForgeActionRegistrar(this).register(coreLib().actionRegistry());
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    private void registerPublicApiService() {
        EmakiForgeApi.install(forgeApiBridge);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ForgePlaceholderExpansion(this, playerDataStore);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    @Override
    public <T> T getService(Class<T> type) {
        if (type == null) {
            return null;
        }
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        ForgeRuntimeComponents components = snapshot.components();
        Object service = components == null ? null : components.services().get(type);
        if (service == null && type == YamlConfigLoader.class) {
            service = snapshot.appConfigLoader() == null ? appConfigLoader : snapshot.appConfigLoader();
        } else if (service == null && type == LanguageLoader.class) {
            service = snapshot.languageLoader() == null ? languageLoader : snapshot.languageLoader();
        } else if (service == null && type == RecipeLoader.class) {
            service = snapshot.recipeLoader() == null ? recipeLoader : snapshot.recipeLoader();
        } else if (service == null && type == GuiTemplateLoader.class) {
            service = snapshot.guiTemplateLoader() == null ? guiTemplateLoader : snapshot.guiTemplateLoader();
        } else if (service == null && type == MessageService.class) {
            service = snapshot.messageService() == null ? messageService : snapshot.messageService();
        } else if (service == null && type == BootstrapService.class) {
            service = snapshot.bootstrapService() == null ? bootstrapService : snapshot.bootstrapService();
        }
        if (type.isInstance(service)) {
            return type.cast(service);
        }
        return super.getService(type);
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.appConfigLoader() == null ? appConfigLoader : snapshot.appConfigLoader();
    }

    public LanguageLoader languageLoader() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.languageLoader() == null ? languageLoader : snapshot.languageLoader();
    }

    public RecipeLoader recipeLoader() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.recipeLoader() == null ? recipeLoader : snapshot.recipeLoader();
    }

    public GuiTemplateLoader guiTemplateLoader() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.guiTemplateLoader() == null ? guiTemplateLoader : snapshot.guiTemplateLoader();
    }

    public PlayerDataStore playerDataStore() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? playerDataStore : components.playerDataStore();
    }

    public MessageService messageService() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.messageService() == null ? messageService : snapshot.messageService();
    }

    public BootstrapService bootstrapService() {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return snapshot.bootstrapService() == null ? bootstrapService : snapshot.bootstrapService();
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public ExecutionDispatcher executionDispatcher() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? executionDispatcher : components.executionDispatcher();
    }

    public ThreadOwnership threadOwnership() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? threadOwnership : components.threadOwnership();
    }

    public GuiService guiService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? guiService : components.guiService();
    }

    public ItemIdentifierService itemIdentifierService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? itemIdentifierService : components.itemIdentifierService();
    }

    public ForgeAttributeBridge pdcAttributeGateway() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? pdcAttributeGateway : components.pdcAttributeGateway();
    }

    public ForgeItemRefreshService itemRefreshService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? itemRefreshService : components.itemRefreshService();
    }

    public ForgeService forgeService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? forgeService : components.forgeService();
    }

    public ForgeGuiService forgeGuiService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? forgeGuiService : components.forgeGuiService();
    }

    public RecipeBookGuiService recipeBookGuiService() {
        ForgeRuntimeComponents components = runtimeSnapshot.get().components();
        return components == null ? recipeBookGuiService : components.recipeBookGuiService();
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public ForgeRuntimeSnapshot runtimeSnapshot() {
        return runtimeSnapshot.get();
    }

    public ForgeRuntimeStatus runtimeStatus() {
        return runtimeSnapshot.get().status();
    }

    public long runtimeGeneration() {
        return runtimeSnapshot.get().generation();
    }

    public boolean isRuntimeReady() {
        return runtimeSnapshot.get().available();
    }

    public boolean isGenerationActive(long generation) {
        ForgeRuntimeSnapshot snapshot = runtimeSnapshot.get();
        return generation > 0L && snapshot.generation() == generation
                && snapshot.status() != ForgeRuntimeStatus.CLOSING
                && snapshot.status() != ForgeRuntimeStatus.CLOSED
                && snapshot.status() != ForgeRuntimeStatus.UNAVAILABLE;
    }

    public boolean isGenerationRequested(long generation) {
        return lifecycleCoordinator.requestedGeneration() == generation;
    }

    public ForgeRuntimeMetrics runtimeMetrics() {
        return runtimeMetrics;
    }

    boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    void beginReload(long generation) {
        runtimeSnapshot.updateAndGet(current -> {
            if (shutdownStarted.get()
                    || current.status() == ForgeRuntimeStatus.CLOSING
                    || current.status() == ForgeRuntimeStatus.CLOSED) {
                return current;
            }
            if (current.generation() > 0L && current.recipeLoader() != null) {
                return current.withStatus(ForgeRuntimeStatus.RELOADING);
            }
            return new ForgeRuntimeSnapshot(generation, ForgeRuntimeStatus.STARTING, current.components(),
                    current.appConfigLoader(), current.languageLoader(), current.messageService(),
                    current.bootstrapService(), current.recipeLoader(), current.guiTemplateLoader(),
                    current.recipeReport(), current.lookupSnapshot(), current.installedAtNanos());
        });
    }

    void installCandidate(ForgeReloadCandidate candidate) {
        if (candidate == null) {
            return;
        }
        ForgeRuntimeSnapshot previous = runtimeSnapshot.get();
        if (shutdownStarted.get()
                || previous.status() == ForgeRuntimeStatus.CLOSING
                || previous.status() == ForgeRuntimeStatus.CLOSED
                || !isGenerationRequested(candidate.generation())) {
            throw new IllegalStateException("Forge runtime candidate is no longer installable.");
        }
        ForgeRuntimeComponents previousComponents = previous.components();
        if (previousComponents == null) {
            throw new IllegalStateException("Forge runtime components are unavailable for candidate installation.");
        }
        EmakiCoreLibPlugin coreLibPlugin = coreLib();
        ForgeService nextForgeService = new ForgeService(
                this,
                coreLibPlugin.asyncTaskScheduler(),
                coreLibPlugin.performanceMonitor(),
                coreLibPlugin.itemAssemblyService(),
                coreLibPlugin::actionExecutor,
                previousComponents.executionDispatcher(),
                previousComponents.threadOwnership()
        );
        ForgeItemRefreshService nextItemRefreshService = new ForgeItemRefreshService(
                this,
                coreLibPlugin.itemAssemblyService(),
                previousComponents.executionDispatcher()
        );
        ForgeGuiService nextForgeGuiService = new ForgeGuiService(
                this,
                previousComponents.guiService(),
                previousComponents.executionDispatcher(),
                previousComponents.threadOwnership()
        );
        RecipeBookGuiService nextRecipeBookGuiService = new RecipeBookGuiService(
                this,
                previousComponents.guiService()
        );
        ForgeRuntimeComponents activeComponents = new ForgeRuntimeComponents(
                candidate.appConfigLoader(),
                previousComponents.executionDispatcher(),
                previousComponents.threadOwnership(),
                candidate.languageLoader(),
                candidate.recipeLoader(),
                candidate.guiTemplateLoader(),
                previousComponents.playerDataStore(),
                candidate.messageService(),
                candidate.bootstrapService(),
                previousComponents.guiService(),
                previousComponents.itemIdentifierService(),
                previousComponents.pdcAttributeGateway(),
                nextItemRefreshService,
                nextForgeService,
                nextForgeGuiService,
                nextRecipeBookGuiService
        );
        ForgeRuntimeSnapshot next = ForgeRuntimeSnapshot.active(candidate, activeComponents)
                .withStatus(ForgeRuntimeStatus.RELOADING);
        DebugLogger previousLogger = debugLogger();
        DebugCommand previousCommand = debugCommand;
        DebugLogger nextLogger = replacementDebugLogger(previousLogger, candidate.languageLoader());
        DebugCommand nextCommand = new DebugCommand(nextLogger, DEBUG_MODULES);
        boolean committed = false;
        try {
            nextForgeService.installLookupSnapshot(candidate.lookupSnapshot());
            if (!runtimeSnapshot.compareAndSet(previous, next)) {
                throw new IllegalStateException("Forge runtime changed before candidate publication.");
            }
            committed = true;
            applyRuntimeFields(next);
            setDebugLogger(nextLogger);
            debugCommand = nextCommand;
            previousComponents.forgeService().close();
        } catch (RuntimeException | Error failure) {
            boolean restored = !committed || runtimeSnapshot.compareAndSet(next, previous);
            if (restored) {
                applyRuntimeFields(previous);
                setDebugLogger(previousLogger);
                debugCommand = previousCommand;
            }
            try {
                nextForgeService.close();
                ForgeRuntimeSnapshot active = runtimeSnapshot.get();
                ForgeRuntimeComponents activeRuntime = active.components();
                ForgeService activeForgeService = activeRuntime == null ? forgeService : activeRuntime.forgeService();
                if (activeForgeService != null) {
                    activeForgeService.installLookupSnapshot(active.lookupSnapshot());
                    if (active.available()) {
                        activeForgeService.resumeAccepting();
                    } else {
                        activeForgeService.close();
                    }
                }
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    boolean completeCandidateInstallation(long generation) {
        while (true) {
            ForgeRuntimeSnapshot current = runtimeSnapshot.get();
            if (shutdownStarted.get()
                    || !isGenerationRequested(generation)
                    || current.generation() != generation
                    || current.status() == ForgeRuntimeStatus.CLOSING
                    || current.status() == ForgeRuntimeStatus.CLOSED
                    || current.status() == ForgeRuntimeStatus.UNAVAILABLE) {
                return false;
            }
            if (current.status() == ForgeRuntimeStatus.ACTIVE) {
                return current.available();
            }
            ForgeRuntimeSnapshot next = current.withStatus(ForgeRuntimeStatus.ACTIVE);
            if (!runtimeSnapshot.compareAndSet(current, next)) {
                continue;
            }
            ForgeService activeForgeService = next.forgeService();
            if (activeForgeService == null) {
                runtimeSnapshot.compareAndSet(next, current.withStatus(ForgeRuntimeStatus.UNAVAILABLE));
                return false;
            }
            activeForgeService.resumeAccepting();
            return activeForgeService.isAccepting() && next.available();
        }
    }

    private DebugLogger replacementDebugLogger(DebugLogger previous, LanguageLoader nextLanguageLoader) {
        DebugLogger replacement = new DebugLogger(this, nextLanguageLoader);
        if (previous == null || !previous.isGlobalEnabled()) {
            return replacement;
        }
        if (previous.trackedPlayers().isEmpty() && previous.enabledModules().isEmpty()) {
            replacement.enableAll();
            return replacement;
        }
        previous.enabledModules().forEach(replacement::enableModule);
        previous.trackedPlayers().forEach(replacement::addPlayer);
        return replacement;
    }

    private void applyRuntimeFields(ForgeRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ForgeRuntimeComponents components = snapshot.components();
        if (components != null) {
            appConfigLoader = components.appConfigLoader();
            executionDispatcher = components.executionDispatcher();
            threadOwnership = components.threadOwnership();
            languageLoader = components.languageLoader();
            recipeLoader = components.recipeLoader();
            guiTemplateLoader = components.guiTemplateLoader();
            playerDataStore = components.playerDataStore();
            messageService = components.messageService();
            bootstrapService = components.bootstrapService();
            guiService = components.guiService();
            itemIdentifierService = components.itemIdentifierService();
            pdcAttributeGateway = components.pdcAttributeGateway();
            itemRefreshService = components.itemRefreshService();
            forgeService = components.forgeService();
            forgeGuiService = components.forgeGuiService();
            recipeBookGuiService = components.recipeBookGuiService();
            return;
        }
        appConfigLoader = snapshot.appConfigLoader();
        languageLoader = snapshot.languageLoader();
        messageService = snapshot.messageService();
        bootstrapService = snapshot.bootstrapService();
        recipeLoader = snapshot.recipeLoader();
        guiTemplateLoader = snapshot.guiTemplateLoader();
    }

    void failReload(long generation, String reason) {
        failReload(generation, null, reason);
    }

    void failReload(ForgeReloadCandidate candidate, String reason) {
        failReload(candidate == null ? runtimeGeneration() : candidate.generation(), candidate, reason);
    }

    private void failReload(long generation, ForgeReloadCandidate candidate, String reason) {
        while (true) {
            ForgeRuntimeSnapshot current = runtimeSnapshot.get();
            if (shutdownStarted.get()
                    || current.status() == ForgeRuntimeStatus.CLOSING
                    || current.status() == ForgeRuntimeStatus.CLOSED) {
                return;
            }
            ForgeRuntimeComponents currentComponents = current.components();
            ForgeService currentForgeService = currentComponents == null ? forgeService : currentComponents.forgeService();
            boolean preservedRuntime = current.generation() > 0L
                    && current.recipeLoader() != null
                    && currentForgeService != null
                    && currentForgeService.lookupSnapshot() != null
                    && currentForgeService.lookupSnapshot().generation() == current.generation();
            ForgeRuntimeSnapshot next = preservedRuntime
                    ? current.withStatus(ForgeRuntimeStatus.ACTIVE)
                    : ForgeRuntimeSnapshot.unavailable(generation, candidate, ForgeRuntimeStatus.UNAVAILABLE);
            if (!runtimeSnapshot.compareAndSet(current, next)) {
                continue;
            }
            if (currentForgeService != null) {
                if (preservedRuntime) {
                    currentForgeService.resumeAccepting();
                } else {
                    currentForgeService.close();
                }
            }
            return;
        }
    }

    private void transitionRuntime(ForgeRuntimeStatus status, String detail) {
        runtimeSnapshot.updateAndGet(current -> current.withStatus(status));
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
            String[] completionArgs = args.length == 0 ? new String[]{""} : args;
            java.util.List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? java.util.List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

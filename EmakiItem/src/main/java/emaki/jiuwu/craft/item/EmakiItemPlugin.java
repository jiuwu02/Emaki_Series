package emaki.jiuwu.craft.item;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.action.ItemActionRegistrar;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.config.ItemConfigPrecheckContributor;
import emaki.jiuwu.craft.item.listener.ItemDurabilityListener;
import emaki.jiuwu.craft.item.listener.ItemRepairListener;
import emaki.jiuwu.craft.item.listener.ItemTriggerListener;
import emaki.jiuwu.craft.item.listener.ItemUpdateListener;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.papi.ItemPlaceholderExpansion;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemIdResolver;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewService;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;
import emaki.jiuwu.craft.item.service.ItemRefreshMetrics;
import emaki.jiuwu.craft.item.service.ItemRepairGuiService;
import emaki.jiuwu.craft.item.service.ItemRepairService;
import emaki.jiuwu.craft.item.script.js.JavaScriptItemDefinitionRegistry;
import emaki.jiuwu.craft.item.script.JavaScriptItemFactoryRegistry;

public final class EmakiItemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiitem";
    private static final Set<String> DEBUG_MODULES = Set.of("create", "update", "identify", "set", "item_operation", "pdc");
    private static final String STARTUP_ASCII = """
             ______  __    __  ______  __  __   __  __  ______  ______  __    __  ______
            /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\/\\__  _\\/\\  ___\\/\\ "-./  \\/\\  ___\\
            \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\/_/\\ \\/\\ \\  __\\\\ \\ \\-./\\ \\ \\___  \\
             \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\ \\_\\ \\ \\_____\\ \\_\\ \\ \\_\\/\\_____\\
              \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/  \\/_/  \\/_____/\\/_/  \\/_/\\/_____/
            """;
    private static final int STARTUP_ASCII_START_COLOR = 0x60A5FA;
    private static final int STARTUP_ASCII_END_COLOR = 0x34D399;
    private static final int BSTATS_PLUGIN_ID = 31770;
    private static final String REQUIRED_CORELIB_API_VERSION = "4.5.18";

    private BStatsRegistration metrics;
    private final ItemRefreshMetrics refreshMetrics = new ItemRefreshMetrics();
    private final Object readinessMonitor = new Object();
    private long lifecycleEpoch;
    private long reloadGeneration;
    private long latestCompletedReloadGeneration = -1L;
    private int activeReloads;
    private boolean lifecycleActive;
    private boolean reloadQueueSucceeded;
    private boolean runtimeReady;
    private boolean coreLibDisableRequested;
    private CompletableFuture<Void> reloadTail = CompletableFuture.completedFuture(null);

    private final ItemLifecycleCoordinator lifecycleCoordinator = new ItemLifecycleCoordinator();
    private ItemCommandRouter commandRouter;
    private ItemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;

    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiTemplateLoader guiTemplateLoader;
    private GuiService guiService;
    private EmakiItemLoader itemLoader;
    private EmakiItemSetLoader setLoader;
    private EmakiItemAliasLoader aliasLoader;
    private EmakiItemIdResolver idResolver;
    private EmakiItemMigrationService migrationService;
    private EmakiItemLayerPreviewService layerPreviewService;
    private EmakiItemIdentifier identifier;
    private EmakiItemPdcWriter pdcWriter;
    private EmakiItemFactory itemFactory;
    private EmakiItemUpdateService updateService;
    private EmakiItemSetService setService;
    private EmakiItemActionService actionService;
    private EmakiItemConditionChecker conditionChecker;
    private ItemComponentInspector componentInspector;
    private ItemComponentPlaceholderResolver componentPlaceholderResolver;
    private ItemSourceService itemSourceService;
    private PdcAttributeGateway pdcAttributeGateway;
    private ItemRepairService repairService;
    private ItemRepairGuiService repairGuiService;
    private JavaScriptItemDefinitionRegistry javaScriptDefinitionRegistry;
    private JavaScriptItemFactoryRegistry javaScriptFactoryRegistry;
    private final EmakiItemApi.Bridge itemApiBridge = new EmakiItemApi.Bridge() {
        @Override
        public boolean isReady() {
            return runtimeReady();
        }

        @Override
        public boolean exists(String id) {
            return idResolver != null && idResolver.resolveDefinition(id) != null;
        }

        @Override
        public @Nullable ItemStack create(String id, int amount) {
            return itemFactory == null ? null : itemFactory.create(id, amount);
        }

        @Override
        public @Nullable String identify(@Nullable ItemStack itemStack) {
            return identifier == null ? null : identifier.identify(itemStack);
        }

        @Override
        public @NotNull Set<String> definitionIds() {
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            if (itemLoader != null) {
                ids.addAll(itemLoader.all().keySet());
            }
            if (javaScriptDefinitionRegistry != null) {
                ids.addAll(javaScriptDefinitionRegistry.ids());
            }
            return Set.copyOf(ids);
        }

        @Override
        public @Nullable ConfiguredItemDefinition definition(String id) {
            var definition = idResolver == null ? null : idResolver.resolveDefinition(id);
            return definition == null ? null : definition.itemDefinition();
        }

        @Override
        public @NotNull String displayName(String id) {
            ItemStack itemStack = create(id, 1);
            if (itemStack == null) {
                return "";
            }
            String text = ItemTextBridge.effectiveNameText(itemStack);
            return Texts.isBlank(text) ? MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack)) : text;
        }
    };

    public EmakiItemPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        beginLifecycleEpoch();
        if (!requireCoreLibCompatibility("startup")) {
            return;
        }
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        try {
            applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        } catch (RuntimeException | LinkageError exception) {
            disableForCoreLibFailure("initialize", exception);
            return;
        }
        registerConfigPrecheckContributor();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        EmakiItemApi.install(itemApiBridge);
        lifecycleCoordinator.registerServices(this);
        registerActions();
        registerCommandHandler();
        registerEventHandlers();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        invalidateLifecycleEpoch();
        ConfigPrecheckLifecycleSupport.unregister("item");
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib != null && coreLib.actionRegistry() != null) {
            coreLib.actionRegistry().unregisterAll(this);
        }
        if (javaScriptDefinitionRegistry != null) {
            javaScriptDefinitionRegistry.clear();
        }
        if (javaScriptFactoryRegistry != null) {
            javaScriptFactoryRegistry.clear();
        }
        if (coreLib != null && coreLib.javaScriptRegistrationTracker() != null) {
            coreLib.javaScriptRegistrationTracker().unregisterOwner(this);
        }
        EmakiItemApi.uninstall(itemApiBridge);
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState() {
        ReloadAttempt attempt = beginReloadAttempt();
        if (attempt == null) {
            return;
        }
        observeReload(enqueueReloadAttempt(attempt, () -> lifecycleCoordinator
                .reloadAsync(this, null, () -> isReloadAttemptCurrent(attempt))
                .thenRun(() -> {
                    if (isReloadAttemptCurrent(attempt)) {
                        logConfigPrecheckReport();
                    }
                })));
    }

    public CompletableFuture<Void> reloadPluginStateAsync() {
        ReloadAttempt attempt = beginReloadAttempt();
        if (attempt == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> reload = enqueueReloadAttempt(attempt, () -> lifecycleCoordinator
                .reloadAsync(this, null, () -> isReloadAttemptCurrent(attempt))
                .thenRun(() -> {
                    if (isReloadAttemptCurrent(attempt)) {
                        logConfigPrecheckReport();
                    }
                }));
        observeReload(reload);
        return reload;
    }

    private void beginLifecycleEpoch() {
        synchronized (readinessMonitor) {
            lifecycleEpoch++;
            reloadGeneration = 0L;
            latestCompletedReloadGeneration = -1L;
            activeReloads = 0;
            lifecycleActive = true;
            reloadQueueSucceeded = true;
            runtimeReady = false;
            coreLibDisableRequested = false;
            reloadTail = CompletableFuture.completedFuture(null);
        }
    }

    private void invalidateLifecycleEpoch() {
        synchronized (readinessMonitor) {
            lifecycleEpoch++;
            reloadGeneration = 0L;
            latestCompletedReloadGeneration = -1L;
            activeReloads = 0;
            lifecycleActive = false;
            reloadQueueSucceeded = false;
            runtimeReady = false;
            reloadTail = CompletableFuture.completedFuture(null);
        }
    }

    private ReloadAttempt beginReloadAttempt() {
        synchronized (readinessMonitor) {
            if (!lifecycleActive) {
                return null;
            }
            long generation = ++reloadGeneration;
            activeReloads++;
            runtimeReady = false;
            return new ReloadAttempt(lifecycleEpoch, generation);
        }
    }

    private CompletableFuture<Void> enqueueReloadAttempt(
            ReloadAttempt attempt,
            java.util.function.Supplier<CompletableFuture<Void>> reloadAction) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> queued;
        synchronized (readinessMonitor) {
            CompletableFuture<Void> predecessor = reloadTail;
            queued = predecessor.handle((ignored, failure) -> null)
                    .thenCompose(ignored -> gate.thenCompose(unused -> startReloadAttempt(attempt, reloadAction)));
            queued = queued.whenComplete((ignored, failure) -> completeReloadAttempt(attempt, failure == null));
            reloadTail = queued.handle((ignored, failure) -> null);
        }
        gate.complete(null);
        return queued;
    }

    private CompletableFuture<Void> startReloadAttempt(
            ReloadAttempt attempt,
            java.util.function.Supplier<CompletableFuture<Void>> reloadAction) {
        if (!isReloadAttemptCurrent(attempt)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            CompletableFuture<Void> reload = reloadAction == null ? null : reloadAction.get();
            return reload == null ? CompletableFuture.failedFuture(
                    new IllegalStateException("EmakiItem reload action returned no completion stage.")) : reload;
        } catch (RuntimeException | LinkageError exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void completeReloadAttempt(ReloadAttempt attempt, boolean succeeded) {
        if (attempt == null) {
            return;
        }
        synchronized (readinessMonitor) {
            if (attempt.lifecycleEpoch() != lifecycleEpoch) {
                return;
            }
            activeReloads = Math.max(0, activeReloads - 1);
            latestCompletedReloadGeneration = Math.max(
                    latestCompletedReloadGeneration,
                    attempt.reloadGeneration()
            );
            if (activeReloads == 0 && latestCompletedReloadGeneration == reloadGeneration) {
                reloadQueueSucceeded = succeeded;
            }
            runtimeReady = lifecycleActive
                    && activeReloads == 0
                    && latestCompletedReloadGeneration == reloadGeneration
                    && reloadQueueSucceeded;
        }
    }

    private boolean isReloadAttemptCurrent(ReloadAttempt attempt) {
        synchronized (readinessMonitor) {
            return lifecycleActive
                    && attempt != null
                    && attempt.lifecycleEpoch() == lifecycleEpoch
                    && attempt.reloadGeneration() == reloadGeneration;
        }
    }

    private void observeReload(CompletableFuture<Void> reload) {
        if (reload == null) {
            return;
        }
        reload.whenComplete((ignored, failure) -> {
            if (failure == null) {
                return;
            }
            Throwable cause = failure instanceof java.util.concurrent.CompletionException completionException
                    && completionException.getCause() != null
                    ? completionException.getCause()
                    : failure;
            getLogger().warning("EmakiItem reload failed: " + cause.getClass().getSimpleName()
                    + (Texts.isBlank(cause.getMessage()) ? "" : ": " + cause.getMessage()));
        });
    }

    private boolean runtimeReady() {
        synchronized (readinessMonitor) {
            return runtimeReady;
        }
    }

    public static boolean requireCoreLibCompatibility(String usage) {
        EmakiItemPlugin plugin = null;
        try {
            plugin = JavaPlugin.getPlugin(EmakiItemPlugin.class);
        } catch (RuntimeException | LinkageError ignored) {
        }
        String actualVersion = "<unavailable>";
        boolean available = false;
        boolean ready = false;
        try {
            available = EmakiCoreLibApi.available();
            ready = EmakiCoreLibApi.isReady();
            String reportedVersion = EmakiCoreLibApi.apiVersion();
            actualVersion = reportedVersion == null || reportedVersion.isBlank()
                    ? "<unavailable>"
                    : reportedVersion;
            if (available && ready && REQUIRED_CORELIB_API_VERSION.equals(actualVersion)) {
                return true;
            }
            String details = "required=" + REQUIRED_CORELIB_API_VERSION
                    + ", actual=" + actualVersion
                    + ", available=" + available
                    + ", ready=" + ready;
            if (plugin != null) {
                plugin.disableForCoreLibFailure(usage, details, null);
            } else {
                Bukkit.getLogger().severe("EmakiItem CoreLib compatibility failure during "
                        + Texts.toStringSafe(usage) + ": " + details + ".");
            }
            return false;
        } catch (RuntimeException | LinkageError exception) {
            String details = "required=" + REQUIRED_CORELIB_API_VERSION
                    + ", actual=" + actualVersion
                    + ", available=" + available
                    + ", ready=" + ready
                    + ", failure=" + exception.getClass().getSimpleName();
            if (plugin != null) {
                plugin.disableForCoreLibFailure(usage, details, exception);
            } else {
                Bukkit.getLogger().severe("EmakiItem CoreLib compatibility preflight failed during "
                        + Texts.toStringSafe(usage) + ": " + details + ".");
            }
            return false;
        }
    }

    public static void reportCoreLibApiFailure(String usage, Throwable exception) {
        EmakiItemPlugin plugin = null;
        try {
            plugin = JavaPlugin.getPlugin(EmakiItemPlugin.class);
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (plugin != null) {
            plugin.disableForCoreLibFailure(usage, exception);
        } else {
            Bukkit.getLogger().severe("EmakiItem CoreLib API call failed during "
                    + Texts.toStringSafe(usage) + ": "
                    + (exception == null ? "unknown" : exception.getClass().getSimpleName()) + ".");
        }
    }

    private void disableForCoreLibFailure(String usage, Throwable exception) {
        disableForCoreLibFailure(
                usage,
                "failure=" + (exception == null ? "unknown" : exception.getClass().getSimpleName()),
                exception
        );
    }

    private void disableForCoreLibFailure(String usage, String details, Throwable exception) {
        synchronized (readinessMonitor) {
            runtimeReady = false;
            reloadQueueSucceeded = false;
            if (coreLibDisableRequested) {
                return;
            }
            coreLibDisableRequested = true;
        }
        String message = "EmakiItem requires ready EmakiCoreLib API " + REQUIRED_CORELIB_API_VERSION
                + " during " + Texts.toStringSafe(usage)
                + " but compatibility validation failed: " + Texts.toStringSafe(details)
                + ". Disabling EmakiItem.";
        getLogger().severe(message);
        if (exception != null && Texts.isNotBlank(exception.getMessage())) {
            getLogger().severe("CoreLib compatibility failure detail: " + exception.getMessage());
        }
        Runnable disable = () -> getServer().getPluginManager().disablePlugin(this);
        if (Bukkit.isPrimaryThread()) {
            disable.run();
        } else {
            Bukkit.getScheduler().runTask(this, disable);
        }
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "item");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new ItemConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(ItemRuntimeComponents components) {
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiTemplateLoader = components.guiTemplateLoader();
        guiService = components.guiService();
        itemLoader = components.itemLoader();
        setLoader = components.setLoader();
        aliasLoader = components.aliasLoader();
        idResolver = components.idResolver();
        migrationService = components.migrationService();
        layerPreviewService = components.layerPreviewService();
        identifier = components.identifier();
        pdcWriter = components.pdcWriter();
        itemFactory = components.itemFactory();
        updateService = components.updateService();
        setService = components.setService();
        actionService = components.actionService();
        conditionChecker = components.conditionChecker();
        componentInspector = components.componentInspector();
        componentPlaceholderResolver = components.componentPlaceholderResolver();
        itemSourceService = components.itemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        repairService = components.repairService();
        repairGuiService = components.repairGuiService();
        javaScriptDefinitionRegistry = components.javaScriptDefinitionRegistry();
        javaScriptFactoryRegistry = components.javaScriptFactoryRegistry();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        commandRouter = new ItemCommandRouter(this, executionDispatcher, threadOwnership);
        registerServices(components);
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakiitem command",
                java.util.List.of("ei"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakiitem.use", commandRouter, commandRouter)
        );
    }

    private void registerActions() {
        new ItemActionRegistrar(this).register(coreLib().actionRegistry());
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(new ItemTriggerListener(this), this);
        getServer().getPluginManager().registerEvents(
                new ItemUpdateListener(this, executionDispatcher, threadOwnership),
                this
        );
        getServer().getPluginManager().registerEvents(new ItemDurabilityListener(this, repairService), this);
        getServer().getPluginManager().registerEvents(new ItemRepairListener(this, repairService), this);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ItemPlaceholderExpansion(this);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public GuiService guiService() {
        return guiService;
    }

    public EmakiItemLoader itemLoader() {
        return itemLoader;
    }

    public EmakiItemSetLoader setLoader() {
        return setLoader;
    }

    public EmakiItemAliasLoader aliasLoader() {
        return aliasLoader;
    }

    public EmakiItemIdResolver idResolver() {
        return idResolver;
    }

    public EmakiItemMigrationService migrationService() {
        return migrationService;
    }

    public EmakiItemLayerPreviewService layerPreviewService() {
        return layerPreviewService;
    }

    public EmakiItemIdentifier identifier() {
        return identifier;
    }

    public EmakiItemPdcWriter pdcWriter() {
        return pdcWriter;
    }

    public EmakiItemFactory itemFactory() {
        return itemFactory;
    }

    public EmakiItemUpdateService updateService() {
        return updateService;
    }

    public EmakiItemSetService setService() {
        return setService;
    }

    public EmakiItemActionService actionService() {
        return actionService;
    }

    public EmakiItemConditionChecker conditionChecker() {
        return conditionChecker;
    }

    public EmakiItemApi.Bridge itemApi() {
        return itemApiBridge;
    }

    public ItemComponentInspector componentInspector() {
        return componentInspector;
    }

    public ItemComponentPlaceholderResolver componentPlaceholderResolver() {
        return componentPlaceholderResolver;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public ItemRepairService repairService() {
        return repairService;
    }

    public ItemRepairGuiService repairGuiService() {
        return repairGuiService;
    }

    public JavaScriptItemDefinitionRegistry javaScriptDefinitionRegistry() {
        return javaScriptDefinitionRegistry;
    }

    public JavaScriptItemFactoryRegistry javaScriptFactoryRegistry() {
        return javaScriptFactoryRegistry;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public void scheduleAttributeEquipmentSync(Player player) {
        if (player == null) {
            return;
        }
        RegisteredServiceProvider<EmakiAttributeBridge> registration = Bukkit.getServicesManager().getRegistration(EmakiAttributeBridge.class);
        EmakiAttributeBridge bridge = registration == null ? null : registration.getProvider();
        if (bridge != null && bridge.available()) {
            bridge.scheduleEquipmentSync(player);
        }
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public ItemRefreshMetrics refreshMetrics() {
        return refreshMetrics;
    }

    private record ReloadAttempt(long lifecycleEpoch, long reloadGeneration) {
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

package emaki.jiuwu.craft.item;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.action.ItemStageRegistrar;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider;
import emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewRegistration;
import emaki.jiuwu.craft.item.bridge.MythicItemDropBridge;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.config.ItemConfigPrecheckContributor;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridge;
import emaki.jiuwu.craft.item.integration.ItemContributionGateLifecycle;
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
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewRegistry;
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
import emaki.jiuwu.craft.item.apiimpl.DefaultEmakiItemApi;

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
    private static final int STARTUP_ASCII_START_COLOR = 0x30F07D;
    private static final int STARTUP_ASCII_END_COLOR = 0x627DF5;
    private static final int BSTATS_PLUGIN_ID = 31770;

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
    private CompletableFuture<Void> reloadTail = CompletableFuture.completedFuture(null);

    private final ItemLifecycleCoordinator lifecycleCoordinator = new ItemLifecycleCoordinator();
    private final ItemContributionGateLifecycle itemContributionGateLifecycle = new ItemContributionGateLifecycle(this);
    private MythicItemDropBridge mythicDropBridge;
    private ItemCommandRouter commandRouter;
    private ItemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private ItemStageRegistrar stageRegistrar;

    private EmakiScheduling scheduling;
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
    private EmakiItemLayerPreviewRegistry layerPreviewRegistry;
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
    private ItemAttributeBridge pdcAttributeGateway;
    private ItemRepairService repairService;
    private ItemRepairGuiService repairGuiService;
    private final EmakiItemApi.Bridge itemApiBridge =
            new DefaultEmakiItemApi(this);

    public EmakiItemPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        beginLifecycleEpoch();
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        registerConfigPrecheckContributor();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        EmakiItemApi.install(itemApiBridge);
        lifecycleCoordinator.registerServices(this);
        registerActions();
        registerCommandHandler();
        registerEventHandlers();
        itemContributionGateLifecycle.initialize();
        registerMythicDrops();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        invalidateLifecycleEpoch();
        ConfigPrecheckLifecycleSupport.unregister("item");
        itemContributionGateLifecycle.close();
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
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
            reloadTail = CompletableFuture.completedFuture(null);
        }
        publishLoading();
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
        publishAbsent();
    }

    private ReloadAttempt beginReloadAttempt() {
        ReloadAttempt attempt;
        synchronized (readinessMonitor) {
            if (!lifecycleActive) {
                return null;
            }
            long generation = ++reloadGeneration;
            activeReloads++;
            runtimeReady = false;
            attempt = new ReloadAttempt(lifecycleEpoch, generation);
        }
        publishLoading();
        return attempt;
    }

    private CompletableFuture<Void> enqueueReloadAttempt(
            ReloadAttempt attempt,
            Supplier<CompletableFuture<Void>> reloadAction) {
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
            Supplier<CompletableFuture<Void>> reloadAction) {
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

        boolean becameReady = false;
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
            boolean previouslyReady = runtimeReady;
            runtimeReady = lifecycleActive
                    && activeReloads == 0
                    && latestCompletedReloadGeneration == reloadGeneration
                    && reloadQueueSucceeded;
            becameReady = runtimeReady && !previouslyReady;
        }
        if (becameReady) {
            publishReady();
        }
    }

    private void publishReady() {
        publishReadiness(coreLib -> coreLib.markModuleReady(getName()));
    }

    private void publishLoading() {
        publishReadiness(coreLib -> coreLib.markModuleLoading(getName()));
    }

    private void publishAbsent() {
        publishReadiness(coreLib -> coreLib.markModuleAbsent(getName()));
    }

    private void publishReadiness(Consumer<EmakiCoreLibPlugin> action) {
        try {
            action.accept(coreLib());
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiItem readiness publication skipped: " + exception);
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
            Throwable cause = AsyncFailures.unwrapOnce(failure);
            getLogger().warning("EmakiItem reload failed: " + cause.getClass().getSimpleName()
                    + (Texts.isBlank(cause.getMessage()) ? "" : ": " + cause.getMessage()));
        });
    }

    public boolean runtimeReady() {
        synchronized (readinessMonitor) {
            return runtimeReady;
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new ItemConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(ItemRuntimeComponents components) {
        scheduling = components.scheduling();
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
        layerPreviewRegistry = components.layerPreviewRegistry();
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
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        commandRouter = new ItemCommandRouter(this, scheduling);
        registerServices(components);
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakiitem command",
                List.of("ei"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakiitem.use", commandRouter, commandRouter)
        );
    }

    private void registerActions() {
        stageRegistrar = new ItemStageRegistrar(this);
        stageRegistrar.register();
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(layerPreviewRegistry, this);
        getServer().getPluginManager().registerEvents(new ItemTriggerListener(this), this);
        getServer().getPluginManager().registerEvents(
                new ItemUpdateListener(this, scheduling),
                this
        );
        getServer().getPluginManager().registerEvents(new ItemDurabilityListener(this, repairService), this);
        getServer().getPluginManager().registerEvents(new ItemRepairListener(this, repairService), this);
    }

    private void registerMythicDrops() {
        if (!appConfig().mythicEnabled() || !appConfig().mythicDropsEnabled()
                || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicDropBridge = new MythicItemDropBridge(this);
        getServer().getPluginManager().registerEvents(mythicDropBridge, this);
        messageService.info("console.mythic_drops_registered");
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ItemPlaceholderExpansion(this);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public EmakiScheduling scheduling() {
        return scheduling;
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

    public EmakiItemLayerPreviewRegistry layerPreviewRegistry() {
        return layerPreviewRegistry;
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

    public ItemAttributeBridge pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public ItemRepairService repairService() {
        return repairService;
    }

    public ItemRepairGuiService repairGuiService() {
        return repairGuiService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public void scheduleAttributeEquipmentSync(Player player) {
        if (player == null || pdcAttributeGateway == null) {
            return;
        }
        pdcAttributeGateway.scheduleEquipmentSync(player);
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
            String[] completionArgs = args.length == 0 ? new String[]{""} : args;
            List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

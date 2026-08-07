package emaki.jiuwu.craft.cooking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;
import emaki.jiuwu.craft.cooking.action.CookingStageRegistrar;
import emaki.jiuwu.craft.cooking.config.AppConfig;
import emaki.jiuwu.craft.cooking.config.CookingConfigPrecheckContributor;
import emaki.jiuwu.craft.cooking.listener.MmoItemsNutritionListener;
import emaki.jiuwu.craft.cooking.listener.NeigeItemsNutritionListener;
import emaki.jiuwu.craft.cooking.listener.NutritionConsumeListener;
import emaki.jiuwu.craft.cooking.listener.NutritionPlayerDataListener;
import emaki.jiuwu.craft.cooking.loader.ChoppingBoardRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.FermentationBarrelRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.JuicerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.NutritionTypeLoader;
import emaki.jiuwu.craft.cooking.loader.OvenRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingCompletionCoordinator;
import emaki.jiuwu.craft.cooking.service.CookingEffectService;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.CookingStationLocator;
import emaki.jiuwu.craft.cooking.service.CookingStationTracker;
import emaki.jiuwu.craft.cooking.service.FermentationBarrelRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.JuicerRuntimeService;
import emaki.jiuwu.craft.cooking.service.NutritionService;
import emaki.jiuwu.craft.cooking.service.NutritionTypeRegistry;
import emaki.jiuwu.craft.cooking.service.OvenRuntimeService;
import emaki.jiuwu.craft.cooking.service.PlayerNutritionDataStore;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import emaki.jiuwu.craft.cooking.papi.CookingPlaceholderExpansion;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;

public final class EmakiCookingPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "ecooking";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  __  __   __  __   __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ "-.\\ \\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\ \\/\\ \\ \\  _"-.\\ \\ \\ \\ \\-.  \\ \\ \\__ \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_____\\ \\_\\ \\_\\\\ \\_\\ \\_\\\\"\\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/\\/_/\\/_/ \\/_/\\/_/ \\/_/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x22C55E;
    private static final int STARTUP_ASCII_END_COLOR = 0xFACC15;
    private static final int BSTATS_PLUGIN_ID = 31765;

    private BStatsRegistration metrics;

    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "stir", "display", "station", "pdc");

    private final CookingLifecycleCoordinator lifecycleCoordinator = new CookingLifecycleCoordinator();
    private final CookingCommandRouter commandRouter = new CookingCommandRouter(this);
    private CookingStationListener stationListener;
    private CookingPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private CookingStageRegistrar stageRegistrar;

    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private ChoppingBoardRecipeLoader choppingBoardRecipeLoader;
    private WokRecipeLoader wokRecipeLoader;
    private GrinderRecipeLoader grinderRecipeLoader;
    private SteamerRecipeLoader steamerRecipeLoader;
    private OvenRecipeLoader ovenRecipeLoader;
    private JuicerRecipeLoader juicerRecipeLoader;
    private FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private ItemSourceService coreItemSourceService;
    private CraftEngineBlockBridge craftEngineBlockBridge;
    private CustomBlockBridge itemsAdderBlockBridge;
    private CustomBlockBridge nexoBlockBridge;
    private CustomBlockBridge oraxenBlockBridge;
    private CookingSettingsService settingsService;
    private CookingBlockMatcher blockMatcher;
    private StationStateStore stationStateStore;
    private CookingRecipeService recipeService;
    private CookingRewardService rewardService;
    private CookingCompletionCoordinator completionCoordinator;
    private CookingInspectService inspectService;
    private CookingDisplayService displayService;
    private CookingTextDisplayService textDisplayService;
    private CookingEffectService effectService;
    private ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private WokRuntimeService wokRuntimeService;
    private GrinderRuntimeService grinderRuntimeService;
    private SteamerRuntimeService steamerRuntimeService;
    private OvenRuntimeService ovenRuntimeService;
    private JuicerRuntimeService juicerRuntimeService;
    private FermentationBarrelRuntimeService fermentationBarrelRuntimeService;
    private CookingStationLocator stationLocator;
    private CookingStationTracker stationTracker;
    private NutritionTypeLoader nutritionTypeLoader;
    private NutritionTypeRegistry nutritionTypeRegistry;
    private PlayerNutritionDataStore nutritionDataStore;
    private NutritionService nutritionService;
    private final EmakiCookingApi.Bridge cookingApiBridge =
            new emaki.jiuwu.craft.cooking.apiimpl.DefaultEmakiCookingApi(this);
    private volatile boolean publicApiReady;

    public EmakiCookingPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        registerConfigPrecheckContributor();
        reloadPluginState();
        recoverCookingCompletions();
        registerCommandHandler();
        registerEventHandlers();
        registerActions();
        registerPublicApiService();
        registerPlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        publicApiReady = false;
        publishAbsent();
        ConfigPrecheckLifecycleSupport.unregister("cooking");
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.namespaceRegistry().unregister("cooking");
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
        }
        EmakiCookingApi.uninstall(cookingApiBridge);
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (nutritionService != null) {
            nutritionService.shutdown();
        }
        if (nutritionDataStore != null) {
            var flushResult = nutritionDataStore.flushAndSeal(5L, TimeUnit.SECONDS);
            if (!flushResult.success()) {
                getLogger().warning("Nutrition data drain incomplete: pending="
                        + flushResult.pendingFileOperations()
                        + ", dirty=" + flushResult.remainingDirtyEntries()
                        + ", failures=" + flushResult.failures().size());
            }
        }
        if (grinderRuntimeService != null) {
            grinderRuntimeService.shutdown();
        }
        if (steamerRuntimeService != null) {
            steamerRuntimeService.shutdown();
        }
        if (ovenRuntimeService != null) {
            ovenRuntimeService.shutdown();
        }
        if (juicerRuntimeService != null) {
            juicerRuntimeService.shutdown();
        }
        if (fermentationBarrelRuntimeService != null) {
            fermentationBarrelRuntimeService.shutdown();
        }
        if (completionCoordinator != null) {
            var drainResult = completionCoordinator.sealAndDrain(5L, TimeUnit.SECONDS);
            if (!drainResult.drained() || !drainResult.failures().isEmpty()) {
                getLogger().warning("Cooking completion journal drain incomplete: pending="
                        + drainResult.pendingOperations()
                        + ", failures=" + drainResult.failures().size());
            }
        }
        if (stationStateStore != null) {
            var drainResult = stationStateStore.sealAndDrain(5L, TimeUnit.SECONDS);
            if (!drainResult.drained() || !drainResult.failures().isEmpty()) {
                getLogger().warning("Station state drain incomplete: pending="
                        + drainResult.pendingOperations()
                        + ", failures=" + drainResult.failures().size());
            }
        }
        if (displayService != null) {
            displayService.shutdown();
        }
        if (textDisplayService != null) {
            textDisplayService.shutdown();
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
    }

    public void reloadPluginState() {
        publicApiReady = false;
        publishLoading();
        lifecycleCoordinator.reload(this);
        logConfigPrecheckReport();
        publicApiReady = true;
        publishReady();
    }

    public CompletableFuture<Void> reloadPluginStateAsync() {
        publicApiReady = false;
        publishLoading();
        return lifecycleCoordinator.reloadAsync(this, null)
                .thenRun(() -> {
                    logConfigPrecheckReport();
                    publicApiReady = true;
                    publishReady();
                });
    }

    /**
     * Publishes "my data is loaded" to CoreLib's readiness registry.
     *
     * <p>This module sets {@code publicApiReady} in a plain method body with no lock held, so there is
     * no monitor to leave before the waiting third-party callbacks run synchronously here.</p>
     */
    private void publishReady() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleReady(getName()));
    }

    private void publishLoading() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleLoading(getName()));
    }

    private void publishAbsent() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleAbsent(getName()));
    }

    /**
     * Runs a readiness publication, tolerating CoreLib being gone.
     *
     * @param action what to publish
     */
    private void publishReadiness(java.util.function.Consumer<EmakiCoreLibPlugin> action) {
        try {
            action.accept(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class));
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiCooking readiness publication skipped: " + exception);
        }
    }

    private void recoverCookingCompletions() {
        if (completionCoordinator == null) {
            return;
        }
        completionCoordinator.recover().whenComplete((_, error) -> {
            if (error != null) {
                getLogger().warning("Cooking completion recovery failed: " + error.getMessage());
            }
        });
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "cooking");
    }

    private void applyRuntimeComponents(CookingRuntimeComponents components) {
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        choppingBoardRecipeLoader = components.choppingBoardRecipeLoader();
        wokRecipeLoader = components.wokRecipeLoader();
        grinderRecipeLoader = components.grinderRecipeLoader();
        steamerRecipeLoader = components.steamerRecipeLoader();
        ovenRecipeLoader = components.ovenRecipeLoader();
        juicerRecipeLoader = components.juicerRecipeLoader();
        fermentationBarrelRecipeLoader = components.fermentationBarrelRecipeLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        coreItemSourceService = components.coreItemSourceService();
        craftEngineBlockBridge = components.craftEngineBlockBridge();
        itemsAdderBlockBridge = components.itemsAdderBlockBridge();
        nexoBlockBridge = components.nexoBlockBridge();
        oraxenBlockBridge = components.oraxenBlockBridge();
        settingsService = components.settingsService();
        blockMatcher = components.blockMatcher();
        stationStateStore = components.stationStateStore();
        recipeService = components.recipeService();
        rewardService = components.rewardService();
        completionCoordinator = components.completionCoordinator();
        inspectService = components.inspectService();
        displayService = components.displayService();
        textDisplayService = components.textDisplayService();
        effectService = new CookingEffectService(this, settingsService);
        choppingBoardRuntimeService = components.choppingBoardRuntimeService();
        wokRuntimeService = components.wokRuntimeService();
        grinderRuntimeService = components.grinderRuntimeService();
        steamerRuntimeService = components.steamerRuntimeService();
        ovenRuntimeService = components.ovenRuntimeService();
        juicerRuntimeService = components.juicerRuntimeService();
        fermentationBarrelRuntimeService = components.fermentationBarrelRuntimeService();
        nutritionTypeLoader = components.nutritionTypeLoader();
        nutritionTypeRegistry = components.nutritionTypeRegistry();
        nutritionDataStore = components.nutritionDataStore();
        nutritionService = components.nutritionService();
        stationTracker = new CookingStationTracker();
        stationListener = new CookingStationListener(choppingBoardRuntimeService, wokRuntimeService, grinderRuntimeService, steamerRuntimeService, ovenRuntimeService, juicerRuntimeService, fermentationBarrelRuntimeService, blockMatcher, settingsService);
        stationLocator = new CookingStationLocator(this);
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "ecooking command",
                java.util.List.of("ec"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakicooking.use", commandRouter, commandRouter)
        );
    }

    private void registerEventHandlers() {
        if (stationListener == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(stationListener, this);
        if (stationStateStore != null) {
            getServer().getPluginManager().registerEvents(new CookingStationStorageListener(this), this);
        }
        if (stationTracker != null) {
            getServer().getPluginManager().registerEvents(stationTracker, this);
        }
        if (steamerRuntimeService != null) {
            getServer().getPluginManager().registerEvents(steamerRuntimeService, this);
        }
        if (ovenRuntimeService != null) {
            getServer().getPluginManager().registerEvents(ovenRuntimeService, this);
        }
        if (juicerRuntimeService != null) {
            getServer().getPluginManager().registerEvents(juicerRuntimeService, this);
        }
        if (fermentationBarrelRuntimeService != null) {
            getServer().getPluginManager().registerEvents(fermentationBarrelRuntimeService, this);
        }
        registerCraftEngineEventHandlers();
        registerItemsAdderEventHandlers();
        registerNexoEventHandlers();
        registerOraxenEventHandlers();
        registerNutritionEventHandlers();
    }

    private void registerNutritionEventHandlers() {
        if (nutritionService == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(new NutritionConsumeListener(this), this);
        NutritionPlayerDataListener playerDataListener = new NutritionPlayerDataListener(this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        registerMmoItemsNutritionHandler();
        registerNeigeItemsNutritionHandler();

        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            playerDataListener.ensureSession(online);
        }
    }

    private void registerMmoItemsNutritionHandler() {
        if (!getServer().getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new MmoItemsNutritionListener(this), this);
            messageService.info("console.nutrition_bridge_ready", Map.of("provider", "MMOItems"));
        } catch (LinkageError exception) {
            messageService.warning("console.nutrition_bridge_unavailable", Map.of("provider", "MMOItems", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerNeigeItemsNutritionHandler() {
        if (!getServer().getPluginManager().isPluginEnabled("NeigeItems")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new NeigeItemsNutritionListener(this), this);
            messageService.info("console.nutrition_bridge_ready", Map.of("provider", "NeigeItems"));
        } catch (LinkageError exception) {
            messageService.warning("console.nutrition_bridge_unavailable", Map.of("provider", "NeigeItems", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerActions() {
        stageRegistrar = new CookingStageRegistrar(this);
        stageRegistrar.register();
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new CookingConfigPrecheckContributor(this));
    }

    private void registerPublicApiService() {
        EmakiCookingApi.install(cookingApiBridge);
    }

    private void registerCraftEngineEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("CraftEngine")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new CraftEngineCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "CraftEngine"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "CraftEngine", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerItemsAdderEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new ItemsAdderCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "ItemsAdder"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "ItemsAdder", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerNexoEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("Nexo")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new NexoCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "Nexo"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "Nexo", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerOraxenEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("Oraxen")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new OraxenCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "Oraxen"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "Oraxen", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerPlaceholderExpansion() {
        if (placeholderExpansion != null || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new CookingPlaceholderExpansion(this);
        placeholderExpansion.register();
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public ChoppingBoardRecipeLoader choppingBoardRecipeLoader() {
        return choppingBoardRecipeLoader;
    }

    public WokRecipeLoader wokRecipeLoader() {
        return wokRecipeLoader;
    }

    public GrinderRecipeLoader grinderRecipeLoader() {
        return grinderRecipeLoader;
    }

    public SteamerRecipeLoader steamerRecipeLoader() {
        return steamerRecipeLoader;
    }

    public OvenRecipeLoader ovenRecipeLoader() {
        return ovenRecipeLoader;
    }

    public JuicerRecipeLoader juicerRecipeLoader() {
        return juicerRecipeLoader;
    }

    public FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader() {
        return fermentationBarrelRecipeLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    /**
     * {@return the runner used to execute configured pipeline lines}
     *
     * <p>Created on demand rather than cached: it reads the live engine per call, so a CoreLib reload
     * needs no action here.</p>
     */
    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public boolean publicApiReady() {
        return publicApiReady;
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public CraftEngineBlockBridge craftEngineBlockBridge() {
        return craftEngineBlockBridge;
    }

    public CustomBlockBridge itemsAdderBlockBridge() {
        return itemsAdderBlockBridge;
    }

    public CustomBlockBridge nexoBlockBridge() {
        return nexoBlockBridge;
    }

    public CustomBlockBridge oraxenBlockBridge() {
        return oraxenBlockBridge;
    }

    public CookingSettingsService settingsService() {
        return settingsService;
    }

    public StationStateStore stationStateStore() {
        return stationStateStore;
    }

    public CookingRecipeService recipeService() {
        return recipeService;
    }

    public CookingRewardService rewardService() {
        return rewardService;
    }

    public CookingInspectService inspectService() {
        return inspectService;
    }

    public CookingTextDisplayService textDisplayService() {
        return textDisplayService;
    }

    public CookingEffectService effectService() {
        return effectService;
    }

    public ChoppingBoardRuntimeService choppingBoardRuntimeService() {
        return choppingBoardRuntimeService;
    }

    public WokRuntimeService wokRuntimeService() {
        return wokRuntimeService;
    }

    public GrinderRuntimeService grinderRuntimeService() {
        return grinderRuntimeService;
    }

    public SteamerRuntimeService steamerRuntimeService() {
        return steamerRuntimeService;
    }

    public OvenRuntimeService ovenRuntimeService() {
        return ovenRuntimeService;
    }

    public JuicerRuntimeService juicerRuntimeService() {
        return juicerRuntimeService;
    }

    public FermentationBarrelRuntimeService fermentationBarrelRuntimeService() {
        return fermentationBarrelRuntimeService;
    }

    public CookingStationLocator stationLocator() {
        return stationLocator;
    }

    public CookingStationTracker stationTracker() {
        return stationTracker;
    }

    public NutritionTypeLoader nutritionTypeLoader() {
        return nutritionTypeLoader;
    }

    public NutritionTypeRegistry nutritionTypeRegistry() {
        return nutritionTypeRegistry;
    }

    public PlayerNutritionDataStore nutritionDataStore() {
        return nutritionDataStore;
    }

    public NutritionService nutritionService() {
        return nutritionService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
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

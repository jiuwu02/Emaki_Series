package emaki.jiuwu.craft.strengthen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.command.PaperCommandAdapter;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.strengthen.action.StrengthenStageRegistrar;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.apiimpl.DefaultEmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.config.StrengthenConfigPrecheckContributor;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixGuiService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixSelectionService;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryProgressService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityPersistenceRetryScheduler;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipeLoader;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.integration.StrengthenItemLayerPreviewLifecycle;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.papi.StrengthenPlaceholderExpansion;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;
import emaki.jiuwu.craft.strengthen.service.StrengthenTransferService;

public final class EmakiStrengthenPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakistrengthen";
    private static final Set<String> DEBUG_MODULES = Set.of("attempt", "state", "gui", "pdc", "pity");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __   __  ______  ______  __  __  ______  __   __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  == \\/\\  ___\\/\\ "-.\\ \\/\\  ___\\/\\__  _\\/\\ \\_\\ \\/\\  ___\\/\\ "-.\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\  __<\\ \\  __\\\\ \\ \\-.  \\ \\ \\__ \\/_/\\ \\/\\ \\  __ \\ \\  __\\\\ \\ \\-.  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\ \\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/ /_/\\/_____/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/\\/_/\\/_____/\\/_/ \\/_/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x3636F5;
    private static final int STARTUP_ASCII_END_COLOR = 0xE02492;
    private static final int BSTATS_PLUGIN_ID = 31769;

    private BStatsRegistration metrics;

    private volatile boolean contentReady;

    private final StrengthenLifecycleCoordinator lifecycleCoordinator = new StrengthenLifecycleCoordinator();
    private final StrengthenItemLayerPreviewLifecycle itemLayerPreviewLifecycle = new StrengthenItemLayerPreviewLifecycle(this);
    private final StrengthenCommandRouter commandRouter = new StrengthenCommandRouter(this);
    private StrengthenItemRefreshListener itemRefreshListener;
    private ItemSourceService coreItemSourceService;
    private DebugCommand debugCommand;
    private final CoreItemFactory coreItemFactory = (source, amount) ->
            coreItemSourceService == null ? null : coreItemSourceService.createItem(source, amount);

    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private StrengthenRecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private StrengthenAttributeBridge pdcAttributeGateway;
    private StrengthenAttributeBridge affixAttributeGateway;
    private StrengthenRecipeResolver recipeResolver;
    private ChanceCalculator chanceCalculator;
    private StrengthenEconomyService economyService;
    private StrengthenSnapshotBuilder snapshotBuilder;
    private StrengthenActionCoordinator actionCoordinator;
    private StrengthenAttemptService attemptService;
    private StrengthenTransferService transferService;
    private StrengthenRefreshService refreshService;
    private StrengthenGuiService strengthenGuiService;
    private EnhancementRecipeLoader enhancementRecipeLoader;
    private EnhancementTargetRegistry enhancementTargetRegistry;
    private InMemoryPityStateStore pityStateStore;
    private EnhancementAttemptService enhancementAttemptService;
    private PityPersistenceRetryScheduler pityRetryScheduler;
    private MasteryProgressService masteryProgressService;
    private AffixSelectionService affixSelectionService;
    private AffixGuiService affixGuiService;
    private StrengthenPlaceholderExpansion placeholderExpansion;
    private EmakiStrengthenApi.Bridge strengthenApiBridge;
    private StrengthenStageRegistrar stageRegistrar;

    public EmakiStrengthenPlugin() {
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
        registerConfigPrecheckContributor();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        pityRetryScheduler.start();
        registerApi();
        registerActions();
        registerCommandHandler();
        registerEventHandlers();
        itemLayerPreviewLifecycle.initialize();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        contentReady = false;
        publishAbsent();
        if (pityRetryScheduler != null) {
            pityRetryScheduler.stop();
        }
        ConfigPrecheckLifecycleSupport.unregister("strengthen");
        itemLayerPreviewLifecycle.close();
        lifecycleCoordinator.shutdown(this);
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
        }
        EmakiStrengthenApi.uninstall(strengthenApiBridge);
        strengthenApiBridge = null;
        getServer().getServicesManager().unregisterAll(this);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        contentReady = false;
        publishLoading();
        if (pityRetryScheduler != null) {
            pityRetryScheduler.stop();
        }
        lifecycleCoordinator.reload(this, closeOpenInventories);
        contentReady = true;
        publishReady();
        if (pityRetryScheduler != null) {
            pityRetryScheduler.start();
        }
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        contentReady = false;
        publishLoading();
        if (pityRetryScheduler != null) {
            pityRetryScheduler.stop();
        }
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null)
                .thenRun(() -> {
                    contentReady = true;
                    publishReady();
                    if (pityRetryScheduler != null) {
                        pityRetryScheduler.start();
                    }
                });
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
            getLogger().fine("EmakiStrengthen readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new StrengthenConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(StrengthenRuntimeComponents components) {
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        itemRefreshListener = new StrengthenItemRefreshListener(this, executionDispatcher);
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        coreItemSourceService = components.coreItemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        affixAttributeGateway = components.affixAttributeGateway();
        recipeResolver = components.recipeResolver();
        chanceCalculator = components.chanceCalculator();
        economyService = components.economyService();
        snapshotBuilder = components.snapshotBuilder();
        actionCoordinator = components.actionCoordinator();
        attemptService = components.attemptService();
        transferService = components.transferService();
        refreshService = components.refreshService();
        strengthenGuiService = components.strengthenGuiService();
        enhancementRecipeLoader = components.enhancementRecipeLoader();
        enhancementTargetRegistry = components.enhancementTargetRegistry();
        if (pityRetryScheduler != null) {
            pityRetryScheduler.stop();
        }
        pityStateStore = components.pityStateStore();
        enhancementAttemptService = components.enhancementAttemptService();
        masteryProgressService = components.masteryProgressService();
        pityRetryScheduler = new PityPersistenceRetryScheduler(this);
        affixSelectionService = components.affixSelectionService();
        affixGuiService = components.affixGuiService();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(coreLib().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerServices(components);
    }

    private void registerApi() {
        strengthenApiBridge = new DefaultEmakiStrengthenApi(this);
        EmakiStrengthenApi.install(strengthenApiBridge);
    }

    private void registerActions() {
        stageRegistrar = new StrengthenStageRegistrar(this);
        stageRegistrar.register();
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakistrengthen command",
                List.of("estrengthen"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakistrengthen.use", commandRouter, commandRouter)
        );
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(strengthenGuiService, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new StrengthenPlaceholderExpansion(this, attemptService);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public StrengthenRecipeLoader recipeLoader() {
        return recipeLoader;
    }

    public EnhancementRecipeLoader enhancementRecipeLoader() {
        return enhancementRecipeLoader;
    }

    public EnhancementTargetRegistry enhancementTargetRegistry() {
        return enhancementTargetRegistry;
    }

    public PityPersistenceRetryScheduler pityRetryScheduler() {
        return pityRetryScheduler;
    }

    public MasteryProgressService masteryProgressService() {
        return masteryProgressService;
    }

    public InMemoryPityStateStore pityStateStore() {
        return pityStateStore;
    }

    public EnhancementAttemptService enhancementAttemptService() {
        return enhancementAttemptService;
    }

    public AffixSelectionService affixSelectionService() {
        return affixSelectionService;
    }

    public AffixGuiService affixGuiService() {
        return affixGuiService;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
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

    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public GuiService guiService() {
        return guiService;
    }

    public StrengthenAttributeBridge pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    StrengthenAttributeBridge affixAttributeGateway() {
        return affixAttributeGateway;
    }

    public StrengthenRecipeResolver recipeResolver() {
        return recipeResolver;
    }

    public ChanceCalculator chanceCalculator() {
        return chanceCalculator;
    }

    public StrengthenEconomyService economyService() {
        return economyService;
    }

    public StrengthenSnapshotBuilder snapshotBuilder() {
        return snapshotBuilder;
    }

    public StrengthenActionCoordinator actionCoordinator() {
        return actionCoordinator;
    }

    public StrengthenAttemptService attemptService() {
        return attemptService;
    }

    public StrengthenTransferService transferService() {
        return transferService;
    }

    public StrengthenRefreshService refreshService() {
        return refreshService;
    }

    public StrengthenGuiService strengthenGuiService() {
        return strengthenGuiService;
    }

    public CoreItemFactory coreItemFactory() {
        return coreItemFactory;
    }

    @FunctionalInterface
    public interface CoreItemFactory {

        ItemStack create(ItemSourceRef source, int amount);
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}

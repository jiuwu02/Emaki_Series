package emaki.jiuwu.craft.strengthen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
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
    private static final Set<String> DEBUG_MODULES = Set.of("attempt", "state", "gui", "pdc");

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
    // "Data is loaded", not "services exist": the services are non-null from initialize() onward, so a
    // service null-check reported ready for the whole duration of a reload. Unrelated to the
    // accept/freeze gate on StrengthenAttemptService, which answers a different question.
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
    private StrengthenRecipeResolver recipeResolver;
    private ChanceCalculator chanceCalculator;
    private StrengthenEconomyService economyService;
    private StrengthenSnapshotBuilder snapshotBuilder;
    private StrengthenActionCoordinator actionCoordinator;
    private StrengthenAttemptService attemptService;
    private StrengthenTransferService transferService;
    private StrengthenRefreshService refreshService;
    private StrengthenGuiService strengthenGuiService;
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
        lifecycleCoordinator.reload(this, closeOpenInventories);
        contentReady = true;
        publishReady();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        contentReady = false;
        publishLoading();
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null)
                .thenRun(() -> {
                    contentReady = true;
                    publishReady();
                });
    }

    /**
     * {@return whether this module's configured content has finished loading}
     *
     * <p>Read by the API bridge so {@code status()} means "data is loaded" rather than "the services
     * were constructed". Deliberately separate from {@code attemptService().accepting()}, which is the
     * shutdown gate for new requests and says nothing about whether the recipe table is loaded.</p>
     */
    public boolean contentReady() {
        return contentReady;
    }

    /**
     * Publishes "my data is loaded" to CoreLib's readiness registry.
     *
     * <p>This module's flag is set in a plain method body with no lock held. In particular it is not
     * guarded by {@code lifecycleMonitor}, which belongs to the accept/freeze gate, so no monitor is
     * held while the waiting third-party callbacks run synchronously here.</p>
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
        recipeResolver = components.recipeResolver();
        chanceCalculator = components.chanceCalculator();
        economyService = components.economyService();
        snapshotBuilder = components.snapshotBuilder();
        actionCoordinator = components.actionCoordinator();
        attemptService = components.attemptService();
        transferService = components.transferService();
        refreshService = components.refreshService();
        strengthenGuiService = components.strengthenGuiService();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
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
                java.util.List.of("estrengthen"),
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

    public GuiService guiService() {
        return guiService;
    }

    public StrengthenAttributeBridge pdcAttributeGateway() {
        return pdcAttributeGateway;
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

    /** Creates a stack from a parsed item source; used by economy payouts and command item resolution. */
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

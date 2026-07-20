package emaki.jiuwu.craft.strengthen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.item.preview.ItemLayerPreviewRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.strengthen.action.StrengthenActionRegistrar;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.config.StrengthenConfigPrecheckContributor;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.papi.StrengthenPlaceholderExpansion;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenItemLayerPreviewProvider;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;
import emaki.jiuwu.craft.strengthen.script.JavaScriptStrengthenChanceRuleRegistry;
import emaki.jiuwu.craft.strengthen.script.JavaScriptStrengthenResultHookRegistry;

public final class EmakiStrengthenPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakistrengthen";
    private static final Set<String> DEBUG_MODULES = Set.of("attempt", "state", "gui", "script");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __   __  ______  ______  __  __  ______  __   __    
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  == \\/\\  ___\\/\\ "-.\\ \\/\\  ___\\/\\__  _\\/\\ \\_\\ \\/\\  ___\\/\\ "-.\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\  __<\\ \\  __\\\\ \\ \\-.  \\ \\ \\__ \\/_/\\ \\/\\ \\  __ \\ \\  __\\\\ \\ \\-.  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\ \\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/ /_/\\/_____/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/\\/_/\\/_____/\\/_/ \\/_/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xFACC15;
    private static final int STARTUP_ASCII_END_COLOR = 0xF97316;
    private static final int BSTATS_PLUGIN_ID = 31769;

    private BStatsRegistration metrics;

    private final StrengthenLifecycleCoordinator lifecycleCoordinator = new StrengthenLifecycleCoordinator();
    private final StrengthenCommandRouter commandRouter = new StrengthenCommandRouter(this);
    private StrengthenItemRefreshListener itemRefreshListener;
    private ItemSourceService coreItemSourceService;
    private DebugCommand debugCommand;
    private final GuiItemBuilder.ItemFactory coreItemFactory = (source, amount) -> {
        return coreItemSourceService == null ? null : coreItemSourceService.createItem(source, amount);
    };

    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private StrengthenRecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private PdcAttributeGateway pdcAttributeGateway;
    private StrengthenRecipeResolver recipeResolver;
    private ChanceCalculator chanceCalculator;
    private StrengthenEconomyService economyService;
    private StrengthenSnapshotBuilder snapshotBuilder;
    private StrengthenActionCoordinator actionCoordinator;
    private StrengthenAttemptService attemptService;
    private StrengthenRefreshService refreshService;
    private StrengthenGuiService strengthenGuiService;
    private JavaScriptStrengthenChanceRuleRegistry javaScriptChanceRuleRegistry;
    private JavaScriptStrengthenResultHookRegistry javaScriptResultHookRegistry;
    private StrengthenPlaceholderExpansion placeholderExpansion;
    private final EmakiStrengthenApi.Bridge strengthenApiBridge = new EmakiStrengthenApi.Bridge() {
        @Override
        public boolean canStrengthen(@Nullable ItemStack itemStack) {
            return attemptService != null && attemptService.canStrengthen(itemStack);
        }

        @Override
        public @NotNull StrengthenState readState(@Nullable ItemStack itemStack) {
            return attemptService.readState(itemStack);
        }

        @Override
        public @NotNull AttemptPreview preview(@Nullable Player player, @Nullable AttemptContext context) {
            return attemptService.preview(player, context);
        }

        @Override
        public @NotNull AttemptResult attempt(@Nullable Player player, @Nullable AttemptContext context) {
            return attemptService.attempt(player, context);
        }

        @Override
        public @Nullable ItemStack rebuild(@Nullable ItemStack itemStack) {
            return attemptService.rebuild(itemStack);
        }
    };

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
        ItemLayerPreviewRegistry.register(this, new StrengthenItemLayerPreviewProvider(this));
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        ConfigPrecheckLifecycleSupport.unregister("strengthen");
        lifecycleCoordinator.shutdown(this);
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        EmakiCoreLibPlugin coreLib = coreLib();
        if (coreLib != null && coreLib.actionRegistry() != null) {
            coreLib.actionRegistry().unregisterAll(this);
        }
        ItemLayerPreviewRegistry.unregister(this);
        EmakiStrengthenApi.uninstall(strengthenApiBridge);
        getServer().getServicesManager().unregisterAll(this);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
        logConfigPrecheckReport();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null)
                .thenRun(this::logConfigPrecheckReport);
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "strengthen");
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
        refreshService = components.refreshService();
        strengthenGuiService = components.strengthenGuiService();
        javaScriptChanceRuleRegistry = new JavaScriptStrengthenChanceRuleRegistry(this);
        javaScriptResultHookRegistry = new JavaScriptStrengthenResultHookRegistry(this);
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerApi() {
        EmakiStrengthenApi.install(strengthenApiBridge);
    }

    private void registerActions() {
        new StrengthenActionRegistrar(this).register(coreLib().actionRegistry());
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

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public GuiService guiService() {
        return guiService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
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

    public StrengthenRefreshService refreshService() {
        return refreshService;
    }

    public StrengthenGuiService strengthenGuiService() {
        return strengthenGuiService;
    }

    public JavaScriptStrengthenChanceRuleRegistry javaScriptChanceRuleRegistry() {
        return javaScriptChanceRuleRegistry;
    }

    public JavaScriptStrengthenResultHookRegistry javaScriptResultHookRegistry() {
        return javaScriptResultHookRegistry;
    }

    public GuiItemBuilder.ItemFactory coreItemFactory() {
        return coreItemFactory;
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

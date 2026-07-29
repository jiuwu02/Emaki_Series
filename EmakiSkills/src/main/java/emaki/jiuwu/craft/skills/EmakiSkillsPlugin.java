package emaki.jiuwu.craft.skills;

import java.util.Map;
import java.util.Set;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.skills.action.SkillsActionRegistrar;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.ExternalManaBridge;
import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.config.SkillsConfigPrecheckContributor;
import emaki.jiuwu.craft.skills.gui.SkillsGuiService;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.ManualSkillSourceService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.script.SkillScriptCastService;
import emaki.jiuwu.craft.skills.script.SkillScriptExecutor;
import emaki.jiuwu.craft.skills.script.SkillVariableResolver;
import emaki.jiuwu.craft.skills.trigger.DefaultTriggerDispatcher;
import emaki.jiuwu.craft.skills.trigger.DropTriggerSource;
import emaki.jiuwu.craft.skills.trigger.HotbarTriggerSource;
import emaki.jiuwu.craft.skills.trigger.InteractTriggerSource;
import emaki.jiuwu.craft.skills.trigger.PassiveTriggerDispatcher;
import emaki.jiuwu.craft.skills.trigger.PassiveTriggerSource;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;
import emaki.jiuwu.craft.skills.listener.CastModeKeyListener;
import emaki.jiuwu.craft.skills.listener.PlayerJoinQuitListener;
import emaki.jiuwu.craft.skills.papi.SkillsPlaceholderExpansion;

import org.bukkit.Bukkit;

public final class EmakiSkillsPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakiskills";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  __  __   __  __      __      ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\ \\/ /  /\\ \\/\\ \\    /\\ \\    /\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\___\\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x38BDF8;
    private static final int STARTUP_ASCII_END_COLOR = 0x8B5CF6;
    private static final int BSTATS_PLUGIN_ID = 31768;

    private BStatsRegistration metrics;

    private static final Set<String> DEBUG_MODULES = Set.of("cast", "unlock", "upgrade", "slot");

    private final SkillsLifecycleCoordinator lifecycleCoordinator = new SkillsLifecycleCoordinator();
    private final SkillsCommandRouter commandRouter = new SkillsCommandRouter(this);
    private DebugCommand debugCommand;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private LanguageLoader languageLoader;
    private SkillDefinitionLoader skillDefinitionLoader;
    private LocalResourceDefinitionLoader localResourceDefinitionLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private EquipmentSkillCollector equipmentSkillCollector;
    private SkillSourceRegistry skillSourceRegistry;
    private TriggerRegistry triggerRegistry;
    private TriggerConflictResolver triggerConflictResolver;
    private SkillRegistryService skillRegistryService;
    private PlayerSkillDataStore playerSkillDataStore;
    private ManualSkillSourceService manualSkillSourceService;
    private PlayerSkillStateService playerSkillStateService;
    private SkillLevelService skillLevelService;
    private SkillParameterResolver skillParameterResolver;
    private SkillVariableResolver skillVariableResolver;
    private SkillScriptActionRegistry skillScriptActionRegistry;
    private SkillScriptExecutor skillScriptExecutor;
    private SkillScriptCastService skillScriptCastService;
    private SkillUpgradeService skillUpgradeService;
    private CastModeService castModeService;
    private CastAttemptService castAttemptService;
    private MythicSkillCastService mythicSkillCastService;
    private ActionBarService actionBarService;
    private SkillsGuiService skillsGuiService;
    private EaBridge eaBridge;
    private ExternalManaBridge externalManaBridge;
    private MythicBridge mythicBridge;
    private SkillsPlaceholderExpansion placeholderExpansion;
    private DefaultTriggerDispatcher triggerDispatcher;
    private PassiveTriggerDispatcher passiveTriggerDispatcher;
    private PassiveTriggerSource passiveTriggerSource;
    private final EmakiSkillsApi.Bridge skillsApiBridge = new EmakiSkillsApi.Bridge() {
        @Override
        public SkillScriptActionRegistry scriptActionRegistry() {
            return skillScriptActionRegistry;
        }
    };

    public EmakiSkillsPlugin() {
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
        if (languageLoader != null) {
            languageLoader.load();
            languageLoader.setLanguage(appConfig().language());
        }
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerCommandHandler();
        registerEventHandlers();
        registerCoreLibActions();
        registerPublicApi();
        ensurePlaceholderExpansion();
        if (actionBarService != null) {
            actionBarService.startRefreshTask();
        }
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        unregisterCoreLibActions();
        ConfigPrecheckLifecycleSupport.unregister("skills");
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (skillScriptActionRegistry != null) {
            skillScriptActionRegistry.unregisterAll(this);
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        EmakiSkillsApi.uninstall(skillsApiBridge);
        if (passiveTriggerSource != null) {
            passiveTriggerSource.stop();
            passiveTriggerSource = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
        logConfigPrecheckReport();
    }

    public java.util.concurrent.CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories, java.util.function.Consumer<String> progressListener) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, progressListener)
                .thenRun(() -> logConfigPrecheckReport());
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "skills");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new SkillsConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(SkillsRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        languageLoader = components.languageLoader();
        skillDefinitionLoader = components.skillDefinitionLoader();
        localResourceDefinitionLoader = components.localResourceDefinitionLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        equipmentSkillCollector = components.equipmentSkillCollector();
        skillSourceRegistry = components.skillSourceRegistry();
        triggerRegistry = components.triggerRegistry();
        triggerConflictResolver = components.triggerConflictResolver();
        skillRegistryService = components.skillRegistryService();
        playerSkillDataStore = components.playerSkillDataStore();
        manualSkillSourceService = components.manualSkillSourceService();
        playerSkillStateService = components.playerSkillStateService();
        skillLevelService = components.skillLevelService();
        skillParameterResolver = components.skillParameterResolver();
        skillVariableResolver = components.skillVariableResolver();
        skillScriptActionRegistry = components.skillScriptActionRegistry();
        skillScriptExecutor = components.skillScriptExecutor();
        skillScriptCastService = components.skillScriptCastService();
        skillUpgradeService = components.skillUpgradeService();
        castModeService = components.castModeService();
        castAttemptService = components.castAttemptService();
        mythicSkillCastService = components.mythicSkillCastService();
        actionBarService = components.actionBarService();
        skillsGuiService = components.skillsGuiService();
        eaBridge = components.eaBridge();
        externalManaBridge = components.externalManaBridge();
        mythicBridge = components.mythicBridge();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakiskills command",
                java.util.List.of("eskills"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakiskills.use", commandRouter, commandRouter)
        );
    }

    private void registerEventHandlers() {
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }

        triggerDispatcher = new DefaultTriggerDispatcher(
                this, castModeService, triggerRegistry, playerSkillDataStore,
                playerSkillStateService, equipmentSkillCollector,
                castAttemptService, this::appConfig, messageService, executionDispatcher);
        new InteractTriggerSource().register(this, triggerDispatcher);
        new DropTriggerSource().register(this, triggerDispatcher);
        new HotbarTriggerSource().register(this, triggerDispatcher);

        passiveTriggerDispatcher = new PassiveTriggerDispatcher(
                triggerRegistry, playerSkillStateService, castAttemptService);
        passiveTriggerSource = new PassiveTriggerSource(this::appConfig);
        passiveTriggerSource.register(this, passiveTriggerDispatcher, executionDispatcher);

        getServer().getPluginManager().registerEvents(
                new CastModeKeyListener(castModeService, actionBarService, messageService),
                this);

        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerSkillDataStore,
                        castModeService, actionBarService, this::appConfig),
                this);
    }

    private void registerCoreLibActions() {
        SkillsActionRegistrar.registerAll(coreLib().actionRegistry(), this, mythicSkillCastService,
                playerSkillStateService, skillLevelService, skillUpgradeService, playerSkillDataStore,
                manualSkillSourceService);
    }

    private void registerPublicApi() {
        EmakiSkillsApi.install(skillsApiBridge);
        if (skillScriptActionRegistry != null) {
            getServer().getServicesManager().register(SkillScriptActionRegistry.class, skillScriptActionRegistry, this,
                    org.bukkit.plugin.ServicePriority.Normal);
        }
    }

    private void unregisterCoreLibActions() {
        SkillsActionRegistrar.unregisterAll(coreLib().actionRegistry(), this);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new SkillsPlaceholderExpansion(
                this, playerSkillDataStore, skillRegistryService, localResourceDefinitionLoader);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public SkillDefinitionLoader skillDefinitionLoader() {
        return skillDefinitionLoader;
    }

    public LocalResourceDefinitionLoader localResourceDefinitionLoader() {
        return localResourceDefinitionLoader;
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

    public GuiService guiService() {
        return guiService;
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

    public EquipmentSkillCollector equipmentSkillCollector() {
        return equipmentSkillCollector;
    }

    public SkillSourceRegistry skillSourceRegistry() {
        return skillSourceRegistry;
    }

    public TriggerRegistry triggerRegistry() {
        return triggerRegistry;
    }

    public TriggerConflictResolver triggerConflictResolver() {
        return triggerConflictResolver;
    }

    public SkillRegistryService skillRegistryService() {
        return skillRegistryService;
    }

    public PlayerSkillDataStore playerSkillDataStore() {
        return playerSkillDataStore;
    }

    public ManualSkillSourceService manualSkillSourceService() {
        return manualSkillSourceService;
    }

    public PlayerSkillStateService playerSkillStateService() {
        return playerSkillStateService;
    }

    public SkillLevelService skillLevelService() {
        return skillLevelService;
    }

    public SkillParameterResolver skillParameterResolver() {
        return skillParameterResolver;
    }

    public SkillVariableResolver skillVariableResolver() {
        return skillVariableResolver;
    }

    public SkillScriptActionRegistry skillScriptActionRegistry() {
        return skillScriptActionRegistry;
    }

    public SkillScriptExecutor skillScriptExecutor() {
        return skillScriptExecutor;
    }

    public SkillScriptCastService skillScriptCastService() {
        return skillScriptCastService;
    }

    public EmakiSkillsApi.Bridge emakiSkillsApi() {
        return skillsApiBridge;
    }

    public SkillUpgradeService skillUpgradeService() {
        return skillUpgradeService;
    }

    public CastModeService castModeService() {
        return castModeService;
    }

    public CastAttemptService castAttemptService() {
        return castAttemptService;
    }

    public MythicSkillCastService mythicSkillCastService() {
        return mythicSkillCastService;
    }

    public ActionBarService actionBarService() {
        return actionBarService;
    }

    public SkillsGuiService skillsGuiService() {
        return skillsGuiService;
    }

    public EaBridge eaBridge() {
        return eaBridge;
    }

    public ExternalManaBridge externalManaBridge() {
        return externalManaBridge;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
    }

    public SkillsPlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }

    public DefaultTriggerDispatcher triggerDispatcher() {
        return triggerDispatcher;
    }

    public PassiveTriggerDispatcher passiveTriggerDispatcher() {
        return passiveTriggerDispatcher;
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

package emaki.jiuwu.craft.skills;

import java.util.Map;
import java.util.Set;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.skills.action.CastSkillAction;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.gui.SkillsGuiService;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
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

    private static final Set<String> DEBUG_MODULES = Set.of("cast", "unlock", "upgrade", "slot");

    private final SkillsLifecycleCoordinator lifecycleCoordinator = new SkillsLifecycleCoordinator();
    private final SkillsCommandRouter commandRouter = new SkillsCommandRouter(this);
    private DebugCommand debugCommand;

    private YamlConfigLoader<AppConfig> appConfigLoader;
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
    private PlayerSkillStateService playerSkillStateService;
    private SkillLevelService skillLevelService;
    private SkillParameterResolver skillParameterResolver;
    private SkillVariableResolver skillVariableResolver;
    private SkillScriptActionRegistry skillScriptActionRegistry;
    private SkillScriptExecutor skillScriptExecutor;
    private SkillScriptCastService skillScriptCastService;
    private EmakiSkillsApi emakiSkillsApi;
    private SkillUpgradeService skillUpgradeService;
    private CastModeService castModeService;
    private CastAttemptService castAttemptService;
    private MythicSkillCastService mythicSkillCastService;
    private ActionBarService actionBarService;
    private SkillsGuiService skillsGuiService;
    private EaBridge eaBridge;
    private MythicBridge mythicBridge;
    private SkillsPlaceholderExpansion placeholderExpansion;
    private DefaultTriggerDispatcher triggerDispatcher;
    private PassiveTriggerDispatcher passiveTriggerDispatcher;

    public EmakiSkillsPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
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
        registerWebConsole();
        registerPublicApi();
        ensurePlaceholderExpansion();
        if (actionBarService != null) {
            actionBarService.startRefreshTask();
        }
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        unregisterCoreLibActions();
        WebConsoleRegistry.unregisterModule(this);
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (skillScriptActionRegistry != null) {
            skillScriptActionRegistry.unregisterAll(this);
        }
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
    }

    public java.util.concurrent.CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories, java.util.function.Consumer<String> progressListener) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, progressListener);
    }

    private void applyRuntimeComponents(SkillsRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
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
        playerSkillStateService = components.playerSkillStateService();
        skillLevelService = components.skillLevelService();
        skillParameterResolver = components.skillParameterResolver();
        skillVariableResolver = components.skillVariableResolver();
        skillScriptActionRegistry = components.skillScriptActionRegistry();
        skillScriptExecutor = components.skillScriptExecutor();
        skillScriptCastService = components.skillScriptCastService();
        emakiSkillsApi = components.emakiSkillsApi();
        skillUpgradeService = components.skillUpgradeService();
        castModeService = components.castModeService();
        castAttemptService = components.castAttemptService();
        mythicSkillCastService = components.mythicSkillCastService();
        actionBarService = components.actionBarService();
        skillsGuiService = components.skillsGuiService();
        eaBridge = components.eaBridge();
        mythicBridge = components.mythicBridge();
        setDebugLogger(new DebugLogger(getLogger(), languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerCommandHandler() {
        PluginCommand pluginCommand = getCommand(ROOT_COMMAND);
        if (pluginCommand == null) {
            return;
        }
        pluginCommand.setExecutor(commandRouter);
        pluginCommand.setTabCompleter(commandRouter);
    }

    private void registerEventHandlers() {
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }

        triggerDispatcher = new DefaultTriggerDispatcher(
                castModeService, triggerRegistry, playerSkillDataStore,
                castAttemptService, this::appConfig, messageService);
        new InteractTriggerSource().register(this, triggerDispatcher);
        new DropTriggerSource().register(this, triggerDispatcher);
        new HotbarTriggerSource().register(this, triggerDispatcher);

        passiveTriggerDispatcher = new PassiveTriggerDispatcher(
                triggerRegistry, playerSkillStateService, castAttemptService);
        new PassiveTriggerSource(this::appConfig).register(this, passiveTriggerDispatcher);

        getServer().getPluginManager().registerEvents(
                new CastModeKeyListener(castModeService, actionBarService, messageService),
                this);

        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerSkillDataStore,
                        castModeService, actionBarService, this::appConfig),
                this);
    }

    private void registerCoreLibActions() {
        coreLib().actionRegistry().register(new CastSkillAction(mythicSkillCastService));
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
    }

    private void registerPublicApi() {
        if (emakiSkillsApi == null) {
            return;
        }
        getServer().getServicesManager().register(EmakiSkillsApi.class, emakiSkillsApi, this,
                org.bukkit.plugin.ServicePriority.Normal);
        if (skillScriptActionRegistry != null) {
            getServer().getServicesManager().register(SkillScriptActionRegistry.class, skillScriptActionRegistry, this,
                    org.bukkit.plugin.ServicePriority.Normal);
        }
    }

    private void unregisterCoreLibActions() {
        coreLib().actionRegistry().unregister("castskill");
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

    public EmakiSkillsApi emakiSkillsApi() {
        return emakiSkillsApi;
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
}

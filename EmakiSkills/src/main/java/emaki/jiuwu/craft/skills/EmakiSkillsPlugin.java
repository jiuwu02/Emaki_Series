package emaki.jiuwu.craft.skills;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
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
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.trigger.DefaultTriggerDispatcher;
import emaki.jiuwu.craft.skills.trigger.DropTriggerSource;
import emaki.jiuwu.craft.skills.trigger.HotbarTriggerSource;
import emaki.jiuwu.craft.skills.trigger.InteractTriggerSource;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;
import emaki.jiuwu.craft.skills.listener.PlayerJoinQuitListener;

public final class EmakiSkillsPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakiskills";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  __  __   __  __      __      ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\ \\/ /  /\\ \\/\\ \\    /\\ \\    /\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\___\\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/
""";

    private final SkillsLifecycleCoordinator lifecycleCoordinator = new SkillsLifecycleCoordinator();
    private final SkillsCommandRouter commandRouter = new SkillsCommandRouter(this);

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
    private CastModeService castModeService;
    private CastAttemptService castAttemptService;
    private MythicSkillCastService mythicSkillCastService;
    private ActionBarService actionBarService;
    private SkillsGuiService skillsGuiService;
    private EaBridge eaBridge;
    private MythicBridge mythicBridge;

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
        if (actionBarService != null) {
            actionBarService.startRefreshTask();
        }
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
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
        castModeService = components.castModeService();
        castAttemptService = components.castAttemptService();
        mythicSkillCastService = components.mythicSkillCastService();
        actionBarService = components.actionBarService();
        skillsGuiService = components.skillsGuiService();
        eaBridge = components.eaBridge();
        mythicBridge = components.mythicBridge();
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

        // Register trigger sources
        DefaultTriggerDispatcher dispatcher = new DefaultTriggerDispatcher(
                castModeService, triggerRegistry, playerSkillDataStore,
                castAttemptService, this::appConfig, messageService);
        new InteractTriggerSource().register(this, dispatcher);
        new DropTriggerSource().register(this, dispatcher);
        new HotbarTriggerSource().register(this, dispatcher);

        // Register player join/quit listener
        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerSkillDataStore,
                        castModeService, actionBarService, this::appConfig),
                this);
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
}

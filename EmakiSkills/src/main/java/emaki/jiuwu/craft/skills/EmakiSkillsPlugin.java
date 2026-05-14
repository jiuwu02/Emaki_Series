package emaki.jiuwu.craft.skills;

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
    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M19 8a8 8 0 110 16 8 8 0 010-16zM19 11v5l4 3M19 2v4M19 32v4M7 19H3M35 19h-4M9 9l3 3M29 9l-3 3M13 26l-3 3M25 26l3 3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

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

        // Register trigger sources — 存为字段以便 reload 时可追踪
        triggerDispatcher = new DefaultTriggerDispatcher(
                castModeService, triggerRegistry, playerSkillDataStore,
                castAttemptService, this::appConfig, messageService);
        new InteractTriggerSource().register(this, triggerDispatcher);
        new DropTriggerSource().register(this, triggerDispatcher);
        new HotbarTriggerSource().register(this, triggerDispatcher);

        passiveTriggerDispatcher = new PassiveTriggerDispatcher(
                triggerRegistry, playerSkillStateService, castAttemptService);
        new PassiveTriggerSource(this::appConfig).register(this, passiveTriggerDispatcher);

        // Register fixed F-key cast mode listener
        getServer().getPluginManager().registerEvents(
                new CastModeKeyListener(castModeService, actionBarService, messageService),
                this);

        // Register player join/quit listener
        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerSkillDataStore,
                        castModeService, actionBarService, this::appConfig),
                this);
    }

    private void registerCoreLibActions() {
        coreLib().actionRegistry().register(new CastSkillAction(mythicSkillCastService));
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(getName(), "Skills 技能", "槽位、施法模式、触发器", "skills", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(getName(), "技能系统配置", "config.yml", "技能系统主配置，包含触发器、施法模式、资源和升级设置。");
        WebConsoleRegistry.registerGuiFile(getName(), "技能 GUI 模板", "gui/**/*.yml", "技能面板与触发器选择 GUI 模板文件。");
        WebConsoleRegistry.registerCommonConfigComments(getName());

        // slots
        WebConsoleRegistry.registerNodeComment(getName(), "slots", "技能槽", "技能槽位数量与分配配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "slots.default_count", "默认槽数", "玩家默认可用的技能槽数量。", "number");

        // cast_mode
        WebConsoleRegistry.registerNodeComment(getName(), "cast_mode", "施法模式", "施法模式切换、快捷键和状态恢复配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "cast_mode.entry_key", "切换按键", "进入/退出施法模式的按键绑定。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "cast_mode.restore_last_state_on_join", "恢复状态", "重新登录后是否恢复上次的施法模式状态。", "boolean");

        // cast_timing
        WebConsoleRegistry.registerNodeComment(getName(), "cast_timing", "施法时序", "施法全局延迟与冷却配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "cast_timing.forced_global_cast_delay_ticks", "全局延迟", "任意技能施放后的全局冷却 tick 数。", "number");

        // actionbar
        WebConsoleRegistry.registerNodeComment(getName(), "actionbar", "ActionBar", "ActionBar 技能状态显示配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "actionbar.enabled", "启用显示", "是否启用 ActionBar 技能状态显示。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "actionbar.refresh_interval_ticks", "刷新间隔", "ActionBar 内容刷新间隔 tick 数。", "number");
        WebConsoleRegistry.registerNodeComment(getName(), "actionbar.template_cast_mode", "施法模板", "施法模式下 ActionBar 显示模板。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "actionbar.template_idle", "待机模板", "非施法模式下 ActionBar 显示模板。", "text");

        // script_engine
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine", "脚本引擎", "原生技能脚本引擎配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine.enabled", "启用引擎", "是否启用原生脚本引擎。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine.default_mode", "默认模式", "脚本引擎的默认执行模式。", "enum:native,mythic,hybrid");
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine.stop_on_failure", "失败停止", "脚本执行失败时是否停止后续动作。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine.max_lines_per_phase", "阶段行数", "每个脚本阶段允许的最大行数。", "number");
        WebConsoleRegistry.registerNodeComment(getName(), "script_engine.max_targets_per_action", "目标上限", "每个动作允许的最大目标数量。", "number");

        // triggers
        WebConsoleRegistry.registerNodeComment(getName(), "triggers", "主动触发器", "左键、右键、Shift 与数字键等主动触发器配置。", "object");

        // passive_trigger_settings
        WebConsoleRegistry.registerNodeComment(getName(), "passive_trigger_settings", "被动触发设置", "被动触发器的全局参数配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "passive_trigger_settings.timer_interval_ticks", "定时间隔", "timer 被动触发器的检查间隔 tick 数。", "number");
        WebConsoleRegistry.registerNodeComment(getName(), "passive_trigger_settings.combo_timeout_ticks", "连击超时", "连击判定的超时间隔 tick 数。", "number");

        // passive_triggers
        WebConsoleRegistry.registerNodeComment(getName(), "passive_triggers", "被动触发器", "受击、击杀、定时等被动触发器配置。", "object");
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

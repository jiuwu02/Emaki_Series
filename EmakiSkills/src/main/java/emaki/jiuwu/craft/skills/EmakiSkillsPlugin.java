package emaki.jiuwu.craft.skills;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.skills.action.SkillsStageRegistrar;
import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
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
import emaki.jiuwu.craft.skills.apiimpl.DefaultEmakiSkillsApi;

import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.script.SkillScriptCastService;
import emaki.jiuwu.craft.skills.script.SkillPipelineRuntime;
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

public final class EmakiSkillsPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiskills";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  __  __   __  __      __      ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\ \\/ /  /\\ \\/\\ \\    /\\ \\    /\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\___\\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x3B82F6;
    private static final int STARTUP_ASCII_END_COLOR = 0x7C3AED;
    private static final int BSTATS_PLUGIN_ID = 31768;

    private BStatsRegistration metrics;

    private volatile boolean contentReady;

    private static final Set<String> DEBUG_MODULES = Set.of("cast", "unlock", "upgrade", "slot");

    private final SkillsLifecycleCoordinator lifecycleCoordinator = new SkillsLifecycleCoordinator();
    private final SkillsCommandRouter commandRouter = new SkillsCommandRouter(this);
    private DebugCommand debugCommand;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private EmakiScheduling scheduling;
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
    private SkillPipelineRuntime skillPipelineRuntime;
    private SkillUpgradeService skillUpgradeService;
    private CastModeService castModeService;
    private CastAttemptService castAttemptService;
    private ActionBarService actionBarService;
    private SkillsGuiService skillsGuiService;
    private EaBridge eaBridge;
    private ExternalManaBridge externalManaBridge;
    private MythicBridge mythicBridge;
    private SkillsPlaceholderExpansion placeholderExpansion;
    private DefaultTriggerDispatcher triggerDispatcher;
    private PassiveTriggerDispatcher passiveTriggerDispatcher;
    private PassiveTriggerSource passiveTriggerSource;
    private SkillsStageRegistrar stageRegistrar;
    private final EmakiSkillsApi.Bridge skillsApiBridge =
            new DefaultEmakiSkillsApi(this);

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
        registerActionStages();
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
        contentReady = false;
        publishAbsent();
        ConfigPrecheckLifecycleSupport.unregister("skills");
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (skillPipelineRuntime != null) {
            skillPipelineRuntime.invalidateAll();
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        EmakiSkillsApi.uninstall(skillsApiBridge);
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
        }
        if (passiveTriggerSource != null) {
            passiveTriggerSource.stop();
            passiveTriggerSource = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        contentReady = false;
        publishLoading();
        lifecycleCoordinator.reload(this, closeOpenInventories);
        contentReady = true;
        publishReady();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories, Consumer<String> progressListener) {
        contentReady = false;
        publishLoading();
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, progressListener)
                .thenRun(() -> {
                    contentReady = true;
                    publishReady();
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
            action.accept(coreLib());
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiSkills readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new SkillsConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(SkillsRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        scheduling = components.scheduling();
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
        skillPipelineRuntime = components.skillPipelineRuntime();
        skillUpgradeService = components.skillUpgradeService();
        castModeService = components.castModeService();
        castAttemptService = components.castAttemptService();
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
                List.of("eskills"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakiskills.use", commandRouter, commandRouter)
        );
    }

    private void registerEventHandlers() {
        if (skillSourceRegistry != null) {
            getServer().getPluginManager().registerEvents(skillSourceRegistry, this);
        }
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }

        triggerDispatcher = new DefaultTriggerDispatcher(
                this, castModeService, triggerRegistry, playerSkillDataStore,
                playerSkillStateService, equipmentSkillCollector,
                castAttemptService, this::appConfig, messageService, scheduling);
        new InteractTriggerSource().register(this, triggerDispatcher);
        new DropTriggerSource().register(this, triggerDispatcher);
        new HotbarTriggerSource().register(this, triggerDispatcher);

        passiveTriggerDispatcher = new PassiveTriggerDispatcher(
                triggerRegistry, playerSkillStateService, castAttemptService);
        passiveTriggerSource = new PassiveTriggerSource(this::appConfig);
        passiveTriggerSource.register(this, passiveTriggerDispatcher, scheduling);
        passiveTriggerSource.reloadCronTasks(this, skillDefinitionLoader.all().values());

        getServer().getPluginManager().registerEvents(
                new CastModeKeyListener(castModeService, actionBarService, messageService),
                this);

        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerSkillDataStore,
                        castModeService, actionBarService, this::appConfig),
                this);
    }

    private void registerActionStages() {
        stageRegistrar = new SkillsStageRegistrar(this);
        stageRegistrar.register();
    }

    private void registerPublicApi() {
        EmakiSkillsApi.install(skillsApiBridge);
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

    public ActionLineRunner actionLines() {
        return coreLib().actionLineRunner(this);
    }

    public EmakiScheduling scheduling() {
        return scheduling;
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

    public SkillPipelineRuntime skillPipelineRuntime() {
        return skillPipelineRuntime;
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

    public PassiveTriggerSource passiveTriggerSource() {
        return passiveTriggerSource;
    }

    public SkillsPlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
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
            String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
            List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

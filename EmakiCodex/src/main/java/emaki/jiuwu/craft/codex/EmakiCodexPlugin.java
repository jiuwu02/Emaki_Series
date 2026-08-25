package emaki.jiuwu.craft.codex;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.action.CodexStageRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementListener;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.advancement.trigger.AdvancementTriggerRegistry;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexGameplaySubscriber;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexTriggerService;
import emaki.jiuwu.craft.codex.codex.gui.CodexGuiService;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.provider.CodexProviderRegistrar;
import emaki.jiuwu.craft.codex.codex.service.CodexEntryService;
import emaki.jiuwu.craft.codex.codex.service.PlayerCodexStore;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.config.CodexConfigPrecheckContributor;
import emaki.jiuwu.craft.codex.api.EmakiCodexApi;
import emaki.jiuwu.craft.codex.listener.PlayerConnectionListener;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.command.PaperCommandAdapter;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.codex.apiimpl.DefaultEmakiCodexApi;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;

public class EmakiCodexPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "codex";
    private static final Set<String> DEBUG_MODULES = Set.of("advancement");
    private static final int BSTATS_PLUGIN_ID = 32376;

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  _____   ______  __  __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  __-./\\  ___\\/\\_\\_\\_\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\ \\/\\ \\ \\  __\\\\/_/\\_\\/_
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\____-\\ \\_____\\/\\_\\/\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/____/ \\/_____/\\/_/\\/_/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xD946EF;
    private static final int STARTUP_ASCII_END_COLOR = 0xF59E0B;

    private final CodexLifecycleCoordinator lifecycleCoordinator = new CodexLifecycleCoordinator();
    private final CodexCommandRouter commandRouter = new CodexCommandRouter(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private MessageService messageService;
    private LanguageLoader languageLoader;
    private BootstrapService bootstrapService;
    private AdvancementPageLoader advancementPageLoader;
    private AdvancementPlatform advancementPlatform;
    private AdvancementJsonBuilder advancementJsonBuilder;
    private AdvancementRegistrar advancementRegistrar;
    private AdvancementService advancementService;
    private AdvancementPacketGateway advancementPacketGateway;
    private AdvancementTriggerRegistry advancementTriggerRegistry;
    private CodexTriggerService triggerService;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private GuiService guiService;
    private GuiTemplateLoader guiTemplateLoader;
    private CodexCategoryLoader codexCategoryLoader;
    private PlayerCodexStore codexStore;
    private CodexProviderRegistrar codexProviderRegistrar;
    private CodexEntryService codexEntryService;
    private CodexGuiService codexGuiService;

    private AdvancementListener advancementListener;
    private CodexGameplaySubscriber gameplaySubscriber;
    private PlayerConnectionListener connectionListener;
    private DebugCommand debugCommand;
    private CodexStageRegistrar stageRegistrar;
    private BStatsRegistration metrics;

    private volatile boolean contentReady;

    private final EmakiCodexApi.Bridge apiBridge =
            new DefaultEmakiCodexApi(this);

    public EmakiCodexPlugin() {
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
        registerActions();

        registerConfigPrecheckContributor();
        reloadPluginState();
        registerCommandHandler();
        registerEventHandlers();
        EmakiCodexApi.install(apiBridge);
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        contentReady = false;
        publishAbsent();
        ConfigPrecheckLifecycleSupport.unregister("codex");
        EmakiCodexApi.uninstall(apiBridge);
        if (gameplaySubscriber != null) {
            gameplaySubscriber.unsubscribe();
            gameplaySubscriber = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState() {
        contentReady = false;
        publishLoading();
        lifecycleCoordinator.reload(this);
        contentReady = true;
        publishReady();
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
            getLogger().fine("EmakiCodex readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new CodexConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(CodexRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        advancementPageLoader = components.advancementPageLoader();
        advancementPlatform = components.advancementPlatform();
        advancementJsonBuilder = components.advancementJsonBuilder();
        advancementRegistrar = components.advancementRegistrar();
        advancementService = components.advancementService();
        advancementPacketGateway = components.advancementPacketGateway();
        advancementTriggerRegistry = components.advancementTriggerRegistry();
        triggerService = components.triggerService();
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        guiService = components.guiService();
        guiTemplateLoader = components.guiTemplateLoader();
        codexCategoryLoader = components.codexCategoryLoader();
        codexStore = components.codexStore();
        codexProviderRegistrar = components.codexProviderRegistrar();
        codexEntryService = components.codexEntryService();
        codexGuiService = components.codexGuiService();

        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(coreLib().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerServices(components);
    }

    private void registerActions() {
        stageRegistrar = new CodexStageRegistrar(this, coreLib().itemSourceService());
        stageRegistrar.register();
    }

    public CodexStageRegistrar stageRegistrar() {
        return stageRegistrar;
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "codex command",
                List.of("ecodex"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakicodex.use", commandRouter, commandRouter)
        );
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(advancementRegistrar, this);
        getServer().getPluginManager().registerEvents(advancementTriggerRegistry, this);
        advancementListener = new AdvancementListener(this, advancementRegistrar);
        getServer().getPluginManager().registerEvents(advancementListener, this);
        gameplaySubscriber = new CodexGameplaySubscriber(this, triggerService);
        gameplaySubscriber.subscribe(coreLib().eventBus());
        connectionListener = new PlayerConnectionListener(this);
        getServer().getPluginManager().registerEvents(connectionListener, this);
        registerAdvancementPacketChannel();
    }

    private void registerAdvancementPacketChannel() {
        if (!appConfig().packetCoordinates()) {
            return;
        }
        if (advancementPacketGateway.register()) {
            messageService.info("console.advancement_packet_enabled");
        } else {
            messageService.info("console.advancement_packet_absent");
        }
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
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

    public AdvancementPageLoader advancementPageLoader() {
        return advancementPageLoader;
    }

    public AdvancementRegistrar advancementRegistrar() {
        return advancementRegistrar;
    }

    public AdvancementService advancementService() {
        return advancementService;
    }

    public AdvancementPacketGateway advancementPacketGateway() {
        return advancementPacketGateway;
    }

    public AdvancementTriggerRegistry advancementTriggerRegistry() {
        return advancementTriggerRegistry;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
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

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public CodexCategoryLoader codexCategoryLoader() {
        return codexCategoryLoader;
    }

    public PlayerCodexStore codexStore() {
        return codexStore;
    }

    public CodexProviderRegistrar codexProviderRegistrar() {
        return codexProviderRegistrar;
    }

    public CodexEntryService codexEntryService() {
        return codexEntryService;
    }

    public CodexGuiService codexGuiService() {
        return codexGuiService;
    }
}

package emaki.jiuwu.craft.codex;

import java.util.Set;
import java.util.UUID;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

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
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.config.CodexConfigPrecheckContributor;
import emaki.jiuwu.craft.codex.api.EmakiCodexApi;
import emaki.jiuwu.craft.codex.listener.PlayerConnectionListener;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;





public class EmakiCodexPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider, EmakiServiceRegistry {

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
    private static final int STARTUP_ASCII_START_COLOR = 0xF59E0B;
    private static final int STARTUP_ASCII_END_COLOR = 0xEC4899;

    private final CodexLifecycleCoordinator lifecycleCoordinator = new CodexLifecycleCoordinator();
    private final CodexCommandRouter commandRouter = new CodexCommandRouter(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private MessageService messageService;
    private emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader;
    private emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService;
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

    private AdvancementListener advancementListener;
    private CodexGameplaySubscriber gameplaySubscriber;
    private PlayerConnectionListener connectionListener;
    private DebugCommand debugCommand;
    private CodexStageRegistrar stageRegistrar;
    private BStatsRegistration metrics;

    private final EmakiCodexApi.Bridge apiBridge =
            new emaki.jiuwu.craft.codex.apiimpl.DefaultEmakiCodexApi(this);

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
        // Registered before the first reload because that reload is now gated on this contributor.
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
        lifecycleCoordinator.reload(this);
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

        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerActions() {
        stageRegistrar = new CodexStageRegistrar(this, coreLib().itemSourceService());
        stageRegistrar.register();
    }

    /** {@return the pipeline stage registrar, or {@code null} before actions are registered} */
    public CodexStageRegistrar stageRegistrar() {
        return stageRegistrar;
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "codex command",
                java.util.List.of("ecodex"),
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

    public emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader() {
        return languageLoader;
    }

    public emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService() {
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

    /**
     * {@return the codex trigger service}
     *
     * @deprecated 仓库内零调用。图鉴触发由生命周期编排在启用期接线，
     *         外部无需从插件主类取用。保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
     */
    @Deprecated(since = "1.0.6", forRemoval = true)
    public CodexTriggerService triggerService() {
        return triggerService;
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

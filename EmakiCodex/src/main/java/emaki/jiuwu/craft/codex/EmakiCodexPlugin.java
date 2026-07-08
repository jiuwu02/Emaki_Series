package emaki.jiuwu.craft.codex;

import java.util.Set;
import java.util.UUID;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.action.CodexActionRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementListener;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexGameplaySubscriber;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexTriggerService;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.api.EmakiCodexApi;
import emaki.jiuwu.craft.codex.listener.PlayerConnectionListener;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

/**
 * EmakiCodex main plugin: registers vanilla advancements driven by corelib actions.
 * Follows the EmakiForge lifecycle skeleton.
 */
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
    private CodexTriggerService triggerService;

    private AdvancementListener advancementListener;
    private CodexGameplaySubscriber gameplaySubscriber;
    private PlayerConnectionListener connectionListener;
    private DebugCommand debugCommand;
    private BStatsRegistration metrics;

    private final EmakiCodexApi.Bridge apiBridge = new CodexApiBridge();

    public EmakiCodexPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        registerActions();
        reloadPluginState();
        registerCommandHandler();
        registerEventHandlers();
        EmakiCodexApi.install(apiBridge);
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
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
        triggerService = components.triggerService();

        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerActions() {
        EmakiCoreLibPlugin coreLib = coreLib();
        new CodexActionRegistrar(this).register(coreLib.actionRegistry());
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

    public CodexTriggerService triggerService() {
        return triggerService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /** Bridge implementation backing the public {@link EmakiCodexApi} facade. */
    private final class CodexApiBridge implements EmakiCodexApi.Bridge {

        @Override
        public String apiVersion() {
            return getDescription().getVersion();
        }

        @Override
        public String pluginName() {
            return getName();
        }

        @Override
        public boolean isReady() {
            return isEnabled() && advancementRegistrar != null;
        }

        @Override
        public boolean grantAdvancement(UUID player, String advancementId) {
            Player target = resolveOnline(player);
            return target != null && advancementService.grant(target, advancementId);
        }

        @Override
        public boolean revokeAdvancement(UUID player, String advancementId) {
            Player target = resolveOnline(player);
            return target != null && advancementService.revoke(target, advancementId);
        }

        private Player resolveOnline(UUID player) {
            return player == null ? null : Bukkit.getPlayer(player);
        }
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
            java.util.List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, args);
            return suggestions == null ? java.util.List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

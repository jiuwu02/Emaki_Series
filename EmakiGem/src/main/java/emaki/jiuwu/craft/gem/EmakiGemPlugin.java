package emaki.jiuwu.craft.gem;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.gem.action.GemStageRegistrar;
import emaki.jiuwu.craft.gem.api.EmakiGemApi;
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.config.GemConfigPrecheckContributor;
import emaki.jiuwu.craft.gem.integration.GemItemLayerPreviewLifecycle;
import emaki.jiuwu.craft.gem.integration.strengthen.GemStrengthenIntegration;
import emaki.jiuwu.craft.gem.listener.GemItemObtainListener;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.loader.GemResonanceLoader;
import emaki.jiuwu.craft.gem.papi.GemPlaceholderExpansion;
import emaki.jiuwu.craft.gem.service.GemActionCoordinator;
import emaki.jiuwu.craft.gem.service.GemEconomyService;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemItemFactory;
import emaki.jiuwu.craft.gem.service.GemItemMatcher;
import emaki.jiuwu.craft.gem.service.GemPdcAttributeWriter;
import emaki.jiuwu.craft.gem.service.GemSnapshotBuilder;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;
import emaki.jiuwu.craft.gem.apiimpl.DefaultEmakiGemApi;

public final class EmakiGemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakigem";

    private static final Set<String> DEBUG_MODULES = Set.of("inlay", "socket", "state", "gui", "pdc");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  __    __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  ___\\/\\ "-./  \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\__ \\ \\  __\\\\ \\ \\-./\\ \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\ \\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/  \\/_/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x22D3EE;
    private static final int STARTUP_ASCII_END_COLOR = 0xA855F7;
    private static final int BSTATS_PLUGIN_ID = 31767;

    private BStatsRegistration metrics;

    private final GemLifecycleCoordinator lifecycleCoordinator = new GemLifecycleCoordinator();
    private final GemItemLayerPreviewLifecycle itemLayerPreviewLifecycle = new GemItemLayerPreviewLifecycle(this);
    private final GemStrengthenIntegration strengthenIntegration = new GemStrengthenIntegration(this);
    private final GemCommandRouter commandRouter = new GemCommandRouter(this);

    private EmakiScheduling scheduling;
    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private GemLoader gemLoader;
    private GemItemLoader gemItemLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemSourceService coreItemSourceService;
    private GemAttributeBridge pdcAttributeGateway;
    private GemItemMatcher itemMatcher;
    private GemItemFactory itemFactory;
    private GemSnapshotBuilder snapshotBuilder;
    private GemStateService stateService;
    private GemEconomyService economyService;
    private GemActionCoordinator actionCoordinator;
    private SocketOpenerService socketOpenerService;
    private GemInlayService inlayService;
    private GemGuiService gemGuiService;
    private GemResonanceLoader resonanceLoader;
    private GemResonanceService resonanceService;
    private GemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private GemStageRegistrar stageRegistrar;
    private final EmakiGemApi.Bridge gemApiBridge =
            new DefaultEmakiGemApi(this);
    private volatile boolean publicApiReady;

    public EmakiGemPlugin() {
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
        registerActions();
        registerEventHandlers();
        registerPublicApiService();
        itemLayerPreviewLifecycle.initialize();
        strengthenIntegration.initialize();
        ensurePlaceholderExpansion();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        publicApiReady = false;
        publishAbsent();
        ConfigPrecheckLifecycleSupport.unregister("gem");
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        itemLayerPreviewLifecycle.close();
        strengthenIntegration.close();
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
        }
        EmakiGemApi.uninstall(gemApiBridge);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        publicApiReady = false;
        publishLoading();
        lifecycleCoordinator.reload(this, closeOpenInventories);
        publicApiReady = true;
        publishReady();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        publicApiReady = false;
        publishLoading();
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null)
                .thenRun(() -> {
                    publicApiReady = true;
                    publishReady();
                });
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
            getLogger().fine("EmakiGem readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new GemConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(GemRuntimeComponents components) {
        scheduling = components.scheduling();
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        gemLoader = components.gemLoader();
        gemItemLoader = components.gemItemLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        coreItemSourceService = components.coreItemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        itemMatcher = components.itemMatcher();
        itemFactory = components.itemFactory();
        snapshotBuilder = components.snapshotBuilder();
        stateService = components.stateService();
        economyService = components.economyService();
        actionCoordinator = components.actionCoordinator();
        socketOpenerService = components.socketOpenerService();
        inlayService = components.inlayService();
        gemGuiService = components.gemGuiService();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(coreLib().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerServices(components);
    }

    private void registerCommandHandler() {
        registerCommand(
                ROOT_COMMAND,
                "emakigem command",
                List.of("egem", "eg"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakigem.use", commandRouter, commandRouter)
        );
    }

    private void registerActions() {
        stageRegistrar = new GemStageRegistrar(this);
        stageRegistrar.register();
    }

    private void registerEventHandlers() {
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }
        getServer().getPluginManager().registerEvents(new GemItemObtainListener(this, scheduling), this);
    }

    private void registerPublicApiService() {
        EmakiGemApi.install(gemApiBridge);
    }

    public void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new GemPlaceholderExpansion(this, stateService, gemItemLoader);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public GemLoader gemLoader() {
        return gemLoader;
    }

    public GemItemLoader gemItemLoader() {
        return gemItemLoader;
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

    public EmakiScheduling scheduling() {
        return scheduling;
    }

    public boolean publicApiReady() {
        return publicApiReady;
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

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public GemAttributeBridge pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public GemItemMatcher itemMatcher() {
        return itemMatcher;
    }

    public GemItemFactory itemFactory() {
        return itemFactory;
    }

    public GemSnapshotBuilder snapshotBuilder() {
        return snapshotBuilder;
    }

    public GemStateService stateService() {
        return stateService;
    }

    public GemEconomyService economyService() {
        return economyService;
    }

    public GemActionCoordinator actionCoordinator() {
        return actionCoordinator;
    }

    public SocketOpenerService socketOpenerService() {
        return socketOpenerService;
    }

    public GemInlayService inlayService() {
        return inlayService;
    }

    public GemGuiService gemGuiService() {
        return gemGuiService;
    }

    public GemStrengthenIntegration strengthenIntegration() {
        return strengthenIntegration;
    }

    public GemResonanceLoader resonanceLoader() {
        return resonanceLoader;
    }

    public GemResonanceService resonanceService() {
        return resonanceService;
    }

    public void setResonanceLoader(GemResonanceLoader resonanceLoader) {
        this.resonanceLoader = resonanceLoader;
    }

    public void setResonanceService(GemResonanceService resonanceService) {
        this.resonanceService = resonanceService;
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

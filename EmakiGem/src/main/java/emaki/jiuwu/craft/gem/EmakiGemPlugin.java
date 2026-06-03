package emaki.jiuwu.craft.gem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.gem.api.EmakiGemApi;
import emaki.jiuwu.craft.gem.apiimpl.DefaultEmakiGemApi;
import emaki.jiuwu.craft.gem.config.AppConfig;
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
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

public final class EmakiGemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakigem";

    private static final Set<String> DEBUG_MODULES = Set.of("inlay", "socket", "state", "gui");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  __    __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  ___\\/\\ "-./  \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\__ \\ \\  __\\\\ \\ \\-./\\ \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\ \\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/  \\/_/
""";
    private static final int BSTATS_PLUGIN_ID = 31767

    private Metrics metrics;

    private final GemLifecycleCoordinator lifecycleCoordinator = new GemLifecycleCoordinator();
    private final GemCommandRouter commandRouter = new GemCommandRouter(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private GemLoader gemLoader;
    private GemItemLoader gemItemLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemSourceService coreItemSourceService;
    private PdcAttributeGateway pdcAttributeGateway;
    private GemItemMatcher itemMatcher;
    private GemItemFactory itemFactory;
    private GemSnapshotBuilder snapshotBuilder;
    private GemPdcAttributeWriter pdcAttributeWriter;
    private GemStateService stateService;
    private GemEconomyService economyService;
    private GemActionCoordinator actionCoordinator;
    private SocketOpenerService socketOpenerService;
    private GemInlayService inlayService;
    private GemExtractService extractService;
    private GemUpgradeService upgradeService;
    private GemGuiService gemGuiService;
    private GemResonanceLoader resonanceLoader;
    private GemResonanceService resonanceService;
    private GemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private EmakiGemApi gemApi;

    public EmakiGemPlugin() {
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
        registerPublicApiService();
        registerWebConsole();
        ensurePlaceholderExpansion();
        forceEnableBStats();
        metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        if (gemApi != null) {
            getServer().getServicesManager().unregister(EmakiGemApi.class, gemApi);
            gemApi = null;
        }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null);
    }

    private void applyRuntimeComponents(GemRuntimeComponents components) {
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
        pdcAttributeWriter = components.pdcAttributeWriter();
        stateService = components.stateService();
        economyService = components.economyService();
        actionCoordinator = components.actionCoordinator();
        socketOpenerService = components.socketOpenerService();
        inlayService = components.inlayService();
        extractService = components.extractService();
        upgradeService = components.upgradeService();
        gemGuiService = components.gemGuiService();
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
        getServer().getPluginManager().registerEvents(new GemItemObtainListener(this), this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
    }

    private void registerPublicApiService() {
        gemApi = new DefaultEmakiGemApi(this);
        getServer().getServicesManager().register(EmakiGemApi.class, gemApi, this, ServicePriority.Normal);
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

    public GuiService guiService() {
        return guiService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
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

    public GemPdcAttributeWriter pdcAttributeWriter() {
        return pdcAttributeWriter;
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

    public GemExtractService extractService() {
        return extractService;
    }

    public GemUpgradeService upgradeService() {
        return upgradeService;
    }

    public GemGuiService gemGuiService() {
        return gemGuiService;
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

    private void forceEnableBStats() {
        java.io.File bStatsFolder = new java.io.File(getDataFolder().getParentFile(), "bStats");
        if (!bStatsFolder.exists()) {
            bStatsFolder.mkdirs();
        }
        java.io.File configFile = new java.io.File(bStatsFolder, "config.yml");
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        config.set("enabled", true);
        try {
            config.save(configFile);
        } catch (java.io.IOException ignored) {
        }
    }
}

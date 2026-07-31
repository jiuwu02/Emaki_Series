package emaki.jiuwu.craft.attribute;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;

import emaki.jiuwu.craft.attribute.action.AttributeActions;
import emaki.jiuwu.craft.attribute.bridge.MmoItemsBridge;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.AttributeConfigPrecheckContributor;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.papi.AttributePlaceholderExpansion;
import emaki.jiuwu.craft.attribute.service.AttributePointsGuiService;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.ContributionProviderRegistrationRegistry;
import emaki.jiuwu.craft.attribute.service.ItemContributionGateRegistry;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeDataStore;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;

public final class EmakiAttributePlugin extends AbstractEmakiPlugin implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String STARTUP_ASCII = """
  ______  __    __  ______  __  __   __  ______  ______  ______  ______  __  ______  __  __  ______  ______
 /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  __ \\/\\__  _\\/\\__  _\\/\\  == \\/\\ \\/\\  == \\/\\ \\/\\ \\/\\__  _\\/\\  ___\\   
 \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __ \\/_/\\ \\/\\/_/\\ \\/\\ \\  __<\\ \\ \\ \\  __<\\ \\ \\_\\ \\/_/\\ \\/\\ \\  __\\   
  \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\ \\ \\_\\   \\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\ \\_\\ \\ \\_____\\ 
   \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/  \\/_/    \\/_/  \\/_/ /_/\\/_/\\/_____/\\/_____/  \\/_/  \\/_____/
                                                                                                                
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xFB7185;
    private static final int STARTUP_ASCII_END_COLOR = 0xA78BFA;
    private static final int BSTATS_PLUGIN_ID = 31764;

    private BStatsRegistration metrics;

    private final AttributeLifecycleCoordinator lifecycleCoordinator = new AttributeLifecycleCoordinator();

    private static final Set<String> DEBUG_MODULES = Set.of("combat", "resync", "snapshot", "resource", "pdc");

    private DebugCommand debugCommand;

    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private AttributeBalanceRegistry attributeBalanceRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private PdcReadRuleLoader pdcReadRuleLoader;
    private ItemContributionGateRegistry itemContributionGateRegistry;
    private ContributionProviderRegistrationRegistry contributionProviderRegistrationRegistry;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private emaki.jiuwu.craft.attribute.service.DamageIndicatorService damageIndicatorService;
    private EmakiAttributeApi.Bridge emakiAttributeBridge;
    private ParentAttributeDataStore parentAttributeDataStore;
    private ParentAttributeService parentAttributeService;
    private GuiTemplateLoader guiTemplateLoader;
    private GuiService guiService;
    private AttributePointsGuiService attributePointsGuiService;
    private AttributeService attributeService;
    private List<Listener> listeners = List.of();
    private AttributeCommand command;
    private MythicBridge mythicBridge;
    private MmoItemsBridge mmoItemsBridge;
    private AttributePlaceholderExpansion placeholderExpansion;
    private TaskHandle regenTask;
    private CompletableFuture<Void> reloadFuture;

    @Override
    public void onEnable() {
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        registerConfigPrecheckContributor();
        registerAttributeBridgeService();
        registerAttributeServiceFacade();
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        reloadPluginState(true);
        ensureMmoItemsBridge();
        lifecycleCoordinator.registerCommand(this);
        lifecycleCoordinator.registerListener(this);
        ensurePlaceholderExpansion();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        unregisterCoreLibActions();
        ConfigPrecheckLifecycleSupport.unregister("attribute");
        EmakiAttributeApi.uninstall(emakiAttributeBridge);
        Bukkit.getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this, regenTask);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        regenTask = null;
    }

    public void ensureMythicBridge() {
        if (mythicBridge != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicBridge = new MythicBridge(this, attributeService);
        getServer().getPluginManager().registerEvents(mythicBridge, this);
    }

    public void ensureMmoItemsBridge() {
        if (mmoItemsBridge != null || attributeService == null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }
        mmoItemsBridge = new MmoItemsBridge(this, attributeService, executionDispatcher);
        getServer().getPluginManager().registerEvents(mmoItemsBridge, this);
        attributeService.resyncAllPlayers();
    }

    public void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new AttributePlaceholderExpansion(this, attributeService);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public void reloadPluginState(boolean resyncPlayers) {
        regenTask = lifecycleCoordinator.reload(this, regenTask, resyncPlayers);
        registerCoreLibActions();
        logConfigPrecheckReport();
    }

    public synchronized CompletableFuture<Void> reloadPluginStateAsync(boolean resyncPlayers, Consumer<String> progressListener) {
        if (reloadFuture != null && !reloadFuture.isDone()) {
            if (progressListener != null) {
                progressListener.accept(messageService.message("command.reload.in_progress"));
            }
            return reloadFuture;
        }
        reloadFuture = lifecycleCoordinator.reloadAsync(this, regenTask, resyncPlayers, progressListener)
                .thenAccept(task -> {
                    regenTask = task;
                    registerCoreLibActions();
                    logConfigPrecheckReport();
                })
                .whenComplete((_, throwable) -> {
                    synchronized (this) {
                        reloadFuture = null;
                    }
                });
        return reloadFuture;
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "attribute");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new AttributeConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(AttributeRuntimeComponents components) {
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        attributeRegistry = components.attributeRegistry();
        attributeBalanceRegistry = components.attributeBalanceRegistry();
        damageTypeRegistry = components.damageTypeRegistry();
        defaultProfileRegistry = components.defaultProfileRegistry();
        loreFormatRegistry = components.loreFormatRegistry();
        presetRegistry = components.presetRegistry();
        pdcReadRuleLoader = components.pdcReadRuleLoader();
        itemContributionGateRegistry = components.itemContributionGateRegistry();
        contributionProviderRegistrationRegistry = components.contributionProviderRegistrationRegistry();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        // 飘字服务用 Supplier 取依赖，这样 reload 后自动读到新配置，无需重建实例。
        damageIndicatorService = new emaki.jiuwu.craft.attribute.service.DamageIndicatorService(
                () -> {
                    emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin coreLib =
                            getPlugin(emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin.class);
                    return coreLib == null ? null : coreLib.textDisplayService();
                },
                () -> configModel() == null ? null : configModel().damageIndicator(),
                this::messageService);
        emakiAttributeBridge = components.emakiAttributeBridge();
        parentAttributeDataStore = components.parentAttributeDataStore();
        parentAttributeService = components.parentAttributeService();
        guiTemplateLoader = components.guiTemplateLoader();
        guiService = components.guiService();
        attributePointsGuiService = components.attributePointsGuiService();
        attributeService = components.attributeService();
        listeners = components.listeners();
        command = components.command();
        mythicBridge = components.mythicBridge();
        initDebugLogger();
        registerServices(components);
    }

    private void initDebugLogger() {
        LanguageLoader coreLanguageLoader = new LanguageLoader(this);
        coreLanguageLoader.load();
        setDebugLogger(new DebugLogger(this, coreLanguageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
    }

    private void registerAttributeBridgeService() {
        if (emakiAttributeBridge == null) {
            return;
        }
        EmakiAttributeApi.install(emakiAttributeBridge);
    }

    private void registerAttributeServiceFacade() {
        if (attributeService == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(AttributeServiceFacade.class, attributeService);
        Bukkit.getServicesManager().register(AttributeServiceFacade.class, attributeService, this, ServicePriority.Normal);
    }

    void setConfigModel(AttributeConfig configModel) {
        this.configModel = configModel == null ? AttributeConfig.defaults() : configModel;
    }

    void setPlaceholderExpansion(AttributePlaceholderExpansion placeholderExpansion) {
        this.placeholderExpansion = placeholderExpansion;
    }

    public AttributeConfig configModel() {
        return configModel;
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistry;
    }

    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistry;
    }

    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistry;
    }

    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistry;
    }

    public AttributePresetRegistry presetRegistry() {
        return presetRegistry;
    }

    public PdcReadRuleLoader pdcReadRuleLoader() {
        return pdcReadRuleLoader;
    }

    public ItemContributionGateRegistry itemContributionGateRegistry() {
        return itemContributionGateRegistry;
    }

    public ContributionProviderRegistrationRegistry contributionProviderRegistrationRegistry() {
        return contributionProviderRegistrationRegistry;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    /** {@return 伤害飘字服务，未启用飘字时仍返回实例但不会生成任何实体} */
    public emaki.jiuwu.craft.attribute.service.DamageIndicatorService damageIndicatorService() {
        return damageIndicatorService;
    }

    public MessageService messageService() {
        return messageService;
    }

    public ParentAttributeDataStore parentAttributeDataStore() {
        return parentAttributeDataStore;
    }

    public ParentAttributeService parentAttributeService() {
        return parentAttributeService;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public GuiService guiService() {
        return guiService;
    }

    public AttributePointsGuiService attributePointsGuiService() {
        return attributePointsGuiService;
    }

    public AttributeService attributeService() {
        return attributeService;
    }

    public List<Listener> listeners() {
        return listeners;
    }

    public AttributeCommand command() {
        return command;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
    }

    public MmoItemsBridge mmoItemsBridge() {
        return mmoItemsBridge;
    }

    public AttributePlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> scalingCurves() {
        return scalingCurves;
    }

    private volatile java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> scalingCurves = java.util.List.of();

    public void loadScalingCurves(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null) {
            this.scalingCurves = java.util.List.of();
            return;
        }
        java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> curves = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            emaki.jiuwu.craft.corelib.yaml.YamlSection curveSection = section.getSection(key);
            if (curveSection == null) {
                continue;
            }
            String attributeId = curveSection.getString("attribute", key);
            double threshold = curveSection.getDouble("threshold", 0D);
            String curveType = curveSection.getString("curve_type", "logarithmic");
            double factor = curveSection.getDouble("factor", 1D);
            curves.add(new emaki.jiuwu.craft.attribute.service.ScalingCurveConfig(
                    attributeId, threshold, curveType, factor));
        }
        this.scalingCurves = java.util.List.copyOf(curves);
    }

    private void registerCoreLibActions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.actionRegistry() == null || attributeService == null) {
            return;
        }
        AttributeActions.registerAll(coreLibPlugin.actionRegistry(), this, attributeService);
    }

    private void unregisterCoreLibActions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.actionRegistry() == null) {
            return;
        }
        AttributeActions.unregisterAll(coreLibPlugin.actionRegistry(), this);
    }

}

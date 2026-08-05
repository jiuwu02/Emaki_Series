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

import emaki.jiuwu.craft.attribute.action.AttributeStageRegistrar;
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
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
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
    private ParentAttributeService parentAttributeService;
    private GuiTemplateLoader guiTemplateLoader;
    private GuiService guiService;
    private AttributePointsGuiService attributePointsGuiService;
    private AttributeService attributeService;
    private List<Listener> listeners = List.of();
    private AttributeCommand command;
    private MythicBridge mythicBridge;
    private boolean mythicBridgeRegistered;
    private MmoItemsBridge mmoItemsBridge;
    private AttributePlaceholderExpansion placeholderExpansion;
    private TaskHandle regenTask;
    private AttributeStageRegistrar stageRegistrar;
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
        // Bukkit 在 disable 时注销本插件的监听，这里同步清掉句柄与注册标记，
        // 避免旧 bridge 实例与「已注册」状态跨 reload 泄漏到下一次 enable。
        mythicBridge = null;
        mythicBridgeRegistered = false;
    }

    /**
     * MythicBridge 的唯一注册入口：幂等地保证实例存在且已注册一次监听。
     *
     * <p>守卫针对「是否已注册」而非「是否已构造」，因为实例可能由生命周期协调器的
     * {@code initialize} 预先构造；只防重复构造会让注册责任落到别处，
     * 导致同一实例被注册两次、处理器触发两次。
     */
    public void ensureMythicBridge() {
        if (mythicBridgeRegistered) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        if (mythicBridge == null) {
            mythicBridge = new MythicBridge(this, attributeService);
        }
        getServer().getPluginManager().registerEvents(mythicBridge, this);
        mythicBridgeRegistered = true;
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

    public AttributePlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    private void registerCoreLibActions() {
        if (attributeService == null) {
            return;
        }
        // Rebuilt on every module reload: the stages capture the service facade, and a reload may have
        // replaced it.
        stageRegistrar = new AttributeStageRegistrar(this, attributeService);
        stageRegistrar.register();
    }

    private void unregisterCoreLibActions() {
        if (stageRegistrar != null) {
            stageRegistrar.unregister();
            stageRegistrar = null;
        }
    }

}

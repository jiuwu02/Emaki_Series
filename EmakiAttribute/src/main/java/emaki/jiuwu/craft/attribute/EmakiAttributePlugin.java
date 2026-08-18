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

import emaki.jiuwu.craft.attribute.service.DamageIndicatorService;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;

import emaki.jiuwu.craft.attribute.action.AttributeStageRegistrar;
import emaki.jiuwu.craft.attribute.bridge.BetterHudBridge;
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
import emaki.jiuwu.craft.attribute.service.AttributeSlotRegistry;
import emaki.jiuwu.craft.attribute.service.ItemContributionGateRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeDataStore;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractEmakiPlugin;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;

public final class EmakiAttributePlugin extends AbstractEmakiPlugin implements LogMessagesProvider {

    private static final String STARTUP_ASCII = """
  ______  __    __  ______  __  __   __  ______  ______  ______  ______  __  ______  __  __  ______  ______
 /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  __ \\/\\__  _\\/\\__  _\\/\\  == \\/\\ \\/\\  == \\/\\ \\/\\ \\/\\__  _\\/\\  ___\\
 \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __ \\/_/\\ \\/\\/_/\\ \\/\\ \\  __<\\ \\ \\ \\  __<\\ \\ \\_\\ \\/_/\\ \\/\\ \\  __\\
  \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\ \\ \\_\\   \\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\ \\_\\ \\ \\_____\\
   \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/  \\/_/    \\/_/  \\/_/ /_/\\/_/\\/_____/\\/_____/  \\/_/  \\/_____/

""";
    private static final int STARTUP_ASCII_START_COLOR = 0xF43F5E;
    private static final int STARTUP_ASCII_END_COLOR = 0xFB923C;
    private static final int BSTATS_PLUGIN_ID = 31764;

    private BStatsRegistration metrics;

    private final AttributeLifecycleCoordinator lifecycleCoordinator = new AttributeLifecycleCoordinator();

    private static final Set<String> DEBUG_MODULES = Set.of("combat", "resync", "snapshot", "resource", "pdc");

    private DebugCommand debugCommand;

    private EmakiScheduling scheduling;
    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private AttributeBalanceRegistry attributeBalanceRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private PdcReadRuleLoader pdcReadRuleLoader;
    private ItemContributionGateRegistry itemContributionGateRegistry;
    private AttributeSlotRegistry attributeSlotRegistry;
    private ContributionProviderRegistrationRegistry contributionProviderRegistrationRegistry;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private DamageIndicatorService damageIndicatorService;
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
    private BetterHudBridge betterHudBridge;
    private boolean betterHudBridgeRegistered;
    private MmoItemsBridge mmoItemsBridge;
    private AttributePlaceholderExpansion placeholderExpansion;
    private TaskToken regenTask;
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
        ensureBetterHudBridge();
        lifecycleCoordinator.registerCommand(this);
        lifecycleCoordinator.registerListener(this);
        ensurePlaceholderExpansion();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        publishAbsent();
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

        mythicBridge = null;
        mythicBridgeRegistered = false;
        betterHudBridge = null;
        betterHudBridgeRegistered = false;
    }

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
        mmoItemsBridge = new MmoItemsBridge(this, attributeService, scheduling);
        getServer().getPluginManager().registerEvents(mmoItemsBridge, this);
        attributeService.resyncAllPlayers();
    }

    public void ensureBetterHudBridge() {
        if (betterHudBridgeRegistered) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("BetterHUD")) {
            return;
        }
        if (betterHudBridge == null) {
            betterHudBridge = new BetterHudBridge(this);
        }
        betterHudBridge.registerTriggers();
        getServer().getPluginManager().registerEvents(betterHudBridge, this);
        betterHudBridgeRegistered = true;
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
        publishLoading();
        regenTask = lifecycleCoordinator.reload(this, regenTask, resyncPlayers);
        registerCoreLibActions();
        syncReadiness();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean resyncPlayers, Consumer<String> progressListener) {
        publishLoading();
        CompletableFuture<Void> reload = startReloadAsync(resyncPlayers, progressListener);

        return reload.whenComplete((ignored, throwable) -> syncReadiness());
    }

    private synchronized CompletableFuture<Void> startReloadAsync(boolean resyncPlayers,
            Consumer<String> progressListener) {
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
                })
                .whenComplete((_, throwable) -> {
                    synchronized (this) {
                        reloadFuture = null;
                    }
                });
        return reloadFuture;
    }

    private void syncReadiness() {
        boolean ready = attributeService != null
                && attributeService.attributeRegistry() != null
                && attributeService.attributeRegistry().loaded()
                && attributeService.damageTypeRegistry() != null
                && attributeService.damageTypeRegistry().loaded()
                && attributeService.defaultProfileRegistry() != null
                && attributeService.defaultProfileRegistry().loaded();
        publishReadiness(coreLibPlugin -> {
            if (ready) {
                coreLibPlugin.markModuleReady(getName());
            } else {
                coreLibPlugin.markModuleLoading(getName());
            }
        });
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
            getLogger().fine("EmakiAttribute readiness publication skipped: " + exception);
        }
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new AttributeConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(AttributeRuntimeComponents components) {
        scheduling = components.scheduling();
        attributeRegistry = components.attributeRegistry();
        attributeBalanceRegistry = components.attributeBalanceRegistry();
        damageTypeRegistry = components.damageTypeRegistry();
        defaultProfileRegistry = components.defaultProfileRegistry();
        loreFormatRegistry = components.loreFormatRegistry();
        presetRegistry = components.presetRegistry();
        pdcReadRuleLoader = components.pdcReadRuleLoader();
        itemContributionGateRegistry = components.itemContributionGateRegistry();
        attributeSlotRegistry = components.attributeSlotRegistry();
        contributionProviderRegistrationRegistry = components.contributionProviderRegistrationRegistry();
        languageLoader = components.languageLoader();
        messageService = components.messageService();

        damageIndicatorService = new DamageIndicatorService(
                () -> {
                    EmakiCoreLibPlugin coreLib =
                            getPlugin(EmakiCoreLibPlugin.class);
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
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(coreLib().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
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

    public EmakiScheduling scheduling() {
        return scheduling;
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

    public AttributeSlotRegistry attributeSlotRegistry() {
        return attributeSlotRegistry;
    }

    public ContributionProviderRegistrationRegistry contributionProviderRegistrationRegistry() {
        return contributionProviderRegistrationRegistry;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public DamageIndicatorService damageIndicatorService() {
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

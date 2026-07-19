package emaki.jiuwu.craft.attribute;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;

import emaki.jiuwu.craft.attribute.action.AttributeActions;
import emaki.jiuwu.craft.attribute.action.AttributeDamageSkillAction;
import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
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
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.papi.AttributePlaceholderExpansion;
import emaki.jiuwu.craft.attribute.service.AttributePointsGuiService;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeDataStore;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptAttributeExtensionLoader;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamageHookListener;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamageHookRegistry;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamagePipelineRegistry;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.attribute.script.ScriptAttributeModuleApi;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;

public final class EmakiAttributePlugin extends AbstractEmakiPlugin implements EmakiServiceRegistry {

    private static final String STARTUP_ASCII = """
  ______  __    __  ______  __  __   __  ______  ______  ______  ______  __  ______  __  __  ______  ______
 /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  __ \\/\\__  _\\/\\__  _\\/\\  == \\/\\ \\/\\  == \\/\\ \\/\\ \\/\\__  _\\/\\  ___\\   
 \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __ \\/_/\\ \\/\\/_/\\ \\/\\ \\  __<\\ \\ \\ \\  __<\\ \\ \\_\\ \\/_/\\ \\/\\ \\  __\\   
  \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\ \\ \\_\\   \\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\ \\_\\ \\ \\_____\\ 
   \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/  \\/_/    \\/_/  \\/_/ /_/\\/_/\\/_____/\\/_____/  \\/_/  \\/_____/
                                                                                                                
""";
    private static final int BSTATS_PLUGIN_ID = 31764;

    private BStatsRegistration metrics;

    private final AttributeLifecycleCoordinator lifecycleCoordinator = new AttributeLifecycleCoordinator();

    private static final Set<String> DEBUG_MODULES = Set.of("combat", "resync", "snapshot", "resource");

    private DebugCommand debugCommand;

    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private AttributeBalanceRegistry attributeBalanceRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private PdcReadRuleLoader pdcReadRuleLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private EmakiAttributeBridge emakiAttributeBridge;
    private PdcAttributeApi.Bridge pdcAttributeApi;
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
    private JavaScriptDamageHookRegistry javaScriptDamageHookRegistry;
    private JavaScriptDamagePipelineRegistry javaScriptDamagePipelineRegistry;
    private JavaScriptAttributeExtensionLoader javaScriptAttributeExtensionLoader;
    private TaskHandle regenTask;
    private CompletableFuture<Void> reloadFuture;

    @Override
    public void onEnable() {
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        registerConfigPrecheckContributor();
        registerAttributeBridgeService();
        registerPdcAttributeApi();
        registerAttributeServiceFacade();
        registerScriptModule();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        reloadPluginState(true);
        ensureMmoItemsBridge();
        lifecycleCoordinator.registerCommand(this);
        lifecycleCoordinator.registerListener(this);
        ensurePlaceholderExpansion();
        registerSkillScriptActions();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        unregisterCoreLibActions();
        ConfigPrecheckLifecycleSupport.unregister("attribute");
        coreLib().scriptModuleRegistry().unregister("attribute");
        if (javaScriptAttributeExtensionLoader != null) {
            javaScriptAttributeExtensionLoader.close();
            javaScriptAttributeExtensionLoader = null;
        }
        PdcAttributeApi.uninstall(pdcAttributeApi);
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
        mmoItemsBridge = new MmoItemsBridge(this, attributeService);
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
        reloadJavaScriptAttributeExtensions();
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
                    reloadJavaScriptAttributeExtensions();
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
        attributeRegistry = components.attributeRegistry();
        attributeBalanceRegistry = components.attributeBalanceRegistry();
        damageTypeRegistry = components.damageTypeRegistry();
        defaultProfileRegistry = components.defaultProfileRegistry();
        loreFormatRegistry = components.loreFormatRegistry();
        presetRegistry = components.presetRegistry();
        pdcReadRuleLoader = components.pdcReadRuleLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        emakiAttributeBridge = components.emakiAttributeBridge();
        pdcAttributeApi = components.pdcAttributeApi();
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
        emaki.jiuwu.craft.corelib.loader.LanguageLoader coreLanguageLoader =
                new emaki.jiuwu.craft.corelib.loader.LanguageLoader(this);
        coreLanguageLoader.load();
        setDebugLogger(new DebugLogger(this, coreLanguageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
    }

    private void registerPdcAttributeApi() {
        if (pdcAttributeApi == null) {
            return;
        }
        PdcAttributeApi.install(pdcAttributeApi);
        emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi coreApi =
                (emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi) pdcAttributeApi;
        Bukkit.getServicesManager().unregister(emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi.class, coreApi);
        Bukkit.getServicesManager().register(emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi.class, coreApi, this, ServicePriority.Normal);
    }

    private void registerAttributeBridgeService() {
        if (emakiAttributeBridge == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(EmakiAttributeBridge.class, emakiAttributeBridge);
        Bukkit.getServicesManager().register(EmakiAttributeBridge.class, emakiAttributeBridge, this, ServicePriority.Normal);
    }

    private void registerAttributeServiceFacade() {
        if (attributeService == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(AttributeServiceFacade.class, attributeService);
        Bukkit.getServicesManager().register(AttributeServiceFacade.class, attributeService, this, ServicePriority.Normal);
    }

    private void registerScriptModule() {
        coreLib().scriptModuleRegistry().register("attribute", context -> new ScriptAttributeModuleApi(context.actionContext()));
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

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public PdcAttributeApi.Bridge pdcAttributeApi() {
        return pdcAttributeApi;
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

    public JavaScriptDamageHookRegistry javaScriptDamageHookRegistry() {
        return javaScriptDamageHookRegistry;
    }

    public JavaScriptDamagePipelineRegistry javaScriptDamagePipelineRegistry() {
        return javaScriptDamagePipelineRegistry;
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

    private void reloadJavaScriptAttributeExtensions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (javaScriptAttributeExtensionLoader != null) {
            javaScriptAttributeExtensionLoader.close();
        }
        releaseBundledScripts(coreLibPlugin);
        if (coreLibPlugin.javaScriptService() == null || attributeService == null) {
            return;
        }
        if (javaScriptDamageHookRegistry == null) {
            javaScriptDamageHookRegistry = new JavaScriptDamageHookRegistry(this, coreLibPlugin.javaScriptService(), coreLibPlugin.configModel().scriptConfig());
        }
        if (javaScriptDamagePipelineRegistry == null) {
            javaScriptDamagePipelineRegistry = new JavaScriptDamagePipelineRegistry(this, coreLibPlugin.javaScriptService(), coreLibPlugin.configModel().scriptConfig());
        }
        javaScriptAttributeExtensionLoader = new JavaScriptAttributeExtensionLoader(
                this,
                coreLibPlugin.javaScriptService(),
                coreLibPlugin.configModel().scriptConfig(),
                javaScriptDamageHookRegistry,
                javaScriptDamagePipelineRegistry
        );
        javaScriptAttributeExtensionLoader.reload();
    }

    private void unregisterCoreLibActions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.actionRegistry() == null) {
            return;
        }
        AttributeActions.unregisterAll(coreLibPlugin.actionRegistry(), this);
    }

    private void releaseBundledScripts(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.releaseBundledScripts(this, "mythic", false, java.util.List.of("mythic_js_damage.js"));
        coreLibPlugin.releaseBundledScripts(this, "examples", false, java.util.List.of("attribute_buff.js", "js_fire_mastery.js"));
    }

    private void registerSkillScriptActions() {
        if (attributeService == null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            return;
        }
        try {
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(
                    Class.forName("emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry"));
            if (provider == null || provider.getProvider() == null) {
                return;
            }
            Object registry = provider.getProvider();
            java.lang.reflect.Method registerMethod = registry.getClass().getMethod(
                    "register", org.bukkit.plugin.Plugin.class,
                    Class.forName("emaki.jiuwu.craft.skills.api.SkillScriptAction"));
            registerMethod.invoke(registry, this, new AttributeDamageSkillAction(attributeService));
            messageService.info("console.skill_action_registered");
        } catch (Exception exception) {
            getLogger().warning("Failed to register attribute_damage skill action: " + exception.getMessage());
        }
    }

}

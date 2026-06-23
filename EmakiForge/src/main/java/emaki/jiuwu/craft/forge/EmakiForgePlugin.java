package emaki.jiuwu.craft.forge;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.config.ForgeConfigPrecheckContributor;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.papi.ForgePlaceholderExpansion;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeResultHookRegistry;
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeRuleRegistry;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

public class EmakiForgePlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakiforge";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __\\\\ \\ \\/\\ \\ \\  __<\\ \\ \\__ \\ \\  __\\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\   \\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/    \\/_____/\\/_/ /_/\\/_____/\\/_____/
""";
    private static final int BSTATS_PLUGIN_ID = 31766;

    private BStatsRegistration metrics;

    private final ForgeLifecycleCoordinator lifecycleCoordinator = new ForgeLifecycleCoordinator();
    private final ForgeCommandRouter commandRouter = new ForgeCommandRouter(this);
    private final ForgePlayerDataListener playerDataListener = new ForgePlayerDataListener(this);
    private final ForgeItemRefreshListener itemRefreshListener = new ForgeItemRefreshListener(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private RecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private PlayerDataStore playerDataStore;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemIdentifierService itemIdentifierService;
    private PdcAttributeGateway pdcAttributeGateway;
    private ForgeItemRefreshService itemRefreshService;
    private ForgeService forgeService;
    private ForgeGuiService forgeGuiService;
    private RecipeBookGuiService recipeBookGuiService;
    private final JavaScriptForgeRuleRegistry javaScriptForgeRuleRegistry = new JavaScriptForgeRuleRegistry(this);
    private final JavaScriptForgeResultHookRegistry javaScriptResultHookRegistry = new JavaScriptForgeResultHookRegistry(this);
    private ForgePlaceholderExpansion placeholderExpansion;
    private TaskHandle autoSaveTask;
    private DebugCommand debugCommand;
    private final EmakiForgeApi.Bridge forgeApiBridge = new EmakiForgeApi.Bridge() {
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
            return isEnabled() && forgeService() != null;
        }
    };

    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "forge", "gui");

    public EmakiForgePlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        registerConfigPrecheckContributor();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerCommandHandler();
        registerEventHandlers();
        registerPublicApiService();
        registerWebConsole();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        ConfigPrecheckLifecycleSupport.unregister("forge");
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        EmakiForgeApi.uninstall(forgeApiBridge);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        lifecycleCoordinator.shutdown(this, autoSaveTask);
        AdventureSupport.close(this);
        autoSaveTask = null;
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        autoSaveTask = lifecycleCoordinator.reload(this, autoSaveTask, closeOpenInventories);
        logConfigPrecheckReport();
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, autoSaveTask, closeOpenInventories, null)
                .thenAccept(task -> {
                    autoSaveTask = task;
                    logConfigPrecheckReport();
                });
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messageService(), "forge");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new ForgeConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(ForgeRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        playerDataStore = components.playerDataStore();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        itemIdentifierService = components.itemIdentifierService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        itemRefreshService = components.itemRefreshService();
        forgeService = components.forgeService();
        forgeGuiService = components.forgeGuiService();
        recipeBookGuiService = components.recipeBookGuiService();
        setDebugLogger(new DebugLogger(this, languageLoader));
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
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
        registerJavaScriptCompletions();
    }

    private void registerJavaScriptCompletions() {
        scriptMethod("available", "available()", "available()");
        scriptMethod("apiVersion", "apiVersion()", "apiVersion()");
        scriptMethod("pluginName", "pluginName()", "pluginName()");
        scriptMethod("ready", "ready()", "ready()");
        scriptMethod("registerForgeRule", "registerForgeRule(definition)", "registerForgeRule({ id: \"moon_phase_bonus\", function: \"modifyForge\" })");
        scriptMethod("unregisterForgeRule", "unregisterForgeRule(id)", "unregisterForgeRule(\"moon_phase_bonus\")");
        scriptMethod("registeredForgeRules", "registeredForgeRules()", "registeredForgeRules()");
        scriptMethod("onResult", "onResult(definition)", "onResult({ id: \"forge_result\", function: \"handleResult\" })");
        scriptMethod("unregisterResultHook", "unregisterResultHook(id)", "unregisterResultHook(\"forge_result\")");
        scriptMethod("registeredResultHooks", "registeredResultHooks()", "registeredResultHooks()");
    }

    private void scriptMethod(String label, String detail, String apply) {
        try {
            WebConsoleRegistry.class.getMethod("registerJavaScriptMethod", String.class, String.class, String.class, String.class, String.class, String.class)
                    .invoke(null, getName(), "module:forge", label, detail, apply, "function");
        } catch (NoSuchMethodException ignored) {
            // Older CoreLib builds do not expose JavaScript completion registration; completions are optional.
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Failed to register JavaScript completion " + label + ": " + exception.getMessage());
        }
    }

    private void registerPublicApiService() {
        EmakiForgeApi.install(forgeApiBridge);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ForgePlaceholderExpansion(this, playerDataStore);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public RecipeLoader recipeLoader() {
        return recipeLoader;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public PlayerDataStore playerDataStore() {
        return playerDataStore;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public GuiService guiService() {
        return guiService;
    }

    public ItemIdentifierService itemIdentifierService() {
        return itemIdentifierService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public ForgeItemRefreshService itemRefreshService() {
        return itemRefreshService;
    }

    public ForgeService forgeService() {
        return forgeService;
    }

    public ForgeGuiService forgeGuiService() {
        return forgeGuiService;
    }

    public RecipeBookGuiService recipeBookGuiService() {
        return recipeBookGuiService;
    }

    public JavaScriptForgeRuleRegistry javaScriptForgeRuleRegistry() {
        return javaScriptForgeRuleRegistry;
    }

    public JavaScriptForgeResultHookRegistry javaScriptResultHookRegistry() {
        return javaScriptResultHookRegistry;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}

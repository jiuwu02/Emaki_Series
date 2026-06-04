package emaki.jiuwu.craft.level;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.action.LevelActionRegistrar;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.apiimpl.DefaultEmakiLevelApi;
import emaki.jiuwu.craft.level.bridge.MythicLevelDropBridge;
import emaki.jiuwu.craft.level.command.LevelCommand;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.listener.BlockSourceListener;
import emaki.jiuwu.craft.level.listener.BrewingSourceListener;
import emaki.jiuwu.craft.level.listener.CombatSourceListener;
import emaki.jiuwu.craft.level.listener.CraftingSourceListener;
import emaki.jiuwu.craft.level.listener.FishingSourceListener;
import emaki.jiuwu.craft.level.listener.PlayerDataListener;
import emaki.jiuwu.craft.level.listener.SmeltingSourceListener;
import emaki.jiuwu.craft.level.listener.TamingSourceListener;
import emaki.jiuwu.craft.level.loader.LevelTypeLoader;
import emaki.jiuwu.craft.level.loader.RequirementLoader;
import emaki.jiuwu.craft.level.loader.SourceRuleLoader;
import emaki.jiuwu.craft.level.papi.LevelPlaceholderExpansion;
import emaki.jiuwu.craft.level.service.LevelAttributeBridge;
import emaki.jiuwu.craft.level.service.LevelMessageService;
import emaki.jiuwu.craft.level.service.LevelPdcService;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.LevelTypeRegistry;
import emaki.jiuwu.craft.level.service.PlayerLevelDataStore;
import emaki.jiuwu.craft.level.service.PlayerLevelService;
import emaki.jiuwu.craft.level.service.RequirementService;

public final class EmakiLevelPlugin extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 31794;
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __      ______  __   ________  __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\    /\\  ___\\/\\ \\ / /\\  ___\\/\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\  __\\\\ \\ \\'/\\ \\  __\\\\ \\ \\____
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\__| \\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/   \\/_____/\\/_____/
""";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("web-console.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of(
            "requirements.yml",
            "gui/level_gui.yml",
            "types/main.yml",
            "types/combat.yml",
            "types/mining.yml",
            "types/logging.yml",
            "types/farming.yml",
            "types/fishing.yml",
            "types/gathering.yml",
            "types/crafting.yml",
            "types/brewing.yml",
            "types/cooking.yml",
            "types/forging.yml",
            "types/taming.yml",
            "types/smelting.yml",
            "sources/combat.yml",
            "sources/block_actions.yml",
            "sources/fishing.yml",
            "sources/crafting.yml",
            "sources/brewing.yml",
            "sources/taming.yml",
            "sources/smelting.yml",
            "sources/mythicmobs.yml"
    );
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    private EmakiCoreLibPlugin coreLib;
    private AppConfig appConfig = AppConfig.defaults();
    private LevelMessageService messages;
    private BootstrapService bootstrapService;
    private LevelTypeLoader typeLoader;
    private RequirementLoader requirementLoader;
    private SourceRuleLoader sourceRuleLoader;
    private LevelTypeRegistry typeRegistry;
    private RequirementService requirementService;
    private PlayerLevelDataStore dataStore;
    private LevelPdcService pdcService;
    private PlayerLevelService levelService;
    private LevelTopService topService;
    private LevelAttributeBridge attributeBridge;
    private MythicLevelDropBridge mythicDropBridge;
    private LevelPlaceholderExpansion placeholderExpansion;
    private EmakiLevelApi api;
    private BStatsRegistration metrics;

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        initializeServices();
        messages.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        registerCommand();
        registerListeners();
        registerApi();
        registerActions();
        registerWebConsole();
        registerPlaceholderExpansion();
        registerAttributeBridge();
        registerMythicDrops();
        metrics = coreLib.registerBStats(this, BSTATS_PLUGIN_ID);
        messages.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (attributeBridge != null) {
            attributeBridge.unregister();
        }
        if (mythicDropBridge != null) {
            HandlerList.unregisterAll(mythicDropBridge);
            mythicDropBridge = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        if (api != null) {
            getServer().getServicesManager().unregister(EmakiLevelApi.class, api);
            api = null;
        }
        if (dataStore != null) {
            dataStore.saveAll();
        }
        if (coreLib != null && coreLib.actionRegistry() != null) {
            coreLib.actionRegistry().unregisterAll(this);
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (messages != null) {
            messages.info("console.plugin_stopped");
        }
        AdventureSupport.close(this);
    }

    public void reloadPluginState() {
        appConfig = AppConfig.parse(YamlFiles.load(getDataFolder().toPath().resolve("config.yml").toFile()));
        messages.load(appConfig.language());
        typeLoader.load(appConfig);
        requirementLoader.load();
        sourceRuleLoader.load();
        typeRegistry.reload(typeLoader.types());
        requirementService.reload(requirementLoader.config());
        pdcService.enabled(appConfig.pdcEnabled());
        levelService.config(appConfig);
        if (attributeBridge != null) {
            attributeBridge.config(appConfig);
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            dataStore.load(player, typeRegistry.asMap());
        }
        levelService.syncAllOnline();
        messages.info("console.types_loaded", Map.of("count", String.valueOf(typeRegistry.all().size())));
        messages.info("console.sources_loaded", Map.of("count", String.valueOf(sourceRuleLoader.rules().size())));
    }

    private void initializeServices() {
        messages = new LevelMessageService(this);
        messages.load(appConfig.language());
        bootstrapService = new BootstrapService(
                this,
                messages,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig runtimeConfig = AppConfig.parse(YamlFiles.load(getDataFolder().toPath().resolve("config.yml").toFile()));
                        return runtimeConfig.releaseDefaultData();
                    }

                    @Override
                    public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                        if (relativePath == null || !relativePath.startsWith("lang/") || runtime == null || bundled == null) {
                            return;
                        }
                        YamlSection consoleDefaults = bundled.getSection("console");
                        if (consoleDefaults != null) {
                            runtime.set("console", consoleDefaults.asMap());
                        }
                    }
                }
        );
        typeLoader = new LevelTypeLoader(this);
        requirementLoader = new RequirementLoader(this);
        sourceRuleLoader = new SourceRuleLoader(this);
        typeRegistry = new LevelTypeRegistry();
        requirementService = new RequirementService();
        dataStore = new PlayerLevelDataStore(this);
        pdcService = new LevelPdcService(appConfig.pdcNamespace(), appConfig.pdcEnabled());
        attributeBridge = new LevelAttributeBridge(this, typeRegistry, dataStore, appConfig);
        levelService = new PlayerLevelService(
                this,
                typeRegistry,
                requirementService,
                dataStore,
                pdcService,
                coreLib.itemSourceService(),
                coreLib.economyManager(),
                coreLib.actionExecutor(),
                appConfig,
                () -> attributeBridge.resyncAll(),
                player -> attributeBridge.resync(player)
        );
        topService = new LevelTopService(dataStore);
    }

    private void registerCommand() {
        LevelCommand command = new LevelCommand(this);
        PluginCommand pluginCommand = getCommand("emakilevel");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new BrewingSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new SmeltingSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new TamingSourceListener(this), this);
    }

    private void registerApi() {
        api = new DefaultEmakiLevelApi(this);
        getServer().getServicesManager().register(EmakiLevelApi.class, api, this, ServicePriority.Normal);
    }

    private void registerActions() {
        new LevelActionRegistrar(this).register(coreLib.actionRegistry());
        messages.info("console.actions_registered");
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new LevelPlaceholderExpansion(this);
        placeholderExpansion.register();
        messages.info("console.papi_registered");
    }

    private void registerAttributeBridge() {
        if (attributeBridge.register()) {
            messages.info("console.attribute_bridge_ready");
        } else if (appConfig.attributeEnabled()) {
            messages.info("console.attribute_bridge_unavailable");
        }
    }

    private void registerMythicDrops() {
        if (!appConfig.mythicEnabled() || !appConfig.mythicDropsEnabled() || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicDropBridge = new MythicLevelDropBridge(this);
        getServer().getPluginManager().registerEvents(mythicDropBridge, this);
        messages.info("console.mythic_drops_registered");
    }

    public AppConfig appConfig() {
        return appConfig;
    }

    public LevelMessageService messages() {
        return messages;
    }

    public LevelTypeRegistry typeRegistry() {
        return typeRegistry;
    }

    public RequirementService requirementService() {
        return requirementService;
    }

    public SourceRuleLoader sourceRuleLoader() {
        return sourceRuleLoader;
    }

    public PlayerLevelDataStore dataStore() {
        return dataStore;
    }

    public PlayerLevelService levelService() {
        return levelService;
    }

    public LevelTopService topService() {
        return topService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return coreLib;
    }
}

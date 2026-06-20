package emaki.jiuwu.craft.level;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebPluginApiRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.action.LevelActionRegistrar;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelTypeView;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.api.PlayerLevelEntryView;
import emaki.jiuwu.craft.level.api.PlayerLevelView;
import emaki.jiuwu.craft.level.bridge.MythicLevelDropBridge;
import emaki.jiuwu.craft.level.command.LevelCommand;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelConfigPrecheckContributor;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.listener.BlockSourceListener;
import emaki.jiuwu.craft.level.listener.BrewingSourceListener;
import emaki.jiuwu.craft.level.listener.CombatSourceListener;
import emaki.jiuwu.craft.level.listener.CraftingSourceListener;
import emaki.jiuwu.craft.level.listener.FishingSourceListener;
import emaki.jiuwu.craft.level.listener.PlayerDataListener;
import emaki.jiuwu.craft.level.listener.SmeltingSourceListener;
import emaki.jiuwu.craft.level.listener.TamingSourceListener;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.loader.LevelTypeLoader;
import emaki.jiuwu.craft.level.loader.RequirementLoader;
import emaki.jiuwu.craft.level.loader.SourceRuleLoader;
import emaki.jiuwu.craft.level.papi.LevelPlaceholderExpansion;
import emaki.jiuwu.craft.level.service.LevelAntiAbuseService;
import emaki.jiuwu.craft.level.service.LevelAttributeBridge;
import emaki.jiuwu.craft.level.service.LevelCurveService;
import emaki.jiuwu.craft.level.service.LevelExperienceRuleService;
import emaki.jiuwu.craft.level.service.LevelGuiService;
import emaki.jiuwu.craft.level.service.LevelMessageService;
import emaki.jiuwu.craft.level.service.LevelPdcService;
import emaki.jiuwu.craft.level.service.LevelTopGuiService;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.LevelTypeRegistry;
import emaki.jiuwu.craft.level.service.PlayerLevelDataStore;
import emaki.jiuwu.craft.level.service.PlayerLevelService;
import emaki.jiuwu.craft.level.service.RequirementService;
import emaki.jiuwu.craft.level.script.ScriptLevelModuleApi;

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
            "gui/top_gui.yml",
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
    private GuiTemplateLoader guiTemplateLoader;
    private GuiService guiService;
    private LevelTypeRegistry typeRegistry;
    private RequirementService requirementService;
    private LevelCurveService curveService;
    private PlayerLevelDataStore dataStore;
    private LevelPdcService pdcService;
    private LevelExperienceRuleService experienceRuleService;
    private LevelAntiAbuseService antiAbuseService;
    private PlayerLevelService levelService;
    private LevelTopService topService;
    private LevelGuiService levelGuiService;
    private LevelTopGuiService levelTopGuiService;
    private LevelAttributeBridge attributeBridge;
    private MythicLevelDropBridge mythicDropBridge;
    private LevelPlaceholderExpansion placeholderExpansion;
    private BStatsRegistration metrics;
    private final EmakiLevelApi.Bridge levelApiBridge = new EmakiLevelApi.Bridge() {
        @Override
        public Optional<LevelTypeView> type(String typeId) {
            return typeRegistry.type(typeId).map(EmakiLevelPlugin.this::view);
        }

        @Override
        public Collection<LevelTypeView> types() {
            return typeRegistry.all().stream().map(EmakiLevelPlugin.this::view).toList();
        }

        @Override
        public CompletableFuture<PlayerLevelView> getPlayerData(UUID uuid) {
            return CompletableFuture.completedFuture(playerView(dataStore.getOrLoad(uuid, typeRegistry.asMap())));
        }

        @Override
        public int getLevel(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0 : entry.level();
        }

        @Override
        public double getExp(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0D : entry.exp();
        }

        @Override
        public double getTotalExp(UUID uuid, String typeId) {
            PlayerLevelEntry entry = entry(uuid, typeId);
            return entry == null ? 0D : entry.totalExp();
        }

        @Override
        public double getRequiredExp(UUID uuid, String typeId, int targetLevel) {
            LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
            if (type == null) {
                return 0D;
            }
            return requirementService.requiredExp(type, entry(uuid, typeId), targetLevel);
        }

        @Override
        public LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason) {
            return levelService.addExp(uuid, typeId, amount, reason);
        }

        @Override
        public LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason) {
            return levelService.removeExp(uuid, typeId, amount, reason);
        }

        @Override
        public LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason) {
            return levelService.setExp(uuid, typeId, amount, reason);
        }

        @Override
        public LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason) {
            return levelService.addLevel(uuid, typeId, amount, reason);
        }

        @Override
        public LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason) {
            return levelService.removeLevel(uuid, typeId, amount, reason);
        }

        @Override
        public LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason) {
            return levelService.setLevel(uuid, typeId, level, reason);
        }

        @Override
        public LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause) {
            return levelService.levelUp(uuid, typeId, cause);
        }
    };

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        initializeServices();
        registerConfigPrecheckContributor();
        messages.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        registerCommand();
        registerListeners();
        registerApi();
        registerScriptModule();
        releaseBundledScripts();
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
        if (coreLib != null) {
            ConfigPrecheckLifecycleSupport.unregister("level");
            coreLib.scriptModuleRegistry().unregister("level");
        }
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
        WebPluginApiRegistry.unregister(this);
        EmakiLevelApi.uninstall(levelApiBridge);
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
        guiTemplateLoader.load();
        typeRegistry.reload(typeLoader.types());
        requirementService.reload(requirementLoader.config());
        pdcService.enabled(appConfig.pdcEnabled());
        antiAbuseService.config(appConfig);
        experienceRuleService.config(appConfig);
        experienceRuleService.clearExpired();
        levelService.config(appConfig);
        if (attributeBridge != null) {
            attributeBridge.config(appConfig);
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            dataStore.load(player, typeRegistry.asMap());
        }
        topService.rebuild();
        levelService.syncAllOnline();
        messages.info("console.types_loaded", Map.of("count", String.valueOf(typeRegistry.all().size())));
        messages.info("console.sources_loaded", Map.of("count", String.valueOf(sourceRuleLoader.rules().size())));
        logConfigPrecheckReport();
    }

    private void logConfigPrecheckReport() {
        ConfigPrecheckLifecycleSupport.logReport(messages, "level");
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new LevelConfigPrecheckContributor(this));
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
        guiTemplateLoader = new GuiTemplateLoader(this);
        guiService = new GuiService(this, coreLib.asyncTaskScheduler(), coreLib.performanceMonitor());
        typeRegistry = new LevelTypeRegistry();
        requirementService = new RequirementService();
        curveService = new LevelCurveService(typeRegistry, requirementService);
        dataStore = new PlayerLevelDataStore(this);
        pdcService = new LevelPdcService(appConfig.pdcNamespace(), appConfig.pdcEnabled());
        experienceRuleService = new LevelExperienceRuleService();
        experienceRuleService.config(appConfig);
        antiAbuseService = new LevelAntiAbuseService(appConfig);
        attributeBridge = new LevelAttributeBridge(this, typeRegistry, dataStore, appConfig);
        topService = new LevelTopService(dataStore, typeRegistry);
        levelService = new PlayerLevelService(
                this,
                typeRegistry,
                requirementService,
                dataStore,
                pdcService,
                experienceRuleService,
                coreLib.itemSourceService(),
                coreLib.economyManager(),
                coreLib.actionExecutor(),
                appConfig,
                () -> attributeBridge.resyncAll(),
                player -> attributeBridge.resync(player),
                data -> topService.update(data)
        );
        levelGuiService = new LevelGuiService(this, guiService, guiTemplateLoader);
        levelTopGuiService = new LevelTopGuiService(this, guiService, guiTemplateLoader);
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
        getServer().getPluginManager().registerEvents(guiService, this);
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
        EmakiLevelApi.install(levelApiBridge);
    }

    private void registerScriptModule() {
        coreLib.scriptModuleRegistry().register("level", context -> new ScriptLevelModuleApi());
    }

    private void releaseBundledScripts() {
        coreLib.releaseBundledScripts(this, "examples", false, List.of("level_status.js"));
    }

    private void registerActions() {
        new LevelActionRegistrar(this).register(coreLib.actionRegistry());
        messages.info("console.actions_registered");
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
        WebPluginApiRegistry.register(this, "level", "curve", request -> {
            request.requirePost();
            return curveService.curves(request.stringList("types"), request.integer("fromLevel", 1), request.integer("toLevel", 0));
        });
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

    private PlayerLevelEntry entry(UUID uuid, String typeId) {
        if (uuid == null) {
            return null;
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        return data.entry(emaki.jiuwu.craft.corelib.text.Texts.normalizeId(typeId));
    }

    private LevelTypeView view(LevelTypeConfig type) {
        return new LevelTypeView(type.id(), type.displayName(), type.description(), type.primary(), type.enabled(), type.startLevel(), type.maxLevel(), type.upgrade().autoUpgrade(), type.upgrade().manualUpgrade(), type.attributes());
    }

    private PlayerLevelView playerView(PlayerLevelData data) {
        Map<String, PlayerLevelEntryView> entries = new LinkedHashMap<>();
        for (LevelTypeConfig type : typeRegistry.all()) {
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry == null) {
                continue;
            }
            double required = requirementService.requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
            double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
            entries.put(type.id(), new PlayerLevelEntryView(type.id(), entry.level(), entry.exp(), entry.totalExp(), required, progress));
        }
        return new PlayerLevelView(data.uuid(), data.name(), entries);
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

    public LevelAntiAbuseService antiAbuseService() {
        return antiAbuseService;
    }

    public LevelGuiService levelGuiService() {
        return levelGuiService;
    }

    public LevelTopGuiService levelTopGuiService() {
        return levelTopGuiService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return coreLib;
    }
}

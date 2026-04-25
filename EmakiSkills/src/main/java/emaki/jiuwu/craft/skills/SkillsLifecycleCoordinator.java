package emaki.jiuwu.craft.skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.MythicBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.gui.SkillsGuiService;
import emaki.jiuwu.craft.skills.loader.LocalResourceDefinitionLoader;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.provider.SkillSourceRegistry;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

final class SkillsLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiSkillsPlugin, SkillsRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#4DA6FF:#FF6B6B>主动技能</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public SkillsRuntimeComponents initialize(EmakiSkillsPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                "config_version",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        SkillDefinitionLoader skillDefinitionLoader = new SkillDefinitionLoader(plugin);
        LocalResourceDefinitionLoader localResourceDefinitionLoader = new LocalResourceDefinitionLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                List.of(),
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor());
        EquipmentSkillCollector equipmentSkillCollector = new EquipmentSkillCollector(plugin, () -> skillDefinitionLoader.all());
        SkillSourceRegistry skillSourceRegistry = new SkillSourceRegistry();
        TriggerRegistry triggerRegistry = new TriggerRegistry();
        TriggerConflictResolver triggerConflictResolver = new TriggerConflictResolver();
        SkillRegistryService skillRegistryService = new SkillRegistryService(plugin, () -> skillDefinitionLoader.all());
        PlayerSkillDataStore playerSkillDataStore = new PlayerSkillDataStore(
                plugin,
                appConfigLoader.current().defaultSlotCount()
        );
        EaBridge eaBridge = new EaBridge(plugin, messageService);
        eaBridge.init();
        MythicBridge mythicBridge = new MythicBridge(plugin, messageService);
        mythicBridge.init();
        PlayerSkillStateService playerSkillStateService = new PlayerSkillStateService(
                plugin,
                playerSkillDataStore,
                skillRegistryService,
                equipmentSkillCollector,
                skillSourceRegistry,
                triggerConflictResolver,
                triggerRegistry
        );
        SkillLevelService skillLevelService = new SkillLevelService(playerSkillDataStore);
        SkillParameterResolver skillParameterResolver = new SkillParameterResolver(skillLevelService);
        CastModeService castModeService = new CastModeService(playerSkillDataStore);
        MythicSkillCastService mythicSkillCastService = new MythicSkillCastService(mythicBridge);
        CastAttemptService castAttemptService = new CastAttemptService(
                plugin,
                playerSkillStateService,
                castModeService,
                playerSkillDataStore,
                mythicSkillCastService,
                skillParameterResolver,
                eaBridge,
                () -> localResourceDefinitionLoader.all(),
                plugin::appConfig
        );
        SkillUpgradeService skillUpgradeService = new SkillUpgradeService(
                plugin,
                playerSkillStateService,
                playerSkillDataStore,
                skillLevelService,
                skillParameterResolver,
                coreLibPlugin::economyManager,
                coreLibPlugin.itemSourceService(),
                coreLibPlugin::actionExecutor
        );
        ActionBarService actionBarService = new ActionBarService(
                plugin,
                playerSkillDataStore,
                castModeService,
                plugin::appConfig,
                triggerRegistry,
                () -> skillDefinitionLoader.all()
        );
        SkillsGuiService skillsGuiService = new SkillsGuiService(
                plugin, guiService, guiTemplateLoader,
                playerSkillStateService, playerSkillDataStore,
                skillRegistryService, triggerRegistry,
                castModeService, skillLevelService, skillParameterResolver, messageService);
        return new SkillsRuntimeComponents(
                appConfigLoader,
                languageLoader,
                skillDefinitionLoader,
                localResourceDefinitionLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                equipmentSkillCollector,
                skillSourceRegistry,
                triggerRegistry,
                triggerConflictResolver,
                skillRegistryService,
                playerSkillDataStore,
                playerSkillStateService,
                skillLevelService,
                skillParameterResolver,
                skillUpgradeService,
                castModeService,
                castAttemptService,
                mythicSkillCastService,
                actionBarService,
                skillsGuiService,
                eaBridge,
                mythicBridge
        );
    }

    public void reload(EmakiSkillsPlugin plugin, boolean closeInventories) {
        if (closeInventories) {
            Bukkit.getOnlinePlayers().forEach(player -> player.closeInventory());
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.skillDefinitionLoader().load();
        plugin.localResourceDefinitionLoader().load();
        plugin.guiTemplateLoader().load();
        loadTriggersIntoRegistry(plugin);
        plugin.triggerConflictResolver().buildFromDefinitions(plugin.triggerRegistry().all());
        Bukkit.getOnlinePlayers().forEach(player -> plugin.playerSkillStateService().validateBindings(player));
        plugin.actionBarService().startRefreshTask();
    }

    public void shutdown(EmakiSkillsPlugin plugin) {
        if (plugin.actionBarService() != null) {
            plugin.actionBarService().stopRefreshTask();
        }
        if (plugin.playerSkillDataStore() != null) {
            plugin.playerSkillDataStore().saveAll();
            plugin.playerSkillDataStore().unloadAll();
        }
        if (plugin.eaBridge() != null) {
            plugin.eaBridge().shutdown();
        }
        if (plugin.mythicBridge() != null) {
            plugin.mythicBridge().shutdown();
        }
        if (plugin.skillsGuiService() != null) {
            plugin.skillsGuiService().clearAllSessions();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();

        // Slots
        YamlSection slotsSection = configuration.getSection("slots");
        int defaultSlotCount = slotsSection != null
                ? intValue(slotsSection.getInt("default_count", defaults.defaultSlotCount()), defaults.defaultSlotCount())
                : defaults.defaultSlotCount();

        // Cast mode
        YamlSection castModeSection = configuration.getSection("cast_mode");
        AppConfig.CastModeSettings castMode;
        if (castModeSection == null) {
            castMode = defaults.castMode();
        } else {
            castMode = new AppConfig.CastModeSettings(
                    castModeSection.getString("entry_key", defaults.castMode().entryKey()),
                    boolValue(castModeSection.getBoolean("restore_last_state_on_join"), defaults.castMode().restoreLastStateOnJoin())
            );
        }

        // Cast timing
        YamlSection castTimingSection = configuration.getSection("cast_timing");
        AppConfig.CastTimingSettings castTiming;
        if (castTimingSection == null) {
            castTiming = defaults.castTiming();
        } else {
            castTiming = new AppConfig.CastTimingSettings(
                    intValue(castTimingSection.getInt("forced_global_cast_delay_ticks"), (int) defaults.castTiming().forcedGlobalCastDelayTicks())
            );
        }

        // Action bar
        YamlSection actionBarSection = configuration.getSection("actionbar");
        AppConfig.ActionBarSettings actionBar;
        if (actionBarSection == null) {
            actionBar = defaults.actionBar();
        } else {
            actionBar = new AppConfig.ActionBarSettings(
                    boolValue(actionBarSection.getBoolean("enabled"), defaults.actionBar().enabled()),
                    intValue(actionBarSection.getInt("refresh_interval_ticks"), defaults.actionBar().refreshIntervalTicks()),
                    actionBarSection.getString("template_cast_mode", defaults.actionBar().templateCastMode()),
                    actionBarSection.getString("template_idle", defaults.actionBar().templateIdle())
            );
        }

        // Triggers
        Map<String, AppConfig.TriggerConfig> triggers = parseTriggers(configuration.getSection("triggers"));
        Map<String, AppConfig.TriggerConfig> passiveTriggers = parseTriggers(configuration.getSection("passive_triggers"));
        YamlSection passiveTriggerSettingsSection = configuration.getSection("passive_trigger_settings");
        AppConfig.PassiveTriggerSettings passiveTriggerSettings = passiveTriggerSettingsSection == null
                ? defaults.passiveTriggerSettings()
                : new AppConfig.PassiveTriggerSettings(
                        intValue(passiveTriggerSettingsSection.getInt("timer_interval_ticks"),
                                (int) defaults.passiveTriggerSettings().timerIntervalTicks())
                );

        return new AppConfig(
                configuration.getString("language", defaults.language()),
                configuration.getString("config_version", "1.0.0"),
                boolValue(configuration.getBoolean("release_default_data"), defaults.releaseDefaultData()),
                defaultSlotCount,
                castMode,
                castTiming,
                actionBar,
                triggers,
                passiveTriggers,
                passiveTriggerSettings
        );
    }

    private static int intValue(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static boolean boolValue(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private Map<String, AppConfig.TriggerConfig> parseTriggers(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<String, AppConfig.TriggerConfig> triggers = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            YamlSection triggerSection = section.getSection(key);
            if (triggerSection == null) {
                continue;
            }
            String displayName = triggerSection.getString("display_name", key);
            boolean enabled = triggerSection.getBoolean("enabled", true);
            List<String> incompatibleWith = triggerSection.getStringList("incompatible_with");
            triggers.put(key, new AppConfig.TriggerConfig(displayName, enabled, incompatibleWith));
        }
        return Map.copyOf(triggers);
    }

    private void loadTriggersIntoRegistry(EmakiSkillsPlugin plugin) {
        TriggerRegistry registry = plugin.triggerRegistry();
        registry.clear();

        // Load built-in defaults first
        for (SkillTriggerDefinition def : TriggerRegistry.defaultDefinitions()) {
            registry.register(def);
        }
        for (SkillTriggerDefinition def : TriggerRegistry.defaultPassiveDefinitions()) {
            registry.register(def);
        }

        // Override with config-defined triggers
        Map<String, AppConfig.TriggerConfig> configTriggers = plugin.appConfig().triggers();
        for (Map.Entry<String, AppConfig.TriggerConfig> entry : configTriggers.entrySet()) {
            String id = entry.getKey();
            AppConfig.TriggerConfig tc = entry.getValue();
            Set<String> incompatible = tc.incompatibleWith() == null
                    ? Set.of()
                    : new HashSet<>(tc.incompatibleWith());
            registry.register(new SkillTriggerDefinition(
                    id,
                    tc.displayName(),
                    null,
                    tc.enabled(),
                    incompatible,
                    null
            ));
        }

        Map<String, AppConfig.TriggerConfig> configPassiveTriggers = plugin.appConfig().passiveTriggers();
        for (Map.Entry<String, AppConfig.TriggerConfig> entry : configPassiveTriggers.entrySet()) {
            String id = entry.getKey();
            AppConfig.TriggerConfig tc = entry.getValue();
            Set<String> incompatible = tc.incompatibleWith() == null
                    ? Set.of()
                    : new HashSet<>(tc.incompatibleWith());
            registry.register(new SkillTriggerDefinition(
                    id,
                    tc.displayName(),
                    null,
                    tc.enabled(),
                    incompatible,
                    null,
                    emaki.jiuwu.craft.skills.trigger.TriggerCategory.PASSIVE
            ));
        }
    }

    private boolean shouldReleaseDefaultData(EmakiSkillsPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private List<String> staticFiles(EmakiSkillsPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "gui"));
        return List.copyOf(files);
    }

    private List<String> defaultDataFiles(EmakiSkillsPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "skills"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "resources"));
        return List.copyOf(files);
    }
}

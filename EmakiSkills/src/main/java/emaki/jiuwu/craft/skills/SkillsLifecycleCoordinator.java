package emaki.jiuwu.craft.skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.ExternalManaBridge;
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
import emaki.jiuwu.craft.skills.service.ManualSkillSourceService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.service.SkillRegistryService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.skills.script.SkillPipelineRuntime;
import emaki.jiuwu.craft.skills.script.SkillScriptCastService;
import emaki.jiuwu.craft.skills.script.SkillVariableResolver;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerConflictResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

final class SkillsLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiSkillsPlugin, SkillsRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#38BDF8:#8B5CF6>EmakiSkills</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public SkillsRuntimeComponents initialize(EmakiSkillsPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        SkillDefinitionLoader skillDefinitionLoader = new SkillDefinitionLoader(plugin);
        LocalResourceDefinitionLoader localResourceDefinitionLoader = new LocalResourceDefinitionLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        EquipmentSkillCollector equipmentSkillCollector = new EquipmentSkillCollector(
                plugin,
                () -> skillDefinitionLoader.all(),
                plugin::appConfig,
                plugin::eaBridge
        );
        SkillSourceRegistry skillSourceRegistry = new SkillSourceRegistry();
        TriggerRegistry triggerRegistry = new TriggerRegistry();
        TriggerConflictResolver triggerConflictResolver = new TriggerConflictResolver();
        SkillRegistryService skillRegistryService = new SkillRegistryService(plugin, () -> skillDefinitionLoader.all());
        AsyncYamlFiles asyncYamlFiles = coreLibPlugin.asyncYamlFiles(plugin);
        PlayerSkillDataStore playerSkillDataStore = new PlayerSkillDataStore(
                plugin,
                appConfigLoader.current().defaultSlotCount(),
                () -> asyncYamlFiles
        );
        ManualSkillSourceService manualSkillSourceService = new ManualSkillSourceService(playerSkillDataStore, skillRegistryService);
        skillSourceRegistry.register(plugin, manualSkillSourceService);
        EaBridge eaBridge = new EaBridge(plugin, messageService);
        eaBridge.init();
        ExternalManaBridge externalManaBridge = new ExternalManaBridge(plugin, messageService);
        externalManaBridge.init();
        MythicBridge mythicBridge = new MythicBridge(plugin, messageService);
        mythicBridge.init();
        PlayerSkillStateService playerSkillStateService = new PlayerSkillStateService(
                plugin,
                playerSkillDataStore,
                skillRegistryService,
                equipmentSkillCollector,
                skillSourceRegistry,
                triggerConflictResolver,
                triggerRegistry,
                plugin::appConfig
        );
        SkillLevelService skillLevelService = new SkillLevelService(playerSkillDataStore);
        SkillParameterResolver skillParameterResolver = new SkillParameterResolver(skillLevelService, plugin);
        CastModeService castModeService = new CastModeService(playerSkillDataStore);
        MythicSkillCastService mythicSkillCastService = new MythicSkillCastService(mythicBridge);
        SkillVariableResolver skillVariableResolver = new SkillVariableResolver(skillLevelService, skillParameterResolver);
        SkillPipelineRuntime skillPipelineRuntime = new SkillPipelineRuntime(plugin);
        SkillScriptCastService skillScriptCastService = new SkillScriptCastService(plugin, skillVariableResolver, skillPipelineRuntime);
        CastAttemptService castAttemptService = new CastAttemptService(
                plugin,
                playerSkillStateService,
                castModeService,
                playerSkillDataStore,
                mythicSkillCastService,
                skillScriptCastService,
                skillParameterResolver,
                eaBridge,
                externalManaBridge,
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
                plugin.actionLines()
        );
        ActionBarService actionBarService = new ActionBarService(
                plugin,
                playerSkillDataStore,
                castModeService,
                plugin::appConfig,
                triggerRegistry,
                () -> skillDefinitionLoader.all(),
                messageService,
                executionDispatcher
        );
        SkillsGuiService skillsGuiService = new SkillsGuiService(
                plugin, guiService, guiTemplateLoader,
                playerSkillStateService, playerSkillDataStore,
                skillRegistryService, triggerRegistry,
                castModeService, skillLevelService, skillParameterResolver,
                skillUpgradeService, messageService);
        return new SkillsRuntimeComponents(
                appConfigLoader,
                executionDispatcher,
                threadOwnership,
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
                manualSkillSourceService,
                playerSkillStateService,
                skillLevelService,
                skillParameterResolver,
                skillVariableResolver,
                skillPipelineRuntime,
                skillScriptCastService,
                skillUpgradeService,
                castModeService,
                castAttemptService,
                mythicSkillCastService,
                actionBarService,
                skillsGuiService,
                eaBridge,
                externalManaBridge,
                mythicBridge
        );
    }

    public void reload(EmakiSkillsPlugin plugin, boolean closeInventories) {
        if (closeInventories) {
            forEachOnlinePlayer(plugin, Player::closeInventory).join();
        }
        // Definition loaders belong to the candidate step because the skills precheck reads their issue
        // lists; the apply work below only runs once the gate has accepted that candidate.
        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                plugin.messageService(),
                "skills",
                plugin.appConfigLoader()::current,
                () -> {
                    plugin.languageLoader().load();
                    AppConfig candidate = plugin.appConfigLoader().load();
                    plugin.skillDefinitionLoader().load();
                    plugin.localResourceDefinitionLoader().load();
                    plugin.guiTemplateLoader().load();
                    return candidate;
                },
                plugin.appConfigLoader()::overrideCurrent);
        if (gate.rejected()) {
            // Previous AppConfig is active again and no candidate value reached a runtime service.
            return;
        }
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        // The skill YAML was just reread, so every compiled pipeline was built from text that may no longer be
        // configured. Dropping the cache here is what makes the next cast compile the current lines.
        plugin.skillPipelineRuntime().invalidateAll();
        loadTriggersIntoRegistry(plugin);
        plugin.triggerConflictResolver().buildFromDefinitions(plugin.triggerRegistry().all());
        forEachOnlinePlayer(plugin, plugin.playerSkillStateService()::validateBindings).join();
        plugin.actionBarService().startRefreshTask();
        plugin.messageService().info("console.skills_loaded", Map.of(
                "skills", String.valueOf(plugin.skillDefinitionLoader().all().size()),
                "triggers", String.valueOf(plugin.triggerRegistry().all().size())
        ));
    }

    public CompletableFuture<Void> reloadAsync(EmakiSkillsPlugin plugin, boolean closeInventories, Consumer<String> progressListener) {
        emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (scheduler == null) {
            reload(plugin, closeInventories);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> closeStage = closeInventories
                ? forEachOnlinePlayer(plugin, Player::closeInventory)
                : CompletableFuture.completedFuture(null);
        notifyProgress(progressListener, plugin.messageService().message("console.reload_loading_files"));

        return closeStage
                .thenCompose(ignored -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                        "skills", "config-load", plugin.messageService().message("console.reload_loading_configs"), progressListener,
                        () -> {
                            ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                                    plugin.messageService(),
                                    "skills",
                                    plugin.appConfigLoader()::current,
                                    () -> {
                                        plugin.languageLoader().load();
                                        AppConfig candidate = plugin.appConfigLoader().load();
                                        plugin.skillDefinitionLoader().load();
                                        plugin.localResourceDefinitionLoader().load();
                                        plugin.guiTemplateLoader().load();
                                        return candidate;
                                    },
                                    plugin.appConfigLoader()::overrideCurrent);
                            if (gate.rejected()) {
                                // Aborts the stage so the apply step never runs; AppConfig is already restored.
                                throw new IllegalStateException("Skills config precheck failed: "
                                        + String.join("; ", gate.failures()));
                            }
                        },
                        null,
                        (stage, ex) -> plugin.getLogger().warning(
                                "[Reload] Stage " + stage + " failed: " + ex.getMessage())
                )))
                .thenCompose(ignored -> {
                    notifyProgress(progressListener, plugin.messageService().message("console.reload_applying"));
                    return plugin.executionDispatcher().submitGlobal(plugin, () -> {
                        plugin.languageLoader().setLanguage(plugin.appConfig().language());
                        plugin.skillPipelineRuntime().invalidateAll();
                        loadTriggersIntoRegistry(plugin);
                        plugin.triggerConflictResolver().buildFromDefinitions(plugin.triggerRegistry().all());
                        return null;
                    });
                })
                .thenCompose(ignored -> forEachOnlinePlayer(
                        plugin, plugin.playerSkillStateService()::validateBindings))
                .thenRun(() -> {
                    plugin.actionBarService().startRefreshTask();
                    notifyProgress(progressListener, plugin.messageService().message("console.reload_complete"));
                });
    }

    private CompletableFuture<Void> forEachOnlinePlayer(EmakiSkillsPlugin plugin, Consumer<Player> action) {
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        List<CompletableFuture<Void>> tasks = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Void> task = new CompletableFuture<>();
            tasks.add(task);
            try {
                var scheduled = plugin.executionDispatcher().runEntity(plugin, player, () -> {
                    try {
                        if (player.isOnline()) {
                            action.accept(player);
                        }
                        task.complete(null);
                    } catch (Throwable throwable) {
                        task.completeExceptionally(throwable);
                    }
                }, () -> task.completeExceptionally(new RejectedExecutionException(
                        "Skills player reload operation retired before execution.")));
                if (scheduled == null) {
                    task.completeExceptionally(new RejectedExecutionException(
                            "Skills player reload operation scheduling was rejected."));
                }
            } catch (Throwable throwable) {
                task.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }

    public void shutdown(EmakiSkillsPlugin plugin) {
        if (plugin.actionBarService() != null) {
            plugin.actionBarService().stopRefreshTask();
        }
        if (plugin.playerSkillDataStore() != null) {
            var flushResult = plugin.playerSkillDataStore().flushAndSeal(5L, TimeUnit.SECONDS);
            if (!flushResult.clean()) {
                plugin.getLogger().warning("[Shutdown] Skill data drain incomplete: pending="
                        + flushResult.drainResult().pendingOperations()
                        + ", ioFailures=" + flushResult.drainResult().failures().size()
                        + ", saveFailures=" + flushResult.failedEntries()
                        + ", remainingDirty=" + flushResult.remainingDirtyEntries());
            }
        }
        if (plugin.eaBridge() != null) {
            plugin.eaBridge().shutdown();
        }
        if (plugin.externalManaBridge() != null) {
            plugin.externalManaBridge().shutdown();
        }
        if (plugin.mythicBridge() != null) {
            plugin.mythicBridge().shutdown();
        }
        if (plugin.skillsGuiService() != null) {
            plugin.skillsGuiService().clearAllSessions();
        }
        if (plugin.skillSourceRegistry() != null) {
            plugin.skillSourceRegistry().close();
        }
        if (plugin.skillPipelineRuntime() != null) {
            plugin.skillPipelineRuntime().invalidateAll();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();

        YamlSection slotsSection = configuration.getSection("slots");
        int defaultSlotCount = slotsSection != null
                ? intValue(slotsSection.getInt("default_count", defaults.defaultSlotCount()), defaults.defaultSlotCount())
                : defaults.defaultSlotCount();

        YamlSection skillSourcesSection = configuration.getSection("skill_sources");
        AppConfig.SkillSourceSettings skillSources = skillSourcesSection == null
                ? defaults.skillSources()
                : new AppConfig.SkillSourceSettings(
                        boolValue(skillSourcesSection.getBoolean("read_lore_skills"), defaults.skillSources().readLoreSkills()),
                        boolValue(skillSourcesSection.getBoolean("read_pdc_skills"), defaults.skillSources().readPdcSkills()),
                        boolValue(skillSourcesSection.getBoolean("require_lore_pdc_match"), defaults.skillSources().requireLorePdcMatch())
                );

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

        YamlSection castTimingSection = configuration.getSection("cast_timing");
        AppConfig.CastTimingSettings castTiming;
        if (castTimingSection == null) {
            castTiming = defaults.castTiming();
        } else {
            castTiming = new AppConfig.CastTimingSettings(
                    intValue(castTimingSection.getInt("forced_global_cast_delay_ticks"), (int) defaults.castTiming().forcedGlobalCastDelayTicks())
            );
        }

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

        Map<String, Integer> skillTagEquipLimits = parseSkillTagEquipLimits(configuration);
        Map<String, AppConfig.TriggerConfig> triggers = parseTriggers(configuration.getSection("triggers"));
        Map<String, AppConfig.TriggerConfig> passiveTriggers = parseTriggers(configuration.getSection("passive_triggers"));
        YamlSection passiveTriggerSettingsSection = configuration.getSection("passive_trigger_settings");
        AppConfig.PassiveTriggerSettings passiveTriggerSettings = passiveTriggerSettingsSection == null
                ? defaults.passiveTriggerSettings()
                : new AppConfig.PassiveTriggerSettings(
                        intValue(passiveTriggerSettingsSection.getInt("timer_interval_ticks"),
                                (int) defaults.passiveTriggerSettings().timerIntervalTicks()),
                        intValue(passiveTriggerSettingsSection.getInt("combo_timeout_ticks"),
                                (int) defaults.passiveTriggerSettings().comboTimeoutTicks())
                );

        YamlSection triggerSettingsSection = configuration.getSection("trigger_settings");
        AppConfig.TriggerSettings triggerSettings = triggerSettingsSection == null
                ? defaults.triggerSettings()
                : new AppConfig.TriggerSettings(
                        boolValue(triggerSettingsSection.getBoolean("legacy_dispatch_cancelled_events"),
                                defaults.triggerSettings().legacyDispatchCancelledEvents())
                );

        AppConfig.ScriptEngineSettings scriptEngine = parseScriptEngineSettings(
                configuration.getSection("script_engine"), defaults.scriptEngine());

        return new AppConfig(
                configuration.getString("language", defaults.language()),
                configuration.getString("version", defaults.configVersion()),
                boolValue(configuration.getBoolean("release_default_data"), defaults.releaseDefaultData()),
                defaultSlotCount,
                skillSources,
                castMode,
                castTiming,
                actionBar,
                skillTagEquipLimits,
                triggers,
                passiveTriggers,
                passiveTriggerSettings,
                scriptEngine,
                triggerSettings
        );
    }

    private static int intValue(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static boolean boolValue(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private Map<String, Integer> parseSkillTagEquipLimits(YamlSection configuration) {
        if (configuration == null) {
            return Map.of();
        }
        YamlSection section = configuration.getSection("skill_tags.equip_limits");
        if (section == null || section.getKeys(false).isEmpty()) {
            section = configuration.getSection("tag_limits");
        }
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String tag = emaki.jiuwu.craft.corelib.text.Texts.normalizeId(key).replace('-', '_');
            int limit = intValue(section.getInt(key), 0);
            if (!tag.isBlank() && limit > 0) {
                limits.put(tag, limit);
            }
        }
        return limits.isEmpty() ? Map.of() : Map.copyOf(limits);
    }

    private AppConfig.ScriptEngineSettings parseScriptEngineSettings(YamlSection section,
            AppConfig.ScriptEngineSettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new AppConfig.ScriptEngineSettings(
                boolValue(section.getBoolean("enabled"), defaults.enabled()),
                section.getString("default_mode", defaults.defaultMode()),
                boolValue(section.getBoolean("stop_on_failure"), defaults.stopOnFailure()),
                intValue(section.getInt("max_lines_per_phase"), defaults.maxLinesPerPhase()),
                intValue(section.getInt("max_targets_per_action"), defaults.maxTargetsPerAction()),
                boolValue(section.getBoolean("debug"), defaults.debug())
        );
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

        for (SkillTriggerDefinition def : TriggerRegistry.defaultDefinitions()) {
            registry.register(def);
        }
        for (SkillTriggerDefinition def : TriggerRegistry.defaultPassiveDefinitions()) {
            registry.register(def);
        }

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

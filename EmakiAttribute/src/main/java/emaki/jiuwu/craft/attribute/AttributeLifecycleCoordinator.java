package emaki.jiuwu.craft.attribute;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.attribute.listener.DamageIndicatorListener;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

import emaki.jiuwu.craft.attribute.bridge.ServiceBackedEmakiAttributeBridge;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.listener.CombatDamageListener;
import emaki.jiuwu.craft.attribute.listener.CombatDebugHandler;
import emaki.jiuwu.craft.attribute.listener.CombatDebugListener;
import emaki.jiuwu.craft.attribute.listener.InventoryInteractionListener;
import emaki.jiuwu.craft.attribute.listener.PlayerLifecycleListener;
import emaki.jiuwu.craft.attribute.listener.PluginIntegrationListener;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.service.AttributePointsGuiService;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.ContributionProviderRegistrationRegistry;
import emaki.jiuwu.craft.attribute.service.ItemContributionGateRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.attribute.service.ParentAttributeDataStore;
import emaki.jiuwu.craft.attribute.service.ParentAttributeService;
import emaki.jiuwu.craft.attribute.service.PdcAttributeService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

final class AttributeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiAttributePlugin, AttributeRuntimeComponents> {

    @Override
    public AttributeRuntimeComponents initialize(EmakiAttributePlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        EmakiScheduling scheduling = EmakiCoreLibApi.scheduling();
        var executionDispatcher = coreLibPlugin.executionDispatcher();
        LanguageLoader languageLoader = new LanguageLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader,
                "<gray>[ <gradient:#F43F5E:#FB923C>EmakiAttribute</gradient> ]</gray>", true);
        AttributeRegistry attributeRegistry = new AttributeRegistry(plugin);
        AttributeBalanceRegistry attributeBalanceRegistry = new AttributeBalanceRegistry(plugin, attributeRegistry);
        DamageTypeRegistry damageTypeRegistry = new DamageTypeRegistry(plugin, attributeRegistry);
        DefaultProfileRegistry defaultProfileRegistry = new DefaultProfileRegistry(plugin);
        LoreFormatRegistry loreFormatRegistry = new LoreFormatRegistry(plugin);
        AttributePresetRegistry presetRegistry = new AttributePresetRegistry(plugin);
        PdcReadRuleLoader pdcReadRuleLoader = new PdcReadRuleLoader(plugin);
        ItemContributionGateRegistry itemContributionGateRegistry = new ItemContributionGateRegistry(plugin.getLogger());
        PdcAttributeService pdcAttributeService = new PdcAttributeService(plugin, pdcReadRuleLoader, itemContributionGateRegistry);
        ParentAttributeDataStore parentAttributeDataStore = new ParentAttributeDataStore(plugin);
        ParentAttributeService parentAttributeService = new ParentAttributeService(plugin, parentAttributeDataStore);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        AttributePointsGuiService attributePointsGuiService = new AttributePointsGuiService(plugin, guiService, guiTemplateLoader);
        AttributeService attributeService = new AttributeService(
                plugin,
                coreLibPlugin.pdcService(),
                coreLibPlugin.asyncTaskScheduler(),
                plugin.configModel(),
                attributeRegistry,
                attributeBalanceRegistry,
                damageTypeRegistry,
                defaultProfileRegistry,
                loreFormatRegistry,
                presetRegistry,
                pdcAttributeService,
                parentAttributeService,
                scheduling
        );
        ContributionProviderRegistrationRegistry contributionProviderRegistrationRegistry =
                new ContributionProviderRegistrationRegistry(attributeService);
        EmakiAttributeApi.Bridge emakiAttributeBridge = new ServiceBackedEmakiAttributeBridge(
                attributeService,
                scheduling,
                itemContributionGateRegistry,
                contributionProviderRegistrationRegistry,
                pdcAttributeService);
        CombatDebugHandler combatDebugHandler = new CombatDebugHandler(attributeService);
        List<Listener> listeners = List.of(
                new PlayerLifecycleListener(attributeService),
                new PluginIntegrationListener(plugin),
                new InventoryInteractionListener(attributeService),
                new CombatDamageListener(plugin, attributeService, combatDebugHandler, scheduling),
                attributeService.perfectTakeoverCoordinator(),
                new CombatDebugListener(attributeService),
                new DamageIndicatorListener(
                        plugin::damageIndicatorService,
                        () -> plugin.configModel() == null ? null : plugin.configModel().damageIndicator()),
                itemContributionGateRegistry,
                contributionProviderRegistrationRegistry,
                guiService
        );
        MythicBridge mythicBridge = Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
                ? new MythicBridge(plugin, attributeService)
                : null;
        AttributeCommand command = new AttributeCommand(plugin, attributeService, scheduling);
        return new AttributeRuntimeComponents(
                scheduling,
                attributeRegistry,
                attributeBalanceRegistry,
                damageTypeRegistry,
                defaultProfileRegistry,
                loreFormatRegistry,
                presetRegistry,
                pdcReadRuleLoader,
                itemContributionGateRegistry,
                contributionProviderRegistrationRegistry,
                languageLoader,
                messageService,
                emakiAttributeBridge,
                pdcAttributeService,
                parentAttributeDataStore,
                parentAttributeService,
                guiTemplateLoader,
                guiService,
                attributePointsGuiService,
                attributeService,
                listeners,
                command,
                mythicBridge
        );
    }

    public void registerCommand(EmakiAttributePlugin plugin) {
        if (plugin.command() == null) {
            return;
        }
        plugin.registerCommand(
                "emakiattribute",
                "emakiattribute command",
                List.of("eattribute", "ea"),
                new PaperCommandAdapter("emakiattribute", "emakiattribute.use", plugin.command(), plugin.command())
        );
    }

    public void registerListener(EmakiAttributePlugin plugin) {
        for (Listener listener : plugin.listeners()) {
            if (listener != null) {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
        }
        // mythicBridge 不在此注册：它的唯一注册入口是 EmakiAttributePlugin#ensureMythicBridge，
        // 那里同时覆盖 reload 与 MythicMobs 后启用两条路径。
    }

    public TaskToken reload(EmakiAttributePlugin plugin, TaskToken currentTask, boolean resyncPlayers) {
        // Entry B rather than the single-loader gate: this module loads in stages, and two of its
        // registries (DefaultProfileRegistry, AttributeBalanceRegistry) read plugin.configModel()
        // while loading, so the candidate config has to be in place before the stages can run. The
        // previous model is captured here and put back if the precheck rejects the candidate.
        AttributeConfig previousConfig = plugin.configModel();
        if (plugin.languageLoader() != null) {
            plugin.languageLoader().load();
        }
        plugin.setConfigModel(loadConfigModel(plugin));
        if (plugin.attributeService() != null) {
            plugin.attributeService().reloadConfig(plugin.configModel());
        }
        if (plugin.languageLoader() != null) {
            plugin.languageLoader().setLanguage(plugin.configModel().language());
        }
        loadGuiTemplates(plugin);
        runReloadStage("lore_format_registry", () -> plugin.loreFormatRegistry().load(), failureHandler(plugin));
        runReloadStage("attribute_registry", () -> plugin.attributeRegistry().load(), failureHandler(plugin));
        runReloadStage("default_profile_registry", () -> plugin.defaultProfileRegistry().load(), failureHandler(plugin));
        runReloadStage("preset_registry", () -> plugin.presetRegistry().load(), failureHandler(plugin));
        runReloadStage("pdc_read_rule_loader", () -> plugin.pdcReadRuleLoader().load(), failureHandler(plugin));
        runReloadStage("attribute_balance_registry", () -> plugin.attributeBalanceRegistry().load(), failureHandler(plugin));
        runReloadStage("damage_type_registry", () -> plugin.damageTypeRegistry().load(), failureHandler(plugin));
        // Gate after the stages because the attribute precheck reads every registry's issue list, and
        // before the cache/bridge/player work so a rejected candidate never reaches online entities.
        if (ConfigCommitGate.evaluate(plugin.messageService(), "attribute").rejected()) {
            restoreConfigModel(plugin, previousConfig);
            return currentTask;
        }
        if (plugin.attributeService() != null) {
            plugin.attributeService().refreshCaches();
        }
        plugin.ensureMythicBridge();
        if (plugin.mythicBridge() != null) {
            plugin.mythicBridge().resyncActiveMobs();
        }
        plugin.ensureMmoItemsBridge();
        if (plugin.attributeService() != null && resyncPlayers) {
            plugin.attributeService().resyncAllPlayers();
        }
        return rescheduleRegenTask(plugin, currentTask);
    }

    public CompletableFuture<TaskToken> reloadAsync(EmakiAttributePlugin plugin,
            TaskToken currentTask,
            boolean resyncPlayers,
            Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        // Captured before the pipeline mutates it, for the same reason as the synchronous path.
        AttributeConfig previousConfig = plugin.configModel();
        return runReloadPipelineAsync(scheduler, plugin.scheduling(), plugin, new ReloadPipelineConfig<>(
                "attribute",
                "bootstrap",
                "正在读取语言与配置...",
                () -> {
                    if (plugin.languageLoader() != null) {
                        plugin.languageLoader().load();
                    }
                    return loadConfigModel(plugin);
                },
                "config-apply",
                "正在应用配置...",
                configModel -> {
                    plugin.setConfigModel(configModel);
                    if (plugin.attributeService() != null) {
                        plugin.attributeService().reloadConfig(plugin.configModel());
                    }
                    if (plugin.languageLoader() != null) {
                        plugin.languageLoader().setLanguage(plugin.configModel().language());
                    }
                    loadGuiTemplates(plugin);
                    return configModel;
                },
                null,
                null,
                null,
                failureHandler(plugin),
                progressListener
        )).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "lore_format_registry",
                "正在加载词条格式...",
                progressListener,
                () -> plugin.loreFormatRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "attribute_registry",
                "正在加载属性定义...",
                progressListener,
                () -> plugin.attributeRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "default_profile_registry",
                "正在加载默认属性配置...",
                progressListener,
                () -> plugin.defaultProfileRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "pdc_read_rule_loader",
                "正在加载属性读取条件...",
                progressListener,
                () -> plugin.pdcReadRuleLoader().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "preset_registry",
                "正在加载属性预设...",
                progressListener,
                () -> plugin.presetRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "attribute_balance_registry",
                "正在加载属性权重...",
                progressListener,
                () -> plugin.attributeBalanceRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "attribute",
                "damage_type_registry",
                "正在加载伤害类型...",
                progressListener,
                () -> plugin.damageTypeRegistry().load(),
                configModel,
                failureHandler(plugin)
        ))).thenCompose(configModel -> plugin.scheduling().submitGlobal(plugin, () -> {
            if (ConfigCommitGate.evaluate(plugin.messageService(), "attribute").rejected()) {
                restoreConfigModel(plugin, previousConfig);
                notifyProgress(progressListener, "EmakiAttribute 配置预检未通过，已保留上一份配置。");
                return currentTask;
            }
            notifyProgress(progressListener, "正在刷新缓存并同步在线实体...");
            if (plugin.attributeService() != null) {
                plugin.attributeService().refreshCaches();
            }
            plugin.ensureMythicBridge();
            if (plugin.mythicBridge() != null) {
                plugin.mythicBridge().resyncActiveMobs();
            }
            plugin.ensureMmoItemsBridge();
            if (plugin.attributeService() != null && resyncPlayers) {
                plugin.attributeService().resyncAllPlayers();
            }
            TaskToken nextTask = rescheduleRegenTask(plugin, currentTask);
            notifyProgress(progressListener, "EmakiAttribute 重载完成。");
            return nextTask;
        }));
    }

    public TaskToken rescheduleRegenTask(EmakiAttributePlugin plugin, TaskToken currentTask) {
        TaskToken nextTask = cancelRegenTask(currentTask);
        if (plugin.attributeService() == null) {
            return nextTask;
        }
        int intervalTicks = Math.max(1, plugin.configModel().regenIntervalTicks());
        return plugin.scheduling().runGlobalTimer(
                plugin,
                plugin.attributeService()::regenerateOnlinePlayers,
                intervalTicks,
                intervalTicks
        );
    }

    public TaskToken cancelRegenTask(TaskToken currentTask) {
        if (currentTask != null) {
            currentTask.cancel();
        }
        return null;
    }

    public void shutdown(EmakiAttributePlugin plugin, TaskToken currentTask) {
        cancelRegenTask(currentTask);
        if (plugin.itemContributionGateRegistry() != null) {
            plugin.itemContributionGateRegistry().close();
        }
        if (plugin.contributionProviderRegistrationRegistry() != null) {
            plugin.contributionProviderRegistrationRegistry().close();
        }
        if (plugin.attributeService() != null) {
            plugin.attributeService().shutdown();
        }
        if (plugin.placeholderExpansion() != null) {
            plugin.placeholderExpansion().unregister();
            plugin.setPlaceholderExpansion(null);
        }
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopped");
        } else {
            plugin.getLogger().info("EmakiAttribute stopped.");
        }
    }


    private void loadGuiTemplates(EmakiAttributePlugin plugin) {
        if (plugin.guiTemplateLoader() == null) {
            return;
        }
        if (plugin.configModel().releaseDefaultData()) {
            try {
                YamlFiles.copyResourceIfMissing(
                        plugin,
                        "gui/attribute_points.yml",
                        plugin.dataPath("gui/attribute_points.yml").toFile()
                );
            } catch (Exception exception) {
                plugin.messageService().warning("loader.bundled_resource_write_failed", Map.of(
                        "type", "gui",
                        "path", plugin.dataPath("gui/attribute_points.yml").toString(),
                        "error", Texts.toStringSafe(exception.getMessage())
                ));
            }
        }
        plugin.guiTemplateLoader().load();
    }

    private BiConsumer<String, Exception> failureHandler(EmakiAttributePlugin plugin) {
        return (stageName, exception) -> plugin.messageService().warning("console.reload_stage_failed", Map.of(
                "stage", stageName,
                "error", String.valueOf(exception.getMessage())
        ));
    }

    /**
     * Puts the last known good config model back after a rejected precheck.
     *
     * <p>Restoring the captured instance rather than reloading from disk is deliberate: the file on disk
     * is the rejected candidate, and {@code loadConfigModel} falls back to {@link AttributeConfig#defaults()}
     * when parsing fails, which would silently discard the operator's working configuration.
     */
    private void restoreConfigModel(EmakiAttributePlugin plugin, AttributeConfig previousConfig) {
        AttributeConfig restored = previousConfig == null ? AttributeConfig.defaults() : previousConfig;
        plugin.setConfigModel(restored);
        if (plugin.attributeService() != null) {
            plugin.attributeService().reloadConfig(restored);
        }
        if (plugin.languageLoader() != null) {
            plugin.languageLoader().setLanguage(restored.language());
        }
    }

    private AttributeConfig loadConfigModel(EmakiAttributePlugin plugin) {
        try {
            File file = new File(plugin.getDataFolder(), "config.yml");
            VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(
                    plugin,
                    file,
                    "config.yml",
                    "version",
                    document -> mergeBundledConfig(document.root(), document.defaults())
            );
            logVersionUpdate(plugin, "config.yml", versionedFile);
            if (!file.exists()) {
                plugin.messageService().warning("loader.bundled_resource_missing", Map.of(
                        "type", "配置",
                        "path", file.getPath(),
                        "resource", "config.yml"
                ));
            }
            return AttributeConfig.fromConfig(versionedFile == null ? YamlFiles.load(file) : versionedFile.root());
        } catch (Exception exception) {
            plugin.messageService().warning("console.config_load_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
            return AttributeConfig.defaults();
        }
    }

    private void logVersionUpdate(EmakiAttributePlugin plugin, String relativePath, VersionedYamlFile versionedFile) {
        if (versionedFile == null || !versionedFile.versionUpdated()) {
            return;
        }
        plugin.messageService().info("console.versioned_file_updated", Map.of(
                "path", relativePath,
                "old_version", versionedFile.previousVersion().isBlank() ? "unknown" : versionedFile.previousVersion(),
                "new_version", versionedFile.updatedVersion()
        ));
    }

    private void mergeBundledConfig(YamlSection runtime,
            YamlSection bundled) {
        boolean changed = mergeDefaultProfile(runtime, bundled);
        if (mergeAllowedDamageCauses(runtime, bundled)) {
            changed = true;
        }
        if (changed) {
            runtime.set("version", bundled.get("version"));
        }
    }

    private boolean mergeDefaultProfile(YamlSection runtime,
            YamlSection bundled) {
        if (runtime == null || bundled == null || runtime.contains("default_profile")) {
            return false;
        }
        Object bundledProfile = ConfigNodes.toPlainData(bundled.get("default_profile"));
        if (bundledProfile == null) {
            return false;
        }
        runtime.set("default_profile", bundledProfile);
        return true;
    }

    private boolean mergeAllowedDamageCauses(YamlSection runtime,
            YamlSection bundled) {
        if (runtime == null || bundled == null) {
            return false;
        }
        List<Object> runtimeEntries = new ArrayList<>(ConfigNodes.asObjectList(runtime.get("allowed_damage_causes")));
        List<Object> bundledEntries = ConfigNodes.asObjectList(bundled.get("allowed_damage_causes"));
        if (bundledEntries.isEmpty()) {
            return false;
        }
        String defaultDamageType = ConfigNodes.string(bundled, "default_damage_type", "physical");
        Map<String, Object> bundledByCause = new LinkedHashMap<>();
        for (Object entry : bundledEntries) {
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule == null) {
                continue;
            }
            bundledByCause.put(rule.cause(), ConfigNodes.toPlainData(entry));
        }
        Set<String> existingCauses = new LinkedHashSet<>();
        boolean changed = false;
        for (Object entry : runtimeEntries) {
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule == null) {
                continue;
            }
            existingCauses.add(rule.cause());
        }
        for (Map.Entry<String, Object> bundledEntry : bundledByCause.entrySet()) {
            if (existingCauses.contains(bundledEntry.getKey())) {
                continue;
            }
            runtimeEntries.add(ConfigNodes.toPlainData(bundledEntry.getValue()));
            existingCauses.add(bundledEntry.getKey());
            changed = true;
        }
        if (!changed) {
            return false;
        }
        runtime.set("allowed_damage_causes", runtimeEntries);
        return true;
    }

    private static final class PaperCommandAdapter implements BasicCommand {

        private final String rootLabel;
        private final String permission;
        private final CommandExecutor executor;
        private final TabCompleter tabCompleter;

        private PaperCommandAdapter(String rootLabel,
                String permission,
                CommandExecutor executor,
                TabCompleter tabCompleter) {
            this.rootLabel = rootLabel;
            this.permission = permission;
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            executor.onCommand(source.getSender(), null, rootLabel, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
            List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null, rootLabel, completionArgs);
            return suggestions == null ? List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

}

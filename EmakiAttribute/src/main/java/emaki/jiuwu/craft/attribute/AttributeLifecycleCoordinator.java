package emaki.jiuwu.craft.attribute;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;

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
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamageHookListener;
import emaki.jiuwu.craft.attribute.service.PdcAttributeService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

final class AttributeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiAttributePlugin, AttributeRuntimeComponents> {

    @Override
    public AttributeRuntimeComponents initialize(EmakiAttributePlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        LanguageLoader languageLoader = new LanguageLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, plugin::configModel);
        AttributeRegistry attributeRegistry = new AttributeRegistry(plugin);
        AttributeBalanceRegistry attributeBalanceRegistry = new AttributeBalanceRegistry(plugin, attributeRegistry);
        DamageTypeRegistry damageTypeRegistry = new DamageTypeRegistry(plugin, attributeRegistry);
        DefaultProfileRegistry defaultProfileRegistry = new DefaultProfileRegistry(plugin);
        LoreFormatRegistry loreFormatRegistry = new LoreFormatRegistry(plugin);
        AttributePresetRegistry presetRegistry = new AttributePresetRegistry(plugin);
        PdcReadRuleLoader pdcReadRuleLoader = new PdcReadRuleLoader(plugin);
        PdcAttributeService pdcAttributeService = new PdcAttributeService(plugin, pdcReadRuleLoader);
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
                pdcAttributeService
        );
        EmakiAttributeBridge emakiAttributeBridge = new ServiceBackedEmakiAttributeBridge(attributeService);
        CombatDebugHandler combatDebugHandler = new CombatDebugHandler(attributeService);
        List<Listener> listeners = List.of(
                new PlayerLifecycleListener(attributeService),
                new PluginIntegrationListener(plugin),
                new InventoryInteractionListener(attributeService),
                new CombatDamageListener(plugin, attributeService, combatDebugHandler),
                attributeService.perfectTakeoverCoordinator(),
                new CombatDebugListener(attributeService)
        );
        MythicBridge mythicBridge = Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
                ? new MythicBridge(plugin, attributeService)
                : null;
        AttributeCommand command = new AttributeCommand(plugin, attributeService);
        return new AttributeRuntimeComponents(
                attributeRegistry,
                attributeBalanceRegistry,
                damageTypeRegistry,
                defaultProfileRegistry,
                loreFormatRegistry,
                presetRegistry,
                pdcReadRuleLoader,
                languageLoader,
                messageService,
                emakiAttributeBridge,
                pdcAttributeService,
                attributeService,
                listeners,
                command,
                mythicBridge
        );
    }

    public void registerCommand(EmakiAttributePlugin plugin) {
        PluginCommand pluginCommand = getPluginCommand(plugin);
        if (pluginCommand == null || plugin.command() == null) {
            return;
        }
        pluginCommand.setExecutor(plugin.command());
        pluginCommand.setTabCompleter(plugin.command());
    }

    public void registerListener(EmakiAttributePlugin plugin) {
        for (Listener listener : plugin.listeners()) {
            if (listener != null) {
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
        }
        if (plugin.javaScriptDamageHookRegistry() != null) {
            plugin.getServer().getPluginManager().registerEvents(new JavaScriptDamageHookListener(plugin.javaScriptDamageHookRegistry()), plugin);
        }
        if (plugin.mythicBridge() != null) {
            plugin.getServer().getPluginManager().registerEvents(plugin.mythicBridge(), plugin);
        }
    }

    public TaskHandle reload(EmakiAttributePlugin plugin, TaskHandle currentTask, boolean resyncPlayers) {
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
        runReloadStage("lore_format_registry", () -> plugin.loreFormatRegistry().load(), failureHandler(plugin));
        runReloadStage("attribute_registry", () -> plugin.attributeRegistry().load(), failureHandler(plugin));
        runReloadStage("default_profile_registry", () -> plugin.defaultProfileRegistry().load(), failureHandler(plugin));
        runReloadStage("preset_registry", () -> plugin.presetRegistry().load(), failureHandler(plugin));
        runReloadStage("pdc_read_rule_loader", () -> plugin.pdcReadRuleLoader().load(), failureHandler(plugin));
        runReloadStage("attribute_balance_registry", () -> plugin.attributeBalanceRegistry().load(), failureHandler(plugin));
        runReloadStage("damage_type_registry", () -> plugin.damageTypeRegistry().load(), failureHandler(plugin));
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

    public CompletableFuture<TaskHandle> reloadAsync(EmakiAttributePlugin plugin,
            TaskHandle currentTask,
            boolean resyncPlayers,
            Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (scheduler == null) {
            return CompletableFuture.completedFuture(reload(plugin, currentTask, resyncPlayers));
        }
        notifyProgress(progressListener, "正在读取语言与配置...");
        return scheduler.supplyAsync("attribute-reload-bootstrap", () -> {
            if (plugin.languageLoader() != null) {
                plugin.languageLoader().load();
            }
            return loadConfigModel(plugin);
        }).thenCompose(configModel -> scheduler.callSync("attribute-reload-config-apply", () -> {
            plugin.setConfigModel(configModel);
            if (plugin.attributeService() != null) {
                plugin.attributeService().reloadConfig(plugin.configModel());
            }
            if (plugin.languageLoader() != null) {
                plugin.languageLoader().setLanguage(plugin.configModel().language());
            }
            return configModel;
        })).thenCompose(configModel -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
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
        ))).thenCompose(configModel -> scheduler.callSync("attribute-reload-finalize", () -> {
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
            TaskHandle nextTask = rescheduleRegenTask(plugin, currentTask);
            notifyProgress(progressListener, "EmakiAttribute 重载完成。");
            return nextTask;
        }));
    }

    public TaskHandle rescheduleRegenTask(EmakiAttributePlugin plugin, TaskHandle currentTask) {
        TaskHandle nextTask = cancelRegenTask(currentTask);
        if (plugin.attributeService() == null) {
            return nextTask;
        }
        int intervalTicks = Math.max(1, plugin.configModel().regenIntervalTicks());
        return FoliaSchedulerAdapter.runTaskTimer(
                plugin,
                plugin.attributeService()::regenerateOnlinePlayers,
                intervalTicks,
                intervalTicks
        );
    }

    public TaskHandle cancelRegenTask(TaskHandle currentTask) {
        FoliaSchedulerAdapter.cancelTask(currentTask);
        return null;
    }

    public void shutdown(EmakiAttributePlugin plugin, TaskHandle currentTask) {
        cancelRegenTask(currentTask);
        if (plugin.attributeService() != null) {
            plugin.attributeService().shutdown();
        }
        if (plugin.placeholderExpansion() != null) {
            plugin.placeholderExpansion().unregister();
            plugin.setPlaceholderExpansion(null);
        }
        plugin.messageService().info("console.plugin_stopped");
    }

    private PluginCommand getPluginCommand(EmakiAttributePlugin plugin) {
        return plugin.getCommand("emakiattribute");
    }

    private java.util.function.BiConsumer<String, Exception> failureHandler(EmakiAttributePlugin plugin) {
        return (stageName, exception) -> plugin.messageService().warning("console.reload_stage_failed", Map.of(
                "stage", stageName,
                "error", String.valueOf(exception.getMessage())
        ));
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

    private void mergeBundledConfig(emaki.jiuwu.craft.corelib.yaml.YamlSection runtime,
            emaki.jiuwu.craft.corelib.yaml.YamlSection bundled) {
        boolean changed = mergeDefaultProfile(runtime, bundled);
        if (mergeAllowedDamageCauses(runtime, bundled)) {
            changed = true;
        }
        if (changed) {
            runtime.set("version", bundled.get("version"));
        }
    }

    private boolean mergeDefaultProfile(emaki.jiuwu.craft.corelib.yaml.YamlSection runtime,
            emaki.jiuwu.craft.corelib.yaml.YamlSection bundled) {
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

    private boolean mergeAllowedDamageCauses(emaki.jiuwu.craft.corelib.yaml.YamlSection runtime,
            emaki.jiuwu.craft.corelib.yaml.YamlSection bundled) {
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
        ListIterator<Object> iterator = runtimeEntries.listIterator();
        while (iterator.hasNext()) {
            Object entry = iterator.next();
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule == null) {
                continue;
            }
            existingCauses.add(rule.cause());
            if (!isLegacyDefaultDamageCauseRule(entry, rule, defaultDamageType)) {
                continue;
            }
            Object replacement = bundledByCause.get(rule.cause());
            if (replacement == null) {
                continue;
            }
            iterator.set(ConfigNodes.toPlainData(replacement));
            changed = true;
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

    private boolean isLegacyDefaultDamageCauseRule(Object entry, DamageCauseRule rule, String defaultDamageType) {
        if (entry == null || rule == null) {
            return false;
        }
        Map<String, Object> entries = ConfigNodes.entries(entry);
        if (entries.size() != 4) {
            return false;
        }
        for (String key : entries.keySet()) {
            String normalizedKey = Texts.normalizeId(key);
            if (!normalizedKey.equals("cause")
                    && !normalizedKey.equals("damage_type")
                    && !normalizedKey.equals("damage")
                    && !normalizedKey.equals("enabled")) {
                return false;
            }
        }
        String cause = Texts.normalizeId(ConfigNodes.string(entry, "cause", null));
        String damageType = Texts.normalizeId(ConfigNodes.string(entry, "damage_type", defaultDamageType));
        Double damage = Numbers.tryParseDouble(ConfigNodes.get(entry, "damage"), null);
        if (!cause.equals(rule.cause())) {
            return false;
        }
        if (!damageType.equals(Texts.normalizeId(defaultDamageType))) {
            return false;
        }
        if (damage == null || Math.abs(damage - 1D) > 1.0E-9D) {
            return false;
        }
        return ConfigNodes.contains(entry, "enabled") && ConfigNodes.bool(entry, "enabled", true);
    }
}

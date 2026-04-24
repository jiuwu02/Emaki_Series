package emaki.jiuwu.craft.attribute;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.attribute.bridge.ServiceBackedEmakiAttributeBridge;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.listener.AttributeListener;
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
import emaki.jiuwu.craft.attribute.service.PdcAttributeService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
        AttributeListener listener = new AttributeListener(plugin, attributeService);
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
                listener,
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
        if (plugin.listener() != null) {
            plugin.getServer().getPluginManager().registerEvents(plugin.listener(), plugin);
        }
        if (plugin.mythicBridge() != null) {
            plugin.getServer().getPluginManager().registerEvents(plugin.mythicBridge(), plugin);
        }
    }

    public BukkitTask reload(EmakiAttributePlugin plugin, BukkitTask currentTask, boolean resyncPlayers) {
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

    public CompletableFuture<BukkitTask> reloadAsync(EmakiAttributePlugin plugin,
            BukkitTask currentTask,
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
            BukkitTask nextTask = rescheduleRegenTask(plugin, currentTask);
            notifyProgress(progressListener, "EmakiAttribute 重载完成。");
            return nextTask;
        }));
    }

    public BukkitTask rescheduleRegenTask(EmakiAttributePlugin plugin, BukkitTask currentTask) {
        BukkitTask nextTask = cancelRegenTask(currentTask);
        if (plugin.attributeService() == null) {
            return nextTask;
        }
        int intervalTicks = Math.max(1, plugin.configModel().regenIntervalTicks());
        return plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                plugin.attributeService()::regenerateOnlinePlayers,
                intervalTicks,
                intervalTicks
        );
    }

    public BukkitTask cancelRegenTask(BukkitTask currentTask) {
        if (currentTask != null) {
            currentTask.cancel();
        }
        return null;
    }

    public void shutdown(EmakiAttributePlugin plugin, BukkitTask currentTask) {
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
                    "config_version",
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

    private void mergeBundledConfig(YamlSection runtime, YamlSection bundled) {
        boolean changed = mergeDefaultProfile(runtime, bundled);
        if (mergeAllowedDamageCauses(runtime, bundled)) {
            changed = true;
        }
        if (changed) {
            runtime.set("config_version", bundled.get("config_version"));
        }
    }

    private boolean mergeDefaultProfile(YamlSection runtime, YamlSection bundled) {
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

    private boolean mergeAllowedDamageCauses(YamlSection runtime, YamlSection bundled) {
        if (runtime == null || bundled == null) {
            return false;
        }
        List<Object> runtimeEntries = new ArrayList<>(ConfigNodes.asObjectList(runtime.get("allowed_damage_causes")));
        List<Object> bundledEntries = ConfigNodes.asObjectList(bundled.get("allowed_damage_causes"));
        if (bundledEntries.isEmpty()) {
            return false;
        }
        String defaultDamageType = ConfigNodes.string(bundled, "default_damage_type", "physical");
        Set<String> existingCauses = new LinkedHashSet<>();
        for (Object entry : runtimeEntries) {
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule != null) {
                existingCauses.add(rule.cause());
            }
        }
        boolean changed = false;
        for (Object entry : bundledEntries) {
            DamageCauseRule rule = DamageCauseRule.fromMap(entry, defaultDamageType);
            if (rule == null || existingCauses.contains(rule.cause())) {
                continue;
            }
            runtimeEntries.add(ConfigNodes.toPlainData(entry));
            existingCauses.add(rule.cause());
            changed = true;
        }
        if (!changed) {
            return false;
        }
        runtime.set("allowed_damage_causes", runtimeEntries);
        return true;
    }
}

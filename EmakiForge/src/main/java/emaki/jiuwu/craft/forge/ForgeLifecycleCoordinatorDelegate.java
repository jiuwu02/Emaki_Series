package emaki.jiuwu.craft.forge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.bootstrap.ConfigKeyMigration;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter;
import emaki.jiuwu.craft.corelib.legacy.LegacyStartupMigration;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.editor.ForgeEditorModule;
import emaki.jiuwu.craft.forge.legacy.ForgeLegacyTargets;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.loader.ForgeGuiTemplateLoader;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeLookupIndex;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.service.ForgeGuiSession;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;

final class ForgeLifecycleCoordinatorDelegate {

    private final ForgeLifecycleCoordinator ownerRef;

    ForgeLifecycleCoordinatorDelegate(ForgeLifecycleCoordinator ownerRef) {
        this.ownerRef = ownerRef;
    }

    void loadCandidateAppConfigReadOnly(YamlConfigLoader<AppConfig> loader) {
        if (loader == null) {
            throw new IllegalStateException("Forge 应用配置加载器不可用.");
        }
        File file = loader.file();
        if (file == null || !file.isFile()) {
            return;
        }
        YamlSection configuration = YamlFiles.load(file);
        loader.overrideCurrent(configuration == null || configuration.isEmpty()
                ? AppConfig.defaults()
                : ownerRef.parseAppConfig(configuration));
    }

    void validateCandidateLanguagesReadOnly(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles((ignored, name) -> {
            String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
            return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
        });
        if (files == null) {
            throw new IllegalStateException("无法列出 Forge 语言目录 " + directory.getPath());
        }
        for (File file : files) {
            if (file != null && file.isFile()) {
                YamlFiles.load(file);
            }
        }
    }

    List<RecipeLoader.CandidateDocument> readCandidateDocuments(List<File> files) {
        List<RecipeLoader.CandidateDocument> documents = new ArrayList<>();
        if (files == null) {
            return List.of();
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            try {
                documents.add(new RecipeLoader.CandidateDocument(
                        file,
                        Files.readString(file.toPath(), StandardCharsets.UTF_8),
                        null));
            } catch (Exception exception) {
                documents.add(new RecipeLoader.CandidateDocument(file, null, exception));
            }
        }
        return List.copyOf(documents);
    }

    List<File> discoverYamlFiles(File directory) {
        return List.copyOf(YamlDirectoryLoader.collectYamlFiles(directory));
    }

    CompletableFuture<ForgeReloadResult> rejectedReload(EmakiForgePlugin plugin, ForgeReloadCandidate candidate, long started, String detail) {
        return ownerRef.submitGlobal(plugin, () -> {
            plugin.failReload(candidate, detail);
            plugin.messageService().warning("console.reload_rejected", Map.of("generation", candidate.generation(),
                    "summary", candidate.recipeReport().summary()));
            ForgeReloadResult result = new ForgeReloadResult(
                    candidate.generation(),
                    plugin.runtimeGeneration(),
                    plugin.runtimeStatus() == ForgeRuntimeStatus.UNAVAILABLE
                            ? ForgeReloadResult.Outcome.FAILED_UNAVAILABLE
                            : ForgeReloadResult.Outcome.FAILED_PRESERVED,
                    candidate.recipeLoader().all().size(),
                    candidate.guiTemplateLoader().all().size(),
                    ForgeItemRefreshService.RefreshSummary.empty(),
                    candidate.recipeReport(),
                    detail,
                    System.nanoTime() - started
            );
            plugin.runtimeMetrics().recordResult(result);
            return result;
        });
    }

    CompletableFuture<ForgeReloadResult> staleReload(EmakiForgePlugin plugin, long generation, long started, String detail) {
        return ownerRef.submitGlobal(plugin, () -> {
            plugin.failReload(generation, detail);
            ForgeReloadResult result = new ForgeReloadResult(
                    generation,
                    plugin.runtimeGeneration(),
                    ForgeReloadResult.Outcome.STALE,
                    plugin.recipeLoader() == null ? 0 : plugin.recipeLoader().all().size(),
                    plugin.guiTemplateLoader() == null ? 0 : plugin.guiTemplateLoader().all().size(),
                    ForgeItemRefreshService.RefreshSummary.empty(),
                    RecipeLoader.RecipeLoadReport.empty(generation),
                    detail,
                    System.nanoTime() - started
            );
            plugin.runtimeMetrics().recordResult(result);
            return result;
        });
    }

    void sendShutdownMessage(EmakiForgePlugin plugin, String key) {
        ownerRef.sendShutdownMessage(plugin, key, Map.of());
    }

    void sendShutdownMessage(EmakiForgePlugin plugin, String key, Map<String, ?> replacements) {
        if (plugin.messageService() == null) {
            return;
        }
        try {
            if (replacements == null || replacements.isEmpty()) {
                plugin.messageService().info(key);
            } else {
                plugin.messageService().info(key, replacements);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[shutdown] 消息派发失败, 键 '" + key + "': "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    void closeRuntimeForShutdown(EmakiForgePlugin plugin) {
        plugin.forgeGuiService().clearSettledSessions();
        int unresolvedSessions = plugin.forgeGuiService().sessionsSnapshot().size();
        if (unresolvedSessions > 0) {
            plugin.runtimeMetrics().recordGuiSettlementFailure();
            plugin.getLogger().severe("[shutdown] " + unresolvedSessions
                    + " 个 Forge GUI 会话仍持有未结算物品; 未静默丢弃这些会话");
        }
        plugin.recipeBookGuiService().clearAllBooks();
        if (plugin.forgeService() != null) {
            plugin.forgeService().close();
        }
    }

    void scheduleShutdownSettlementFallback(EmakiForgePlugin plugin, EmakiCoreLibPlugin coreLibPlugin, Player player, ForgeGuiSession session, CompletableFuture<Void> quiesceFuture, CompletableFuture<Void> completion) {
        Runnable settle = () -> {
            try {
                plugin.forgeGuiService().settleShutdownSessionOnOwner(session);
                player.closeInventory();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[shutdown] Forge GUI 结算回退失败, 玩家 "
                        + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "关闭结算回退失败: " + String.valueOf(throwable.getMessage()));
            } finally {
                completion.complete(null);
            }
        };
        Runnable retired = () -> {
            plugin.forgeGuiService().handleShutdownClosureFailure(session,
                    "结算回退前玩家所有者已退役");
            completion.complete(null);
        };
        try {
            if (plugin.executionDispatcher() == null || coreLibPlugin == null) {
                retired.run();
                return;
            }
            TaskToken scheduled = plugin.executionDispatcher().runEntityLater(
                    coreLibPlugin,
                    player,
                    settle,
                    retired,
                    ForgeLifecycleCoordinator.SHUTDOWN_SETTLEMENT_DELAY_TICKS);
            if (scheduled == null) {
                retired.run();
                return;
            }
            if (quiesceFuture != null) {
                quiesceFuture.whenComplete((ignored, throwable) -> {
                    if (completion.isDone()) {
                        return;
                    }
                    try {
                        if (!scheduled.cancelled()) {
                            scheduled.cancel();
                        }
                    } catch (Throwable cancellationFailure) {
                        plugin.getLogger().warning("[shutdown] Forge GUI 结算回退取消失败: "
                                + String.valueOf(cancellationFailure.getMessage()));
                    }
                    ownerRef.scheduleShutdownEntityTask(plugin, coreLibPlugin, player, settle, retired, completion,
                            "Forge GUI 静默后结算");
                });
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[shutdown] Forge GUI 结算回退调度失败: "
                    + String.valueOf(throwable.getMessage()));
            try {
                retired.run();
            } catch (Throwable retiredFailure) {
                completion.completeExceptionally(retiredFailure);
            }
        }
    }

    void scheduleShutdownEntityTask(EmakiForgePlugin plugin, EmakiCoreLibPlugin coreLibPlugin, Player player, Runnable operation, Runnable retired, CompletableFuture<Void> completion, String description) {
        try {
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
                operation.run();
                return;
            }
            if (plugin.executionDispatcher() == null || coreLibPlugin == null) {
                retired.run();
                return;
            }
            var scheduled = plugin.executionDispatcher().runEntity(coreLibPlugin, player, operation, retired);
            if (scheduled == null && !completion.isDone()) {
                retired.run();
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[shutdown] " + description + " 调度失败: "
                    + String.valueOf(throwable.getMessage()));
            try {
                retired.run();
            } catch (Throwable retiredFailure) {
                completion.completeExceptionally(retiredFailure);
            }
        }
    }

    CompletableFuture<Void> scheduleInventoryClosures(EmakiForgePlugin plugin) {
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        List<CompletableFuture<Void>> closures = new ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Void> closure = new CompletableFuture<>();
            closures.add(closure);
            Runnable close = () -> {
                try {
                    var forgeSession = plugin.forgeGuiService().getSession(player);
                    if (forgeSession != null || plugin.recipeBookGuiService().isRecipeBookInventory(player)) {
                        player.closeInventory();
                    }
                    closure.complete(null);
                } catch (Throwable throwable) {
                    closure.completeExceptionally(throwable);
                }
            };
            try {
                if (plugin.threadOwnership().isEntityOwned(player)) {
                    close.run();
                } else {
                    var scheduled = plugin.executionDispatcher().runEntity(plugin, player, close,
                            () -> closure.complete(null));
                    if (scheduled == null) {
                        closure.completeExceptionally(new RejectedExecutionException(
                                "Forge GUI 关闭调度被拒绝."));
                    }
                }
            } catch (Throwable throwable) {
                closure.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(closures.toArray(CompletableFuture[]::new));
    }

    AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        YamlSection permission = configuration.getSection("permission");
        YamlSection condition = configuration.getSection("condition");
        YamlSection history = configuration.getSection("history");
        YamlSection numberFormat = configuration.getSection("number_format");
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.defaults().configVersion()),
                configuration.getBoolean("release_default_data", true),
                QualitySettings.fromConfig(configuration.get("quality")),
                numberFormat == null ? "0.##" : numberFormat.getString("default", "0.##"),
                numberFormat == null ? "0" : numberFormat.getString("integer", "0"),
                numberFormat == null ? "0.##%" : numberFormat.getString("percentage", "0.##%"),
                ownerRef.resolveOpBypass(configuration, permission),
                ConditionBlock.fromConfig(condition, true, false).invalidAsFailure(),
                history == null || history.getBoolean("enabled", true),
                history == null || history.getBoolean("auto_save", true),
                history == null ? 6000 : Numbers.tryParseInt(history.get("save_interval"), 6000)
        );
    }

    boolean resolveOpBypass(YamlSection configuration, YamlSection permission) {
        if (configuration.contains("op_bypass")) {
            return configuration.getBoolean("op_bypass", false);
        }
        if (permission != null && permission.contains("op_bypass")) {
            boolean legacyValue = permission.getBoolean("op_bypass", false);
            JavaPlugin.getPlugin(EmakiForgePlugin.class).getLogger().warning("[配置] 配置键 permission.op_bypass"
                    + " 已移到顶层 op_bypass, 当前按旧键值 " + legacyValue
                    + " 生效, 启动时会自动迁移到新键");
            return legacyValue;
        }
        return false;
    }

    void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
    }

}

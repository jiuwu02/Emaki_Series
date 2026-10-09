package emaki.jiuwu.craft.corelib.script.bridge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;
import emaki.jiuwu.craft.corelib.script.host.ScriptLoadReport;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class CoreLibScriptsCoordinator implements AutoCloseable {

    private static final String ENGINE_MISSING_REASON = "GraalJS 运行时库不可用";
    private static final String SCRIPTS_RESOURCE_ROOT = "scripts";

    private final EmakiCoreLibPlugin plugin;
    private final Path scriptsDirectory;
    private final MessageService messageService;

    private final List<ScriptActionStage> scriptStages = new ArrayList<>();
    private final List<CoreStageRegistration> stageHandles = new ArrayList<>();
    private final List<CoreTargetRegistration> conditionHandles = new ArrayList<>();
    private final List<PlaceholderResolver> placeholderResolvers = new ArrayList<>();
    private final List<String> expressionNames = new ArrayList<>();

    private ScriptHost host;
    private ScriptHostSettings settings;

    public CoreLibScriptsCoordinator(@NotNull EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
        this.scriptsDirectory = plugin.getDataFolder().toPath().resolve(SCRIPTS_RESOURCE_ROOT);
        this.messageService = plugin.messageService();
    }

    public synchronized void loadFromConfig() {
        CoreLibConfig.ScriptsConfig config = plugin.configModel().scriptsConfig();
        if (!config.enabled()) {
            disable();
            return;
        }
        if (host != null) {
            reload();
            return;
        }
        if (!ScriptHost.isEngineAvailable()) {
            messageService.warning("console.scripts.engine_unavailable",
                    Map.of("reason", ENGINE_MISSING_REASON));
            return;
        }
        try {
            createHost(config);
        } catch (Throwable throwable) {
            host = null;
            settings = null;
            messageService.warning("console.scripts.engine_unavailable",
                    Map.of("reason", describeThrowable(throwable)));
            return;
        }
        ScriptExampleReleaser.releaseIfNeeded(plugin, scriptsDirectory);
        plugin.stageRebuildListeners().register(plugin, this::reregisterActionStages);
        outputReport(host.load());
    }

    public synchronized void reload() {
        if (host == null) {
            return;
        }
        CoreLibConfig.ScriptsConfig config = plugin.configModel().scriptsConfig();
        if (!config.enabled()) {
            disable();
            return;
        }
        if (!settingsFor(config).equals(settings)) {
            unregisterAll();
            closeHost();
            loadFromConfig();
            return;
        }
        unregisterAll();
        outputReport(host.reload());
    }

    @Override
    public synchronized void close() {
        unregisterAll();
        closeHost();
    }

    private synchronized void disable() {
        messageService.info("console.scripts.disabled");
        unregisterAll();
        closeHost();
    }

    private void createHost(CoreLibConfig.ScriptsConfig config) {
        settings = settingsFor(config);
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("actions", new ActionStageScriptBinding(
                plugin::stageRegistry,
                plugin,
                this::currentRunner,
                settings.timeoutMs(),
                this::resolveReason,
                this::reportRegisterError,
                scriptStages,
                stageHandles));
        bindings.put("placeholders", new PlaceholderScriptBinding(
                plugin::placeholderRegistry,
                this::currentRunner,
                this::reportRegisterError,
                plugin.getLogger()::warning,
                placeholderResolvers));
        bindings.put("expressions", new ExpressionScriptBinding(
                this::currentRunner,
                this::reportRegisterError,
                plugin.getLogger()::warning,
                expressionNames));
        bindings.put("target_conditions", new TargetConditionScriptBinding(
                plugin::targetConditionRegistry,
                plugin,
                this::currentRunner,
                this::resolveReason,
                this::reportRegisterError,
                plugin.getLogger()::warning,
                conditionHandles));
        host = new ScriptHost(plugin, scriptsDirectory, settings, bindings);
    }

    private ScriptHostSettings settingsFor(CoreLibConfig.ScriptsConfig config) {
        return new ScriptHostSettings(config.enabled(), config.timeoutMs());
    }

    private ScriptCallbackRunner currentRunner() {
        ScriptHost current = host;
        return current == null ? null : current.callbackRunner();
    }

    private void reregisterActionStages() {
        if (host == null || scriptStages.isEmpty()) {
            return;
        }
        StageRegistry registry = plugin.stageRegistry();
        if (registry == null) {
            return;
        }
        closeStageHandles();
        for (ScriptActionStage stage : scriptStages) {
            CoreStageRegistration registration = registry.registerAction(plugin, stage);
            if (registration == null || !registration.successful()) {
                plugin.getLogger().warning("脚本动作段重新注册失败 '"
                        + stage.id() + "': " + (registration == null ? "no_registration" : registration.reasonKey()));
                continue;
            }
            stageHandles.add(registration);
        }
    }

    private void unregisterAll() {
        closeStageHandles();
        for (CoreTargetRegistration handle : conditionHandles) {
            try {
                handle.close();
            } catch (Exception exception) {
                plugin.getLogger().warning("脚本条件注销失败: " + exception.getMessage());
            }
        }
        conditionHandles.clear();
        PlaceholderRegistry placeholderRegistry = plugin.placeholderRegistry();
        for (PlaceholderResolver resolver : placeholderResolvers) {
            if (placeholderRegistry != null) {
                placeholderRegistry.unregister(resolver);
            }
        }
        placeholderResolvers.clear();
        for (String name : expressionNames) {
            ExpressionEngine.unregisterDynamicFunction(name);
        }
        expressionNames.clear();
        scriptStages.clear();
    }

    private void closeStageHandles() {
        for (CoreStageRegistration handle : stageHandles) {
            try {
                handle.close();
            } catch (Exception exception) {
                plugin.getLogger().warning("脚本段注销失败: " + exception.getMessage());
            }
        }
        stageHandles.clear();
    }

    private void closeHost() {
        ScriptHost current = host;
        host = null;
        settings = null;
        if (current != null) {
            current.close();
        }
    }

    private void outputReport(@NotNull ScriptLoadReport report) {
        messageService.info("console.scripts.loaded", Map.of(
                "loaded", String.valueOf(report.filesLoaded()),
                "skipped", String.valueOf(report.filesSkipped())));
        for (ScriptLoadReport.ScriptLoadError error : report.errors()) {
            messageService.warning("console.scripts.file_error", Map.of(
                    "file", error.file(),
                    "error", error.message()));
        }
    }

    private void reportRegisterError(@NotNull String message) {
        messageService.warning("console.scripts.register_error", Map.of("error", message));
    }

    private String resolveReason(@NotNull String reasonKey) {
        if (Texts.isBlank(reasonKey)) {
            return "未知";
        }
        int split = reasonKey.indexOf(':');
        String key = split >= 0 ? reasonKey.substring(0, split) : reasonKey;
        String localized = messageService.messageOrFallback(key, Map.of(), reasonKey);
        if (localized == null || Texts.isBlank(localized) || localized.equals(key)) {
            return reasonKey;
        }
        return split >= 0 ? localized + ": " + reasonKey.substring(split + 1) : localized;
    }

    private static String describeThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        String detail = message == null || message.isBlank()
                ? throwable.getClass().getName()
                : throwable.getClass().getName() + ": " + message;
        return Texts.toStringSafe(detail);
    }
}

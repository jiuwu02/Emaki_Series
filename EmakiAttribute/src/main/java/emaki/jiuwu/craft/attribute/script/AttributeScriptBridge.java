package emaki.jiuwu.craft.attribute.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.ScriptsConfig;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;
import emaki.jiuwu.craft.corelib.script.host.ScriptLoadReport;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class AttributeScriptBridge {

    private static final String SCRIPT_SOURCE = "script";
    private static final String BINDING_NAME = "attributes";
    private static final String SCRIPTS_DIRECTORY = "scripts";
    private static final String RESOURCE_PREFIX = "scripts/";
    private static final String UNKNOWN_FILE = "unknown";

    private final EmakiAttributePlugin plugin;
    private final AttributeRegistry attributeRegistry;
    private final MessageService messageService;
    private final AttributeScriptBinding binding;
    private ScriptHost host;
    private boolean closed;
    private boolean engineUnavailableReported;
    private boolean disabledReported;

    public AttributeScriptBridge(EmakiAttributePlugin plugin,
            AttributeRegistry attributeRegistry,
            MessageService messageService) {
        this.plugin = plugin;
        this.attributeRegistry = attributeRegistry;
        this.messageService = messageService;
        this.binding = new AttributeScriptBinding(attributeRegistry);
    }

    public synchronized void loadFromConfig() {
        if (closed) {
            return;
        }
        ScriptsConfig settings = scriptsConfig();
        if (!settings.enabled()) {
            reportDisabled();
            return;
        }
        ScriptHost created = ensureHost(settings);
        if (created == null) {
            return;
        }
        binding.reset();
        report(created.load());
    }

    public synchronized void reload() {
        if (closed || host == null) {
            return;
        }
        ScriptsConfig settings = scriptsConfig();
        if (!settings.enabled()) {
            clearScriptRegistrations();
            reportDisabled();
            return;
        }
        binding.reset();
        clearScriptRegistrations();
        report(host.reload());
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearScriptRegistrations();
        if (host != null) {
            try {
                host.close();
            } catch (Throwable _) {
            }
            host = null;
        }
    }

    private ScriptHost ensureHost(ScriptsConfig settings) {
        if (host != null) {
            return host;
        }
        String reason;
        try {
            if (!ScriptHost.isEngineAvailable()) {
                reason = "未找到 GraalJS 引擎类";
            } else {
                seedScriptsDirectory();
                host = new ScriptHost(
                        plugin,
                        scriptsDirectory(),
                        new ScriptHostSettings(settings.enabled(), settings.timeoutMs()),
                        Map.of(BINDING_NAME, binding)
                );
                return host;
            }
        } catch (Throwable throwable) {
            reason = Texts.toStringSafe(throwable.getMessage());
            if (Texts.isBlank(reason)) {
                reason = throwable.getClass().getName();
            }
        }
        reportEngineUnavailable(reason);
        return null;
    }

    private Path scriptsDirectory() {
        return plugin.dataPath(SCRIPTS_DIRECTORY);
    }

    private void seedScriptsDirectory() throws Exception {
        Path directory = scriptsDirectory();
        if (Files.isDirectory(directory)) {
            return;
        }
        Files.createDirectories(directory);
        for (String resource : YamlFiles.listResourcePaths(plugin, SCRIPTS_DIRECTORY)) {
            String relative = resource.startsWith(RESOURCE_PREFIX)
                    ? resource.substring(RESOURCE_PREFIX.length())
                    : resource;
            YamlFiles.copyResourceIfMissing(plugin, resource, directory.resolve(relative).toFile());
        }
    }

    private void clearScriptRegistrations() {
        if (attributeRegistry == null) {
            return;
        }
        try {
            attributeRegistry.clearRuntimeBySource(SCRIPT_SOURCE);
        } catch (Throwable _) {
        }
    }

    private ScriptsConfig scriptsConfig() {
        var config = plugin.configModel();
        return config == null || config.scripts() == null ? ScriptsConfig.defaults() : config.scripts();
    }

    private void report(ScriptLoadReport report) {
        info("script.loaded", Map.of(
                "loaded", String.valueOf(report.filesLoaded()),
                "skipped", String.valueOf(report.filesSkipped())
        ));
        for (ScriptLoadReport.ScriptLoadError error : report.errors()) {
            warning("script.file_error", Map.of(
                    "file", Texts.toStringSafe(error.file()),
                    "error", Texts.toStringSafe(error.message())
            ));
        }
        for (AttributePayloadParser.PayloadError error : binding.drainErrors()) {
            warning("script.register_error", Map.of("error", renderError(error)));
        }
        for (String id : binding.drainOverrides()) {
            warning("script.register_override", Map.of("id", Texts.toStringSafe(id)));
        }
    }

    private String renderError(AttributePayloadParser.PayloadError error) {
        String file = Texts.isBlank(error.file()) ? UNKNOWN_FILE : error.file();
        String field = Texts.toStringSafe(error.field());
        String value = Texts.toStringSafe(error.value());
        if (messageService == null) {
            return file + " " + field + " " + error.kind() + (value.isBlank() ? "" : ": " + value);
        }
        return switch (error.kind()) {
            case MISSING_ID -> messageService.message("script.payload_missing_id", Map.of("file", file));
            case INVALID_ENUM -> messageService.message("script.payload_invalid_enum", Map.of("file", file, "field", field, "value", value));
            case INVALID_NUMBER -> messageService.message("script.payload_invalid_number", Map.of("file", file, "field", field, "value", value));
            case INVALID_LIST -> messageService.message("script.payload_invalid_list", Map.of("file", file, "field", field));
        };
    }

    private void reportEngineUnavailable(String reason) {
        if (engineUnavailableReported) {
            return;
        }
        engineUnavailableReported = true;
        warning("script.engine_unavailable", Map.of("reason", Texts.toStringSafe(reason)));
    }

    private void reportDisabled() {
        if (disabledReported) {
            return;
        }
        disabledReported = true;
        info("script.disabled", Map.of());
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.info(key, replacements);
            return;
        }
        plugin.getLogger().info("[script] " + key);
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.warning(key, replacements);
            return;
        }
        plugin.getLogger().warning("[script] " + key);
    }
}

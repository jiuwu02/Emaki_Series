package emaki.jiuwu.craft.corelib.script.js;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptActionRegistrationApi {

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String scriptPath;
    private final List<String> registeredIds = new ArrayList<>();

    public JavaScriptActionRegistrationApi(Plugin plugin,
            ActionRegistry registry,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            String scriptPath) {
        this.plugin = plugin;
        this.registry = registry;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.scriptPath = scriptPath;
    }

    public List<String> registeredIds() {
        return List.copyOf(registeredIds);
    }

    @HostAccess.Export
    public boolean registerAction(Map<String, ?> definition) {
        if (definition == null || registry == null || javaScriptService == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            warn("JavaScript action id cannot be blank in " + scriptPath);
            return false;
        }
        JavaScriptRegisteredAction action = new JavaScriptRegisteredAction(
                plugin,
                javaScriptService,
                scriptConfig,
                id,
                value(definition, "category", "javascript"),
                value(definition, "description", id),
                parseParameters(definition.get("parameters")),
                parseExecutionMode(value(definition, "executionMode", "SYNC")),
                parseLong(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis()),
                scriptPath,
                value(definition, "execute", "execute"),
                value(definition, "validate", ""),
                parseBoolean(definition.get("acceptsDynamicParameters"), parseBoolean(definition.get("accepts_dynamic_parameters"), false))
        );
        ActionResult result = registry.register(action);
        if (result.success()) {
            registeredIds.add(id);
            log("Registered JavaScript action: " + id + " from " + scriptPath);
            return true;
        }
        warn("Failed to register JavaScript action " + id + ": " + result.errorMessage());
        return false;
    }

    @HostAccess.Export
    public void unregisterAction(String actionId) {
        if (registry != null) {
            String normalized = Texts.normalizeId(actionId);
            registry.unregister(normalized);
            registeredIds.remove(normalized);
        }
    }

    private List<ActionParameter> parseParameters(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<ActionParameter> parameters = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = Texts.normalizeId(value(map, "name", ""));
            if (Texts.isBlank(name)) {
                continue;
            }
            ActionParameterType type = parseParameterType(value(map, "type", "STRING"));
            boolean required = Boolean.parseBoolean(value(map, "required", "false"));
            String defaultValue = value(map, "defaultValue", value(map, "default_value", ""));
            String description = value(map, "description", "");
            parameters.add(required
                    ? ActionParameter.required(name, type, description)
                    : ActionParameter.optional(name, type, defaultValue, description));
        }
        return List.copyOf(parameters);
    }

    private ActionParameterType parseParameterType(String raw) {
        try {
            return ActionParameterType.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ActionParameterType.STRING;
        }
    }

    private ActionExecutionMode parseExecutionMode(String raw) {
        try {
            return ActionExecutionMode.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ActionExecutionMode.SYNC;
        }
    }

    private long parseLong(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Texts.toStringSafe(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(raw));
    }

    private String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Texts.toStringSafe(value);
    }

    private void log(String message) {
        if (plugin != null) {
            plugin.getLogger().info(message);
        }
    }

    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }
}

package emaki.jiuwu.craft.skills.script.js;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.skills.api.SkillActionExecutionMode;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;

public final class JavaScriptSkillRegistrationApi {

    private final EmakiSkillsPlugin plugin;
    private final SkillScriptActionRegistry registry;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final String scriptPath;
    private final List<String> registeredIds = new ArrayList<>();

    public JavaScriptSkillRegistrationApi(EmakiSkillsPlugin plugin,
            SkillScriptActionRegistry registry,
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
            plugin.messageService().warning("console.js_skill_action_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        JavaScriptSkillAction action = new JavaScriptSkillAction(
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
                value(definition, "validate", "")
        );
        SkillActionResult result = registry.register(plugin, action);
        if (result.success()) {
            registeredIds.add(id);
            plugin.messageService().info("console.js_skill_action_registered", Map.of(
                    "id", id,
                    "script", safe(scriptPath)
            ));
            return true;
        }
        plugin.messageService().warning("console.js_skill_action_register_failed", Map.of(
                "id", id,
                "error", safe(result.errorMessage())
        ));
        return false;
    }

    @HostAccess.Export
    public void unregisterAction(String actionId) {
        if (registry != null) {
            registry.unregister(actionId);
            registeredIds.remove(Texts.normalizeId(actionId));
        }
    }

    private List<SkillActionParameter> parseParameters(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<SkillActionParameter> parameters = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = Texts.normalizeId(value(map, "name", ""));
            if (Texts.isBlank(name)) {
                continue;
            }
            SkillActionParameterType type = parseParameterType(value(map, "type", "STRING"));
            boolean required = Boolean.parseBoolean(value(map, "required", "false"));
            String defaultValue = value(map, "defaultValue", "");
            String description = value(map, "description", "");
            parameters.add(required
                    ? SkillActionParameter.required(name, type, description)
                    : SkillActionParameter.optional(name, type, defaultValue, description));
        }
        return List.copyOf(parameters);
    }

    private SkillActionParameterType parseParameterType(String raw) {
        try {
            return SkillActionParameterType.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SkillActionParameterType.STRING;
        }
    }

    private SkillActionExecutionMode parseExecutionMode(String raw) {
        try {
            return SkillActionExecutionMode.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SkillActionExecutionMode.SYNC;
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

    private String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Texts.toStringSafe(value);
    }

    private String safe(String value) {
        return Texts.toStringSafe(value);
    }
}

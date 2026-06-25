package emaki.jiuwu.craft.corelib.script.js;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.script.js.event.JavaScriptEventRegistry;
import emaki.jiuwu.craft.corelib.script.js.event.JavaScriptEventSubscription;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTypes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptActionRegistrationApi {

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final PlaceholderRegistry placeholderRegistry;
    private final JavaScriptEventRegistry eventRegistry;
    private final JavaScriptService javaScriptService;
    private final MessageService messageService;
    private final ScriptConfig scriptConfig;
    private final String scriptPath;
    private final JavaScriptRegistrationTracker registrationTracker;
    private final JavaScriptExpressionFunctionRegistry expressionFunctionRegistry;
    private final JavaScriptConditionRegistry conditionRegistry;
    private final List<String> registeredIds = new ArrayList<>();
    private final List<JavaScriptPlaceholderResolver> registeredPlaceholders = new ArrayList<>();

    public JavaScriptActionRegistrationApi(Plugin plugin,
            ActionRegistry registry,
            PlaceholderRegistry placeholderRegistry,
            JavaScriptEventRegistry eventRegistry,
            JavaScriptService javaScriptService,
            MessageService messageService,
            ScriptConfig scriptConfig,
            String scriptPath) {
        this(plugin, registry, placeholderRegistry, eventRegistry, javaScriptService, messageService, scriptConfig, scriptPath, null, null, null);
    }

    public JavaScriptActionRegistrationApi(Plugin plugin,
            ActionRegistry registry,
            PlaceholderRegistry placeholderRegistry,
            JavaScriptEventRegistry eventRegistry,
            JavaScriptService javaScriptService,
            MessageService messageService,
            ScriptConfig scriptConfig,
            String scriptPath,
            JavaScriptRegistrationTracker registrationTracker,
            JavaScriptExpressionFunctionRegistry expressionFunctionRegistry,
            JavaScriptConditionRegistry conditionRegistry) {
        this.plugin = plugin;
        this.registry = registry;
        this.placeholderRegistry = placeholderRegistry;
        this.eventRegistry = eventRegistry;
        this.javaScriptService = javaScriptService;
        this.messageService = messageService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.scriptPath = scriptPath;
        this.registrationTracker = registrationTracker;
        this.expressionFunctionRegistry = expressionFunctionRegistry;
        this.conditionRegistry = conditionRegistry;
    }

    public List<String> registeredIds() {
        return List.copyOf(registeredIds);
    }

    public List<JavaScriptPlaceholderResolver> registeredPlaceholders() {
        return List.copyOf(registeredPlaceholders);
    }

    @HostAccess.Export
    public boolean registerAction(Map<String, ?> definition) {
        if (definition == null || registry == null || javaScriptService == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            warning("console.js_action_blank_id", Map.of("script", safe(scriptPath)));
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
        long started = System.nanoTime();
        ActionResult result = registry.register(plugin, scriptPath, action);
        if (result.success()) {
            if (registrationTracker != null && !registrationTracker.register(plugin,
                    scriptPath,
                    JavaScriptRegistrationTypes.ACTION,
                    id,
                    elapsedMillis(started),
                    () -> registry.unregister(id),
                    Map.of("category", action.category(), "description", action.description()))) {
                registry.unregister(id);
                return false;
            }
            registeredIds.add(id);
            info("console.js_action_registered", Map.of(
                    "id", id,
                    "script", safe(scriptPath)
            ));
            return true;
        }
        warning("console.js_action_register_failed", Map.of(
                "id", id,
                "error", safe(result.errorMessage())
        ));
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

    @HostAccess.Export
    public boolean registerPlaceholder(Map<String, ?> definition) {
        if (definition == null || placeholderRegistry == null || javaScriptService == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            warning("console.js_placeholder_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        JavaScriptPlaceholderResolver resolver = new JavaScriptPlaceholderResolver(
                plugin,
                javaScriptService,
                scriptConfig,
                id,
                scriptPath,
                value(definition, "function", value(definition, "resolve", "resolve")),
                parseLong(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis())
        );
        long started = System.nanoTime();
        placeholderRegistry.register(resolver);
        if (registrationTracker != null && !registrationTracker.register(plugin,
                scriptPath,
                JavaScriptRegistrationTypes.PLACEHOLDER,
                id,
                elapsedMillis(started),
                () -> placeholderRegistry.unregister(resolver),
                Map.of("token", "%" + id + "%"))) {
            placeholderRegistry.unregister(resolver);
            return false;
        }
        registeredPlaceholders.add(resolver);
        info("console.js_placeholder_registered", Map.of(
                "placeholder", "%" + id + "%",
                "script", safe(scriptPath)
        ));
        return true;
    }

    @HostAccess.Export
    public void unregisterPlaceholder(String placeholderId) {
        String normalized = Texts.normalizeId(placeholderId);
        for (JavaScriptPlaceholderResolver resolver : List.copyOf(registeredPlaceholders)) {
            if (resolver.id().equals(normalized)) {
                placeholderRegistry.unregister(resolver);
                registeredPlaceholders.remove(resolver);
            }
        }
    }

    @HostAccess.Export
    public boolean onEvent(Map<String, ?> definition) {
        if (definition == null || eventRegistry == null || javaScriptService == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        String eventType = Texts.normalizeId(value(definition, "event", ""));
        if (Texts.isBlank(id) || !JavaScriptEventRegistry.isSupported(eventType)) {
            warning("console.js_event_unsupported", Map.of(
                    "event", safe(eventType),
                    "script", safe(scriptPath)
            ));
            return false;
        }
        JavaScriptEventSubscription subscription = new JavaScriptEventSubscription(
                id,
                eventType,
                parsePriority(value(definition, "priority", "NORMAL")),
                parseBoolean(definition.get("ignoreCancelled"), parseBoolean(definition.get("ignore_cancelled"), true)),
                scriptPath,
                value(definition, "function", value(definition, "execute", "execute")),
                parseLong(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis())
        );
        long started = System.nanoTime();
        boolean registered = eventRegistry.register(subscription);
        if (registered) {
            if (registrationTracker != null && !registrationTracker.register(plugin,
                    scriptPath,
                    JavaScriptRegistrationTypes.EVENT,
                    id,
                    elapsedMillis(started),
                    () -> eventRegistry.unregister(id),
                    Map.of("event", eventType, "function", subscription.functionName()))) {
                eventRegistry.unregister(id);
                return false;
            }
            info("console.js_event_registered", Map.of(
                    "id", id,
                    "event", eventType,
                    "script", safe(scriptPath)
            ));
        }
        return registered;
    }

    @HostAccess.Export
    public void offEvent(String id) {
        if (eventRegistry != null) {
            eventRegistry.unregister(id);
        }
    }

    @HostAccess.Export
    public boolean registerExpressionFunction(Map<String, ?> definition) {
        if (definition == null || expressionFunctionRegistry == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            warning("console.js_expression_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        boolean registered = expressionFunctionRegistry.register(
                scriptPath,
                id,
                value(definition, "description", id),
                parseStringList(definition.get("parameters")),
                value(definition, "function", value(definition, "execute", id)),
                parseLong(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis())
        );
        if (registered) {
            info("console.js_expression_registered", Map.of("id", id, "script", safe(scriptPath)));
        } else {
            warning("console.js_expression_register_failed", Map.of("id", id));
        }
        return registered;
    }

    @HostAccess.Export
    public boolean registerCondition(Map<String, ?> definition) {
        if (definition == null || conditionRegistry == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            warning("console.js_condition_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        boolean registered = conditionRegistry.register(
                scriptPath,
                id,
                value(definition, "description", id),
                parseStringList(definition.get("parameters")),
                value(definition, "function", value(definition, "execute", id)),
                parseLong(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis())
        );
        if (registered) {
            info("console.js_condition_registered", Map.of("id", id, "script", safe(scriptPath)));
        } else {
            warning("console.js_condition_register_failed", Map.of("id", id));
        }
        return registered;
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

    private List<String> parseStringList(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : iterable) {
            String value = Texts.normalizeId(Texts.toStringSafe(item));
            if (Texts.isNotBlank(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
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

    private EventPriority parsePriority(String raw) {
        try {
            return EventPriority.valueOf(Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return EventPriority.NORMAL;
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

    private String safe(Object value) {
        return Texts.toStringSafe(value);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.info(key, replacements);
        }
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.warning(key, replacements);
        }
    }
}

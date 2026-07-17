package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptAttributeContributionProvider;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamagePipelineRegistry;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptAttributeRegistrationApi {

    private final EmakiAttributePlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final JavaScriptDamageHookRegistry damageHookRegistry;
    private final JavaScriptDamagePipelineRegistry damagePipelineRegistry;
    private final String scriptPath;
    private final Set<String> registeredProviders = new LinkedHashSet<>();

    public JavaScriptAttributeRegistrationApi(EmakiAttributePlugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            JavaScriptDamageHookRegistry damageHookRegistry,
            JavaScriptDamagePipelineRegistry damagePipelineRegistry,
            String scriptPath) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.damageHookRegistry = damageHookRegistry;
        this.damagePipelineRegistry = damagePipelineRegistry;
        this.scriptPath = scriptPath;
    }

    public Set<String> registeredProviders() {
        return Set.copyOf(registeredProviders);
    }

    @HostAccess.Export
    public boolean registerAttribute(Map<String, ?> definitionMap) {
        AttributeDefinition definition = ScriptAttributeDefinitionParser.parse(definitionMap);
        if (definition == null || Texts.isBlank(definition.id())) {
            plugin.messageService().warning("console.js_attribute_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        boolean registered = plugin.attributeRegistry().registerRuntime(definition, scriptPath);
        if (registered) {
            plugin.messageService().info("console.js_attribute_registered", Map.of(
                    "id", definition.id(),
                    "script", safe(scriptPath)
            ));
        }
        return registered;
    }

    @HostAccess.Export
    public boolean registerDamageType(Map<String, ?> definitionMap) {
        DamageTypeDefinition definition = DamageTypeDefinition.fromMap(definitionMap, this::resolveAttributeId);
        if (definition == null || Texts.isBlank(definition.id()) || plugin.damageTypeRegistry() == null) {
            plugin.messageService().warning("console.js_damage_type_invalid", Map.of("script", safe(scriptPath)));
            return false;
        }
        boolean registered = plugin.damageTypeRegistry().registerRuntime(definition, scriptPath);
        if (registered) {
            plugin.messageService().info("console.js_damage_type_registered", Map.of(
                    "id", definition.id(),
                    "script", safe(scriptPath)
            ));
        }
        return registered;
    }

    @HostAccess.Export
    public boolean registerDamagePipeline(Map<String, ?> definition) {
        if (definition == null || damagePipelineRegistry == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        String function = value(definition, "function", value(definition, "calculate", ""));
        if (Texts.isBlank(id) || Texts.isBlank(function)) {
            plugin.messageService().warning("console.js_damage_pipeline_invalid", Map.of("script", safe(scriptPath)));
            return false;
        }
        damagePipelineRegistry.register(id,
                scriptPath,
                function,
                Math.round(number(definition.get("timeoutMillis"), scriptConfig.engine().defaultTimeoutMillis())));
        plugin.messageService().info("console.js_damage_pipeline_registered", Map.of(
                "id", id,
                "script", safe(scriptPath)
        ));
        return true;
    }

    @HostAccess.Export
    public boolean registerProvider(Map<String, ?> definition) {
        if (definition == null || plugin.attributeService() == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            plugin.messageService().warning("console.js_attribute_provider_blank_id", Map.of("script", safe(scriptPath)));
            return false;
        }
        JavaScriptAttributeContributionProvider provider = new JavaScriptAttributeContributionProvider(
                plugin,
                javaScriptService,
                scriptConfig,
                id,
                (int) Math.round(number(definition.get("priority"), 0D)),
                scriptPath,
                value(definition, "function", value(definition, "collect", "collect"))
        );
        plugin.attributeService().registerContributionProvider(provider);
        registeredProviders.add(id);
        plugin.messageService().info("console.js_attribute_provider_registered", Map.of(
                "id", id,
                "script", safe(scriptPath)
        ));
        return true;
    }

    @HostAccess.Export
    public boolean onDamage(Map<String, ?> definition) {
        if (definition == null || damageHookRegistry == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        String function = value(definition, "function", "");
        if (Texts.isBlank(id) || Texts.isBlank(function)) {
            plugin.messageService().warning("console.js_damage_hook_invalid", Map.of("script", safe(scriptPath)));
            return false;
        }
        damageHookRegistry.register(id,
                (int) Math.round(number(definition.get("priority"), 0D)),
                stringSet(definition.get("damageTypes")),
                scriptPath,
                function);
        plugin.messageService().info("console.js_damage_hook_registered", Map.of(
                "id", id,
                "script", safe(scriptPath)
        ));
        return true;
    }

    private String resolveAttributeId(String id) {
        if (Texts.isBlank(id)) {
            return "";
        }
        String normalized = Texts.normalizeId(id);
        if (plugin.attributeRegistry() != null) {
            AttributeDefinition definition = plugin.attributeRegistry().resolve(normalized);
            if (definition != null) {
                return definition.id();
            }
        }
        return normalized;
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object entry : iterable) {
            if (entry != null) {
                result.add(Texts.normalizeId(entry.toString()));
            }
        }
        return Set.copyOf(result);
    }

    private String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Texts.toStringSafe(value);
    }

    private String safe(String value) {
        return Texts.toStringSafe(value);
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}

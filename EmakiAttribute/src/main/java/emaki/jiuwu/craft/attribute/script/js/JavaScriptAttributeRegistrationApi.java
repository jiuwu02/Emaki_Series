package emaki.jiuwu.craft.attribute.script.js;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptAttributeRegistrationApi {

    private final EmakiAttributePlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final JavaScriptDamageHookRegistry damageHookRegistry;
    private final String scriptPath;
    private final Set<String> registeredProviders = new LinkedHashSet<>();

    public JavaScriptAttributeRegistrationApi(EmakiAttributePlugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            JavaScriptDamageHookRegistry damageHookRegistry,
            String scriptPath) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.damageHookRegistry = damageHookRegistry;
        this.scriptPath = scriptPath;
    }

    public Set<String> registeredProviders() {
        return Set.copyOf(registeredProviders);
    }

    @HostAccess.Export
    public boolean registerAttribute(Map<String, ?> definitionMap) {
        AttributeDefinition definition = ScriptAttributeDefinitionParser.parse(definitionMap);
        if (definition == null || Texts.isBlank(definition.id())) {
            plugin.getLogger().warning("JavaScript attribute definition id cannot be blank in " + scriptPath);
            return false;
        }
        boolean registered = plugin.attributeRegistry().registerRuntime(definition, scriptPath);
        if (registered) {
            plugin.getLogger().info("Registered JavaScript attribute: " + definition.id() + " from " + scriptPath);
        }
        return registered;
    }

    @HostAccess.Export
    public boolean registerProvider(Map<String, ?> definition) {
        if (definition == null || plugin.attributeService() == null) {
            return false;
        }
        String id = Texts.normalizeId(value(definition, "id", ""));
        if (Texts.isBlank(id)) {
            plugin.getLogger().warning("JavaScript attribute provider id cannot be blank in " + scriptPath);
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
        plugin.getLogger().info("Registered JavaScript attribute provider: " + id + " from " + scriptPath);
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
            plugin.getLogger().warning("JavaScript damage hook requires id and function in " + scriptPath);
            return false;
        }
        damageHookRegistry.register(id,
                (int) Math.round(number(definition.get("priority"), 0D)),
                stringSet(definition.get("damageTypes")),
                scriptPath,
                function);
        plugin.getLogger().info("Registered JavaScript damage hook: " + id + " from " + scriptPath);
        return true;
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

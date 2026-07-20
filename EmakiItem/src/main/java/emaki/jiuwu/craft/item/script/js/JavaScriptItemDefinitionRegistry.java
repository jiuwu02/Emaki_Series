package emaki.jiuwu.craft.item.script.js;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class JavaScriptItemDefinitionRegistry {


    private static final String REGISTRATION_TYPE = "item_definition";

    private final EmakiItemPlugin plugin;
    private final EmakiItemDefinitionParser parser;
    private final Map<String, Entry> definitions = new LinkedHashMap<>();

    public JavaScriptItemDefinitionRegistry(EmakiItemPlugin plugin) {
        this.plugin = plugin;
        this.parser = new EmakiItemDefinitionParser(plugin == null ? null : plugin.getLogger());
    }

    public synchronized boolean register(ScriptModuleContext context,
            Map<String, ?> rawDefinition,
            JavaScriptRegistrationTracker tracker) {
        if (rawDefinition == null) {
            recordError(context, tracker, "", "register", "Item definition cannot be null.");
            return false;
        }
        String id = Texts.normalizeId(value(rawDefinition, "id", ""));
        if (Texts.isBlank(id)) {
            recordError(context, tracker, id, "register", "Item definition id cannot be blank.");
            return false;
        }
        boolean override = bool(rawDefinition.get("override"), false);
        if (!override && plugin.itemLoader() != null && plugin.itemLoader().get(id) != null) {
            recordError(context, tracker, id, "register", "YAML item definition already exists. Set override=true to override it.");
            return false;
        }
        EmakiItemDefinition definition = parseDefinition(id, rawDefinition, context, tracker);
        if (definition == null) {
            return false;
        }
        long started = System.nanoTime();
        definitions.put(id, new Entry(definition, override, scriptPath(context)));
        if (tracker != null && !tracker.register(plugin,
                scriptPath(context),
                REGISTRATION_TYPE,
                id,
                elapsedMillis(started),
                () -> unregister(id),
                Map.of("override", override))) {
            definitions.remove(id);
            return false;
        }
        if (plugin.itemFactory() != null) {
            plugin.itemFactory().clearCache();
        }
        return true;
    }

    public synchronized void unregister(String id) {
        definitions.remove(Texts.normalizeId(id));
        if (plugin.itemFactory() != null) {
            plugin.itemFactory().clearCache();
        }
    }

    public synchronized void clear() {
        definitions.clear();
        if (plugin.itemFactory() != null) {
            plugin.itemFactory().clearCache();
        }
    }

    public synchronized EmakiItemDefinition get(String id) {
        Entry entry = definitions.get(Texts.normalizeId(id));
        return entry == null ? null : entry.definition();
    }

    public synchronized EmakiItemDefinition getOverride(String id) {
        Entry entry = definitions.get(Texts.normalizeId(id));
        return entry != null && entry.override() ? entry.definition() : null;
    }

    public synchronized Map<String, EmakiItemDefinition> all() {
        Map<String, EmakiItemDefinition> result = new LinkedHashMap<>();
        definitions.forEach((id, entry) -> result.put(id, entry.definition()));
        return Map.copyOf(result);
    }

    public synchronized List<String> ids() {
        return definitions.keySet().stream().sorted().toList();
    }


    public EmakiItemDefinition parseDefinition(String id,
            Map<String, ?> rawDefinition,
            ScriptModuleContext context,
            JavaScriptRegistrationTracker tracker) {
        Map<String, Object> normalized = normalizeScriptDefinition(id, rawDefinition);
        EmakiItemDefinition definition = parser.parse(normalized, "javascript:" + scriptPath(context));
        if (definition == null) {
            recordError(context, tracker, id, "register", "Item definition failed shared source/component validation.");
        }
        return definition;
    }

    private Map<String, Object> normalizeScriptDefinition(String id, Map<String, ?> rawDefinition) {
        Map<String, Object> normalized = plainMap(rawDefinition);
        normalized.put("id", Texts.normalizeId(id));
        alias(normalized, "displayName", "display_name");
        alias(normalized, "itemName", "item_name");
        alias(normalized, "nameActions", "name_actions");
        alias(normalized, "loreActions", "lore_actions");
        alias(normalized, "equipSlot", "equip_slot");
        alias(normalized, "eaAttributes", "ea_attributes");
        alias(normalized, "skills", "es_skills");
        alias(normalized, "esSkills", "es_skills");
        alias(normalized, "skillTriggers", "skill_triggers");
        alias(normalized, "esSkillTriggers", "es_skill_triggers");
        aliasLegacyComponentFields(normalized);
        normalizeLegacyVanillaSource(normalized);

        Object componentsRaw = normalized.get("components");
        if (componentsRaw instanceof Map<?, ?> componentsMap) {
            Map<String, Object> components = plainMap(componentsMap);
            aliasLegacyComponentFields(components);
            normalized.put("components", components);
        }

        Object itemRaw = normalized.get("item");
        if (itemRaw instanceof Map<?, ?> itemMap) {
            Map<String, Object> item = plainMap(itemMap);
            alias(item, "displayName", "display_name");
            alias(item, "itemName", "item_name");
            aliasLegacyComponentFields(item);
            Object itemComponentsRaw = item.get("components");
            if (itemComponentsRaw instanceof Map<?, ?> itemComponentsMap) {
                Map<String, Object> itemComponents = plainMap(itemComponentsMap);
                aliasLegacyComponentFields(itemComponents);
                item.put("components", itemComponents);
            }
            normalizeLegacyVanillaSource(item);
            normalized.put("item", item);
        }

        Map<String, Object> variables = plainMap(normalized.get("variables"));
        Map<String, Object> metadata = plainMap(normalized.get("metadata"));
        if (!metadata.isEmpty()) {
            variables.put("metadata", metadata);
        }
        List<String> tags = Texts.asStringList(normalized.get("tags"));
        if (!tags.isEmpty()) {
            variables.put("tags", tags);
        }
        if (!variables.isEmpty()) {
            normalized.put("variables", variables);
        }
        return normalized;
    }

    private void aliasLegacyComponentFields(Map<String, Object> values) {
        alias(values, "customModelData", "custom_model_data");
        alias(values, "itemModel", "item_model");
        alias(values, "tooltipStyle", "tooltip_style");
        alias(values, "itemFlags", "item_flags");
        alias(values, "hideTooltip", "hide_tooltip");
        alias(values, "enchantmentGlintOverride", "enchantment_glint_override");
        alias(values, "maxStackSize", "max_stack_size");
        alias(values, "maxDamage", "max_damage");
        alias(values, "attributeModifiers", "attribute_modifiers");
    }

    private void normalizeLegacyVanillaSource(Map<String, Object> values) {
        Object rawSource = values.get("source");
        if (!(rawSource instanceof String source) || ItemSourceUtil.parse(source) != null) {
            return;
        }
        var material = ItemSourceUtil.resolveVanillaMaterial(source);
        if (material != null && material.isItem()) {
            values.put("source", ItemSourceUtil.canonicalVanillaShorthand(material.name()));
        }
    }

    private Map<String, Object> plainMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        ConfigNodes.entries(raw).forEach((key, value) -> result.put(key, ConfigNodes.toPlainData(value)));
        return result;
    }

    private void alias(Map<String, Object> values, String source, String target) {
        if (!values.containsKey(source)) {
            return;
        }
        if (!values.containsKey(target)) {
            values.put(target, values.get(source));
        }
        if (!source.equals(target)) {
            values.remove(source);
        }
    }

    private void recordError(ScriptModuleContext context,
            JavaScriptRegistrationTracker tracker,
            String id,
            String phase,
            String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), REGISTRATION_TYPE, id, phase, message);
        }
        if (plugin != null) {
            plugin.getLogger().warning("[JavaScript] Item definition registration failed: " + message);
        }
    }

    private static String value(Map<String, ?> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        String text = Texts.toStringSafe(value);
        return Texts.isBlank(text) ? fallback : text;
    }

    private static boolean bool(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        return raw == null ? fallback : Boolean.parseBoolean(Texts.toStringSafe(raw));
    }

    private static String scriptPath(ScriptModuleContext context) {
        return context == null ? "" : context.scriptPath();
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record Entry(EmakiItemDefinition definition, boolean override, String scriptPath) {
    }
}

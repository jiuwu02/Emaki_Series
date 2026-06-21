package emaki.jiuwu.craft.item.script.js;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemComponentsConfig;
import emaki.jiuwu.craft.item.model.ItemConditions;
import emaki.jiuwu.craft.item.model.ItemSetMembership;

public final class JavaScriptItemDefinitionRegistry {

    private final EmakiItemPlugin plugin;
    private final Map<String, Entry> definitions = new LinkedHashMap<>();

    public JavaScriptItemDefinitionRegistry(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean register(ScriptModuleContext context, Map<String, ?> rawDefinition, JavaScriptRegistrationTracker tracker) {
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
                JavaScriptRegistrationType.ITEM_DEFINITION,
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

    public EmakiItemDefinition parseDefinition(String id, Map<String, ?> rawDefinition, ScriptModuleContext context, JavaScriptRegistrationTracker tracker) {
        Material material = material(rawDefinition);
        if (material == null || !material.isItem()) {
            recordError(context, tracker, id, "register", "Item definition material/source must resolve to a vanilla item material.");
            return null;
        }
        Map<String, Object> componentMap = objectMap(rawDefinition.get("components"));
        Object customModelData = firstPresent(rawDefinition, componentMap, "custom_model_data", "customModelData");
        ItemComponentsConfig components = new ItemComponentsConfig(
                ConfigNodes.toPlainData(customModelData),
                firstText(rawDefinition, componentMap, "item_model", "itemModel"),
                firstText(rawDefinition, componentMap, "tooltip_style", "tooltipStyle"),
                Map.of(),
                List.of(),
                bool(firstPresent(rawDefinition, componentMap, "hide_tooltip", "hideTooltip"), false),
                bool(firstPresent(rawDefinition, componentMap, "unbreakable", "unbreakable"), false),
                boolObject(firstPresent(rawDefinition, componentMap, "enchantment_glint_override", "enchantmentGlintOverride")),
                intObject(firstPresent(rawDefinition, componentMap, "max_stack_size", "maxStackSize")),
                firstText(rawDefinition, componentMap, "rarity", "rarity"),
                null,
                null,
                null,
                List.of(),
                ""
        );
        return new EmakiItemDefinition(
                id,
                material,
                ConfigNodes.toPlainData(rawDefinition.containsKey("display_name") ? rawDefinition.get("display_name") : rawDefinition.get("displayName")),
                value(rawDefinition, "item_name", value(rawDefinition, "itemName", "")),
                ConfigNodes.toPlainData(rawDefinition.get("lore")),
                ConfigNodes.toPlainData(rawDefinition.get("name_actions")),
                ConfigNodes.toPlainData(rawDefinition.get("lore_actions")),
                variables(rawDefinition),
                components,
                objectMap(rawDefinition.get("ea_attributes")),
                stringList(rawDefinition.containsKey("skills") ? rawDefinition.get("skills") : rawDefinition.get("es_skills")),
                value(rawDefinition, "equip_slot", "all"),
                ItemSetMembership.empty(),
                ItemConditions.empty(),
                actionMap(rawDefinition.get("actions")),
                null,
                null,
                false
        );
    }

    private Material material(Map<String, ?> rawDefinition) {
        String materialText = value(rawDefinition, "material", "");
        Material material = ItemSourceUtil.resolveVanillaMaterial(materialText);
        if (material != null) {
            return material;
        }
        String sourceText = value(rawDefinition, "source", "");
        if (Texts.isBlank(sourceText)) {
            return null;
        }
        return ItemSourceUtil.resolveVanillaMaterial(sourceText);
    }

    private Map<String, Object> variables(Map<String, ?> rawDefinition) {
        Map<String, Object> result = new LinkedHashMap<>(objectMap(rawDefinition.get("variables")));
        Map<String, Object> metadata = objectMap(rawDefinition.get("metadata"));
        if (!metadata.isEmpty()) {
            result.put("metadata", metadata);
        }
        List<String> tags = stringList(rawDefinition.get("tags"));
        if (!tags.isEmpty()) {
            result.put("tags", tags);
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Object> objectMap(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                result.put(Texts.normalizeId(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, List<String>> actionMap(Object raw) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            List<String> lines = stringList(entry.getValue());
            if (Texts.isNotBlank(entry.getKey()) && !lines.isEmpty()) {
                result.put(Texts.normalizeId(entry.getKey()), lines);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<String> stringList(Object raw) {
        return Texts.asStringList(raw).stream().filter(Texts::isNotBlank).map(String::trim).toList();
    }

    private void recordError(ScriptModuleContext context, JavaScriptRegistrationTracker tracker, String id, String phase, String message) {
        if (tracker != null) {
            tracker.recordError(scriptPath(context), JavaScriptRegistrationType.ITEM_DEFINITION, id, phase, message);
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
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(raw));
    }

    private static Boolean boolObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = Texts.toStringSafe(raw);
        return Texts.isBlank(text) ? null : Boolean.parseBoolean(text);
    }

    private static Integer intObject(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = Texts.toStringSafe(raw);
            return Texts.isBlank(text) ? null : Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Object firstPresent(Map<String, ?> rawDefinition, Map<String, Object> componentMap, String snakeKey, String camelKey) {
        if (rawDefinition != null && rawDefinition.containsKey(snakeKey)) {
            return rawDefinition.get(snakeKey);
        }
        if (rawDefinition != null && rawDefinition.containsKey(camelKey)) {
            return rawDefinition.get(camelKey);
        }
        if (componentMap != null && componentMap.containsKey(Texts.normalizeId(snakeKey))) {
            return componentMap.get(Texts.normalizeId(snakeKey));
        }
        return componentMap == null ? null : componentMap.get(Texts.normalizeId(camelKey));
    }

    private static String firstText(Map<String, ?> rawDefinition, Map<String, Object> componentMap, String snakeKey, String camelKey) {
        String text = Texts.toStringSafe(firstPresent(rawDefinition, componentMap, snakeKey, camelKey));
        return Texts.isBlank(text) ? "" : text;
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

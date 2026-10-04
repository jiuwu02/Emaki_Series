package emaki.jiuwu.craft.corelib.item;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class MinecraftItemComponentCatalog {

    public enum Scope {
        UNIVERSAL,
        MATERIAL
    }

    public record Entry(String componentId,
            String displayName,
            String description,
            String icon,
            String valueFormat,
            boolean nonValued,
            Scope scope,
            List<String> appliesTo) {

        public Entry {
            scope = scope == null ? Scope.UNIVERSAL : scope;
            appliesTo = appliesTo == null ? List.of() : List.copyOf(appliesTo);
        }

        public Entry(String componentId, String valueFormat, boolean nonValued) {
            this(componentId, null, null, null, valueFormat, nonValued, Scope.UNIVERSAL, List.of());
        }

        /** 组件在编辑器中的显示名；缺失时回退为组件 ID。 */
        public String displayNameOrId() {
            return Texts.isBlank(displayName) ? componentId : displayName;
        }

        /** 组件描述（MiniMessage，可含 &lt;newline&gt;）；缺失时返回空串。 */
        public String descriptionText() {
            return description == null ? "" : description;
        }

        /** 组件在编辑器中显示图标所用的物品源；缺失时返回 null。 */
        public String iconSource() {
            return Texts.isBlank(icon) ? null : icon;
        }

        public boolean materialScoped() {
            return scope == Scope.MATERIAL;
        }

        public boolean appliesTo(String materialId) {
            if (appliesTo.isEmpty()) {
                return true;
            }
            return appliesTo.contains(normalizeMaterialId(materialId));
        }
    }

    private static final String MATERIAL_NAMESPACE = "minecraft:";

    private final Map<String, Entry> entries;

    public MinecraftItemComponentCatalog() {
        this.entries = createEntries();
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public Entry entry(String componentId) {
        return entries.get(componentId);
    }

    public List<Entry> universalEntries() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.materialScoped()) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public List<Entry> materialScopedEntries() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.materialScoped()) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public List<Entry> specializedFor(String materialId) {
        String normalized = normalizeMaterialId(materialId);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.materialScoped() && entry.appliesTo(normalized)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public boolean isApplicable(String componentId, String materialId) {
        Entry entry = entry(componentId);
        return entry != null && entry.appliesTo(materialId);
    }

    static String normalizeMaterialId(String materialId) {
        if (materialId == null) {
            return "";
        }
        String trimmed = Texts.lower(materialId).trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.contains(":") ? trimmed : MATERIAL_NAMESPACE + trimmed;
    }

    private Map<String, Entry> createEntries() {
        Map<String, Entry> resourceEntries = loadResourceEntries();
        if (!resourceEntries.isEmpty()) {
            return resourceEntries;
        }
        Map<String, Entry> result = new LinkedHashMap<>();
        add(result, "max_stack_size", "integer 1..99");
        add(result, "max_damage", "positive integer");
        add(result, "damage", "non-negative integer");
        addUnit(result, "unbreakable");
        add(result, "custom_name", "MiniMessage string or vanilla text component map/list");
        add(result, "item_name", "MiniMessage string or vanilla text component map/list");
        add(result, "lore", "MiniMessage string/list or vanilla text component list");
        add(result, "rarity", "common, uncommon, rare, or epic");
        add(result, "enchantments", "direct enchantment resource id to level map");
        add(result, "can_place_on", "vanilla adventure predicate map");
        add(result, "can_break", "vanilla adventure predicate map");
        add(result, "attribute_modifiers", "direct attribute modifier list");
        add(result, "custom_model_data", "map containing floats/flags/strings/colors");
        add(result, "repair_cost", "non-negative integer");
        add(result, "enchantment_glint_override", "boolean");
        addUnit(result, "intangible_projectile");
        add(result, "food", "vanilla food properties map");
        add(result, "consumable", "vanilla consumable map");
        add(result, "use_remainder", "item stack map");
        add(result, "use_cooldown", "vanilla cooldown map");
        add(result, "damage_resistant", "damage type tag map");
        add(result, "tool", "vanilla tool rules map");
        add(result, "weapon", "vanilla weapon properties map");
        add(result, "enchantable", "map containing positive integer value");
        add(result, "equippable", "vanilla equippable map");
        add(result, "repairable", "repair item/tag map");
        addUnit(result, "glider");
        add(result, "item_model", "namespaced resource id");
        add(result, "tooltip_style", "namespaced resource id");
        add(result, "tooltip_display", "map containing hide_tooltip/hidden_components");
        add(result, "death_protection", "vanilla death protection map");
        add(result, "blocks_attacks", "vanilla blocking properties map");
        add(result, "stored_enchantments", "vanilla enchantment component map");
        add(result, "dyed_color", "RGB integer or color map");
        add(result, "potion_contents", "vanilla potion contents map");
        add(result, "charged_projectiles", "item stack list");
        add(result, "bundle_contents", "item stack list");
        add(result, "trim", "trim material/pattern map");
        add(result, "custom_data", "plain map or {$snbt: raw SNBT}");
        add(result, "entity_data", "plain map or {$snbt: raw SNBT}");
        add(result, "block_entity_data", "plain map or {$snbt: raw SNBT}");
        add(result, "block_state", "block state property map");

        add(result, "use_effects", "vanilla use effects map");
        add(result, "minimum_attack_charge", "floating-point number");
        add(result, "damage_type", "damage type resource id");
        add(result, "piercing_weapon", "vanilla piercing weapon map");
        add(result, "kinetic_weapon", "vanilla kinetic weapon map");
        add(result, "attack_range", "vanilla attack range map");
        add(result, "swing_animation", "vanilla swing animation map");
        add(result, "break_sound", "sound resource id");
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Entry> loadResourceEntries() {
        try (InputStream inputStream = MinecraftItemComponentCatalog.class.getResourceAsStream("/item-components.yml")) {
            if (inputStream == null) {
                return Map.of();
            }
            YamlSection root = YamlFiles.load(inputStream);
            List<?> configuredEntries = root.getList("components");
            Map<String, Entry> result = new LinkedHashMap<>();
            for (Object raw : configuredEntries) {
                String componentId = ConfigNodes.string(raw, "id", null);
                if (Texts.isBlank(componentId)) {
                    continue;
                }
                String normalizedId = componentId.contains(":")
                        ? Texts.lower(componentId).trim()
                        : MATERIAL_NAMESPACE + Texts.lower(componentId).trim();
                if (result.containsKey(normalizedId)) {
                    throw new IllegalArgumentException("Duplicate item component catalog id: " + normalizedId);
                }
                String scopeToken = Texts.lower(ConfigNodes.string(raw, "scope", "universal")).trim();
                Scope scope = "material".equals(scopeToken) ? Scope.MATERIAL : Scope.UNIVERSAL;
                result.put(normalizedId, new Entry(
                        normalizedId,
                        ConfigNodes.string(raw, "name", null),
                        ConfigNodes.string(raw, "description", null),
                        ConfigNodes.string(raw, "icon", null),
                        ConfigNodes.string(raw, "format", "vanilla component value"),
                        ConfigNodes.bool(raw, "non_valued", false),
                        scope,
                        readMaterialIds(raw)
                ));
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private List<String> readMaterialIds(Object raw) {
        List<Object> configured = ConfigNodes.asObjectList(ConfigNodes.get(raw, "applies_to"));
        if (configured.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(configured.size());
        for (Object element : configured) {
            String materialId = normalizeMaterialId(Texts.toStringSafe(element));
            if (!materialId.isEmpty() && !result.contains(materialId)) {
                result.add(materialId);
            }
        }
        return result;
    }

    private void add(Map<String, Entry> entries, String id, String format) {
        String namespacedId = MATERIAL_NAMESPACE + id;
        entries.put(namespacedId, new Entry(namespacedId, format, false));
    }

    private void addUnit(Map<String, Entry> entries, String id) {
        String namespacedId = MATERIAL_NAMESPACE + id;
        entries.put(namespacedId, new Entry(namespacedId, "unit: true, null, or empty map", true));
    }
}

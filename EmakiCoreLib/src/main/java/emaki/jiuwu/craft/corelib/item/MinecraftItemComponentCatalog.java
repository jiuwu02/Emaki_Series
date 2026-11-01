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
            List<String> appliesTo,
            String version) {

        public Entry {
            scope = scope == null ? Scope.UNIVERSAL : scope;
            appliesTo = appliesTo == null ? List.of() : List.copyOf(appliesTo);
        }

        public Entry(String componentId, String valueFormat, boolean nonValued) {
            this(componentId, null, null, null, valueFormat, nonValued, Scope.UNIVERSAL, List.of(), null);
        }

        public String displayNameOrId() {
            return Texts.isBlank(displayName) ? componentId : displayName;
        }

        public String descriptionText() {
            return description == null ? "" : description;
        }

        public String iconSource() {
            return Texts.isBlank(icon) ? null : icon;
        }

        public String versionRequirement() {
            return version == null ? "" : version.trim();
        }

        public boolean applicableTo(String serverVersion) {
            return MinecraftServerVersions.satisfies(versionRequirement(), serverVersion);
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
        add(result, "max_stack_size", "1..99 的整数");
        add(result, "max_damage", "正整数");
        add(result, "damage", "非负整数");
        addUnit(result, "unbreakable");
        add(result, "custom_name", "MiniMessage 字符串，或原版文本组件 map/list");
        add(result, "item_name", "MiniMessage 字符串，或原版文本组件 map/list");
        add(result, "lore", "MiniMessage 字符串/list，或原版文本组件 list");
        add(result, "rarity", "common、uncommon、rare 或 epic");
        add(result, "enchantments", "附魔资源 id 到等级的映射");
        add(result, "can_place_on", "原版冒险谓词 map");
        add(result, "can_break", "原版冒险谓词 map");
        add(result, "attribute_modifiers", "属性修饰符列表");
        add(result, "custom_model_data", "包含 floats/flags/strings/colors 的 map");
        add(result, "repair_cost", "非负整数");
        add(result, "enchantment_glint_override", "布尔值");
        addUnit(result, "intangible_projectile");
        add(result, "food", "原版食物属性 map");
        add(result, "consumable", "原版可消耗物 map");
        add(result, "use_remainder", "物品堆 map");
        add(result, "use_cooldown", "原版冷却 map");
        add(result, "damage_resistant", "伤害类型标签 map");
        add(result, "tool", "原版工具规则 map");
        add(result, "weapon", "原版武器属性 map");
        add(result, "enchantable", "包含正整数值的 map");
        add(result, "equippable", "原版可装备 map");
        add(result, "repairable", "修理物品/标签 map");
        addUnit(result, "glider");
        add(result, "item_model", "带命名空间的资源 id");
        add(result, "tooltip_style", "带命名空间的资源 id");
        add(result, "tooltip_display", "包含 hide_tooltip/hidden_components 的 map");
        add(result, "death_protection", "原版死亡保护 map");
        add(result, "blocks_attacks", "原版格挡属性 map");
        add(result, "stored_enchantments", "原版附魔组件 map");
        add(result, "dyed_color", "RGB 整数或颜色 map");
        add(result, "potion_contents", "原版药水内容 map");
        add(result, "charged_projectiles", "物品堆列表");
        add(result, "bundle_contents", "物品堆列表");
        add(result, "trim", "盔甲纹饰材料/图案 map");
        add(result, "custom_data", "普通 map 或 {$snbt: 原始 SNBT}");
        add(result, "entity_data", "普通 map 或 {$snbt: 原始 SNBT}");
        add(result, "block_entity_data", "普通 map 或 {$snbt: 原始 SNBT}");
        add(result, "block_state", "方块状态属性 map");

        add(result, "use_effects", "原版使用效果 map");
        add(result, "minimum_attack_charge", "浮点数");
        add(result, "damage_type", "伤害类型资源 id");
        add(result, "piercing_weapon", "原版穿刺武器 map");
        add(result, "kinetic_weapon", "原版动能武器 map");
        add(result, "attack_range", "原版攻击范围 map");
        add(result, "swing_animation", "原版挥动动画 map");
        add(result, "break_sound", "音效资源 id");
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
                    throw new IllegalArgumentException("重复的物品组件目录 id: " + normalizedId);
                }
                String scopeToken = Texts.lower(ConfigNodes.string(raw, "scope", "universal")).trim();
                Scope scope = "material".equals(scopeToken) ? Scope.MATERIAL : Scope.UNIVERSAL;
                result.put(normalizedId, new Entry(
                        normalizedId,
                        ConfigNodes.string(raw, "name", null),
                        ConfigNodes.string(raw, "description", null),
                        ConfigNodes.string(raw, "icon", null),
                        ConfigNodes.string(raw, "format", "原版组件值"),
                        ConfigNodes.bool(raw, "non_valued", false),
                        scope,
                        readMaterialIds(raw),
                        ConfigNodes.string(raw, "version", null)
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
        entries.put(namespacedId, new Entry(namespacedId, "unit：true、null 或空 map", true));
    }
}

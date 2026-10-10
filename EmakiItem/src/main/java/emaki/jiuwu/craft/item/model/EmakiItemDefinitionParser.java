package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemParser;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class EmakiItemDefinitionParser {

    private static final int DEFAULT_AMOUNT = 1;

    public static final Set<String> BUILT_IN_EFFECT_TYPES = Set.of(
            "variables",
            "ea_attribute",
            "es_skill",
            "accessory_slot",
            "name_action",
            "lore_action");

    private static final List<String> RETIRED_TOP_LEVEL_KEYS = List.of(
            "variables",
            "ea_attributes",
            "es_skills",
            "es_skill_triggers",
            "skill_triggers",
            "accessory_slots",
            "name_actions",
            "lore_actions",
            "source",
            "amount",
            "components",
            "material",
            "display_name",
            "item_name",
            "lore");

    private static final List<String> RETIRED_REPAIR_MATERIAL_KEYS = List.of("item_source", "item");

    private final Logger logger;
    private final ConfiguredItemParser configuredItemParser;

    public EmakiItemDefinitionParser(Logger logger) {
        this(logger, new ConfiguredItemParser());
    }

    public EmakiItemDefinitionParser(Logger logger, ConfiguredItemParser configuredItemParser) {
        this.logger = logger;
        this.configuredItemParser = configuredItemParser == null ? new ConfiguredItemParser() : configuredItemParser;
    }

    public EmakiItemDefinition parse(Map<String, ?> root, String source) {
        return parse(root == null ? null : new MapYamlSection(root), source);
    }

    public EmakiItemDefinition parse(YamlSection root, String source) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        String id = Texts.normalizeId(root.getString("id"));
        if (Texts.isBlank(id)) {
            warning("[loader] 跳过物品定义 " + source + ": ID 无效或缺失");
            return null;
        }
        warnRetiredFields(root, source);
        ConfiguredItemDefinition itemDefinition;
        try {
            itemDefinition = parseConfiguredItem(root, id);
        } catch (IllegalArgumentException exception) {
            warning("[loader] 跳过物品定义 " + source + ": " + exception.getMessage());
            return null;
        }
        List<Map<?, ?>> effects = root.getMapList("effects");
        Map<String, Object> variables = parseVariables(effects);
        Map<String, Object> resolvedValidationVariables;
        try {
            resolvedValidationVariables = variables.isEmpty()
                    ? Map.of()
                    : ExpressionEngine.resolveMixedVariables(variables, Map.of());
        } catch (RuntimeException exception) {
            warning("[loader] 跳过物品定义 " + source + ": 变量校验失败: " + exception.getMessage());
            return null;
        }
        if (!validateConfiguredItem(itemDefinition, source, resolvedValidationVariables)) {
            return null;
        }

        Map<String, Object> attributes = parseAttributes(effects);
        boolean random = containsRandom(itemDefinition.components().values().stream()
                .filter(patch -> patch.operation() == ItemComponentPatch.Operation.SET)
                .map(ItemComponentPatch::value)
                .toList())
                || containsRandom(effects);
        return new EmakiItemDefinition(
                id,
                itemDefinition,
                parseDisplayActions(effects, "name_action", "name_actions", "name_action"),
                parseDisplayActions(effects, "lore_action", "lore_actions", "lore_action"),
                variables,
                attributes,
                parseSkills(effects),
                parseSkillTriggers(effects),
                parseEquipSlot(root, id, source),
                parseAccessorySlots(effects),
                parseCustomEffects(effects),
                parseSetMembership(root.getSection("set")),
                parseConditions(root),
                parseActions(root.getSection("actions")),
                parseUpdate(root.getSection("update"), id, source),
                parseRepair(root.getSection("repair")),
                random
        );
    }

    private ConfiguredItemDefinition parseConfiguredItem(YamlSection root, String itemId) {
        Object nestedItem = root.get("item");
        boolean hasNestedItem = nestedItem instanceof Map<?, ?> || nestedItem instanceof YamlSection || nestedItem instanceof String;
        if (!hasNestedItem) {
            throw new IllegalArgumentException("缺少 'item' 段；基础物品必须在 'item' 下声明。");
        }
        ConfiguredItemDefinition shared = configuredItemParser.parse(nestedItem);
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>(shared.components());
        patches.putAll(parseComponents(ConfigNodes.section(nestedItem, "components"), itemId).toComponentPatches());
        return new ConfiguredItemDefinition(shared.source(), DEFAULT_AMOUNT, patches);
    }

    private boolean validateConfiguredItem(ConfiguredItemDefinition definition,
            String source,
            Map<String, Object> variables) {
        ItemBuildResult result = EmakiCoreLibApi.createConfiguredItem(resolveValidationDefinition(definition, variables));
        for (ItemBuildIssue issue : result.issues()) {
            warning("[loader] 物品定义 " + source + " [" + Texts.toStringSafe(issue.componentId()) + "]: " + issue.message());
        }
        if (!result.success() || result.hasErrors() || result.itemStack() == null) {
            warning("[loader] 跳过物品定义 " + source + ": 物品来源或组件校验失败");
            return false;
        }
        return true;
    }

    private ConfiguredItemDefinition resolveValidationDefinition(ConfiguredItemDefinition definition,
            Map<String, Object> variables) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        definition.components().forEach((componentId, patch) -> patches.put(componentId,
                patch.operation() == ItemComponentPatch.Operation.SET
                        ? ItemComponentPatch.set(resolveValidationValue(componentId, patch.value(), variables))
                        : patch));
        String source = definition.source() == null
                ? null
                : Texts.formatTemplate(definition.source(), variables);
        return new ConfiguredItemDefinition(source, definition.amount(), patches);
    }

    private Object resolveValidationValue(String componentId, Object raw, Map<String, Object> variables) {
        if ("minecraft:custom_name".equals(componentId) || "minecraft:item_name".equals(componentId)) {
            return ExpressionEngine.evaluateStringConfig(raw, variables);
        }
        if ("minecraft:lore".equals(componentId)) {
            return ExpressionEngine.evaluateStringLinesConfig(raw, variables);
        }
        Object value = ConfigNodes.toPlainData(raw);
        if (value instanceof String text) {
            return Texts.formatTemplate(text, variables);
        }
        if (value instanceof Map<?, ?> map) {
            String type = Texts.normalizeId(Texts.toStringSafe(map.get("type"))).replace('-', '_');
            if (List.of("range", "uniform", "gaussian", "normal", "skew_normal", "triangle").contains(type)) {
                return ExpressionEngine.evaluateRandomConfig(map, variables);
            }
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (key != null) {
                    resolved.put(String.valueOf(key), resolveValidationValue(componentId, nested, variables));
                }
            });
            return resolved;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> resolved = new ArrayList<>();
            iterable.forEach(nested -> resolved.add(resolveValidationValue(componentId, nested, variables)));
            return resolved;
        }
        return value;
    }

    private ItemComponentsConfig parseComponents(YamlSection section, String itemId) {
        if (section == null) {
            return ItemComponentsConfig.empty();
        }
        List<VanillaAttributeModifierConfig> modifiers = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("attribute_modifiers")) {
            Object amount = ConfigNodes.toPlainData(entry.get("amount"));
            String attribute = Texts.normalizeId(Texts.toStringSafe(entry.get("attribute")));
            if (Texts.isBlank(attribute) || amount == null) {
                continue;
            }
            modifiers.add(new VanillaAttributeModifierConfig(
                    attribute,
                    amount,
                    Texts.toStringSafe(entry.containsKey("operation") ? entry.get("operation") : "add_number"),
                    Texts.toStringSafe(entry.containsKey("slot") ? entry.get("slot") : "any"),
                    Texts.toStringSafe(entry.containsKey("name") ? entry.get("name") : "emakiitem:" + itemId + "/" + attribute),
                    containsRandom(amount)
            ));
        }
        return new ItemComponentsConfig(
                section.get("custom_model_data"),
                section.getString("item_model", ""),
                section.getString("tooltip_style", ""),
                toIntegerMap(section.get("enchantments")),
                normalizedList(section.get("item_flags")),
                section.getBoolean("hide_tooltip", false),
                section.getBoolean("unbreakable", false),
                section.getBoolean("enchantment_glint_override", null),
                section.getInt("max_stack_size", null),
                section.getString("rarity", ""),
                section.getInt("damage", null),
                section.getInt("max_damage", null),
                section.getInt("enchantable", null),
                modifiers,
                section.getString("raw", "")
        );
    }

    private ItemConditions parseConditions(YamlSection root) {
        return root == null ? ItemConditions.empty() : new ItemConditions(ConditionBlock.fromRoot(root, true, false));
    }

    private Map<String, List<String>> parseActions(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> actions = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            List<String> lines = normalizedList(section.get(key));
            if (!lines.isEmpty()) {
                actions.put(Texts.normalizeId(key), lines);
            }
        }
        return actions;
    }

    private ItemUpdatePolicy parseUpdate(YamlSection section, String itemId, String source) {
        if (section == null) {
            return ItemUpdatePolicy.defaults();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", null));
        Integer configuredVersion = section.getInt("version", null);
        if (!enabled) {
            return ItemUpdatePolicy.defaults();
        }
        if (configuredVersion == null || configuredVersion < 1) {
            warning("[loader] 物品定义 " + source + " 为 '" + itemId + "' 启用了更新，但缺少有效的 update.version；物品更新已禁用");
            return ItemUpdatePolicy.defaults();
        }
        return new ItemUpdatePolicy(
                configuredVersion,
                true,
                section.getBoolean("preserve_amount", null),
                section.getBoolean("preserve_damage", null),
                section.getBoolean("preserve_unknown_attribute_sources", null),
                parseUpdateTriggers(section.getSection("triggers"))
        );
    }

    private ItemUpdatePolicy.TriggerPolicy parseUpdateTriggers(YamlSection section) {
        if (section == null) {
            return ItemUpdatePolicy.TriggerPolicy.empty();
        }
        return new ItemUpdatePolicy.TriggerPolicy(
                section.getBoolean("join", null),
                section.getBoolean("held_change", null),
                section.getBoolean("inventory_click", null),
                section.getBoolean("inventory_drag", null),
                section.getBoolean("pickup", null),
                section.getBoolean("interact", null),
                section.getBoolean("command", null)
        );
    }

    private String parseEquipSlot(YamlSection root, String itemId, String source) {
        String configured = root.getString("equip_slot", EquipmentSlotMatcher.SLOT_ALL);
        String normalized = EquipmentSlotMatcher.normalizeRequired(configured);
        if (isSupportedEquipSlot(normalized)) {
            return normalized;
        }
        warning("[loader] 物品定义 " + source + " 为 '" + itemId + "' 配置了不支持的 equip_slot '" + configured
                + "'；回退为 'all'");
        return EquipmentSlotMatcher.SLOT_ALL;
    }

    private boolean isSupportedEquipSlot(String slot) {
        return switch (slot) {
            case EquipmentSlotMatcher.SLOT_ALL,
                EquipmentSlotMatcher.SLOT_HAND,
                EquipmentSlotMatcher.SLOT_MAIN_HAND,
                EquipmentSlotMatcher.SLOT_OFF_HAND,
                EquipmentSlotMatcher.SLOT_HELMET,
                EquipmentSlotMatcher.SLOT_CHESTPLATE,
                EquipmentSlotMatcher.SLOT_LEGGINGS,
                EquipmentSlotMatcher.SLOT_BOOTS -> true;
            default -> false;
        };
    }

    private List<String> parseAccessorySlots(List<Map<?, ?>> effects) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null
                    || !"accessory_slot".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.addAll(normalizeSlotIds(ConfigNodes.get(effect, "accessory_slots")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private List<String> normalizeSlotIds(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            String normalized = Texts.normalizeId(entry);
            if (Texts.isNotBlank(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseCustomEffects(List<Map<?, ?>> effects) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null) {
                continue;
            }
            String type = Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type")));
            if (Texts.isBlank(type) || BUILT_IN_EFFECT_TYPES.contains(type)) {
                continue;
            }
            Object plain = ConfigNodes.toPlainData(effect);
            if (plain instanceof Map<?, ?> map) {
                Map<String, Object> entry = new LinkedHashMap<>();
                map.forEach((key, value) -> {
                    if (key != null) {
                        entry.put(String.valueOf(key), value);
                    }
                });
                result.add(Collections.unmodifiableMap(entry));
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private ItemSetMembership parseSetMembership(YamlSection section) {
        if (section == null) {
            return ItemSetMembership.empty();
        }
        return new ItemSetMembership(section.getString("id", ""), section.getString("piece", ""));
    }

    private RepairConfig parseRepair(YamlSection section) {
        if (section == null) {
            return RepairConfig.disabled();
        }
        boolean enabled = Boolean.TRUE.equals(section.getBoolean("enabled", false));
        if (!enabled) {
            return RepairConfig.disabled();
        }
        List<RepairMaterial> materials = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("materials")) {
            if (entry == null) {
                continue;
            }
            List<ItemSourceRef> itemSources = parseRepairItemSources(entry);
            Matcher matcher = parseRepairMatcher(entry);
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            String restore = Texts.toStringSafe(ConfigNodes.get(entry, "restore"));
            if ((!itemSources.isEmpty() || matcher != null) && Texts.isNotBlank(restore)) {
                materials.add(new RepairMaterial(itemSources, amount, restore, matcher));
            }
        }
        RepairEconomyConfig economy = parseRepairEconomy(section.getSection("economy"));
        DisabledDisplay disabledDisplay = parseDisabledDisplay(section.getSection("disabled_display"));
        List<String> onDisabled = normalizedList(section.get("on_disabled"));
        List<String> onRepaired = normalizedList(section.get("on_repaired"));
        return new RepairConfig(true, materials, economy, disabledDisplay, onDisabled, onRepaired);
    }

    private Matcher parseRepairMatcher(Map<?, ?> entry) {
        Object rawMatcher = ConfigNodes.get(entry, "matcher");
        if (rawMatcher instanceof YamlSection matcherSection) {
            return matcherSection.isEmpty() ? null : Matcher.fromConfig(matcherSection);
        }
        if (rawMatcher instanceof Map<?, ?> matcherMap) {
            return matcherMap.isEmpty() ? null : Matcher.fromConfig(matcherMap);
        }
        return null;
    }

    private List<ItemSourceRef> parseRepairItemSources(Map<?, ?> entry) {
        List<ItemSourceRef> result = new ArrayList<>();
        for (Object rawSource : ConfigNodes.asObjectList(ConfigNodes.get(entry, "item_sources"))) {
            ItemSourceRef source = ItemSourceUtil.parse(rawSource);
            if (source != null) {
                result.add(source);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private RepairEconomyConfig parseRepairEconomy(YamlSection section) {
        if (section == null) {
            return RepairEconomyConfig.disabled();
        }
        List<RepairCurrencyCost> currencies = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("currencies")) {
            if (entry == null) {
                continue;
            }
            String currencyId = ConfigNodes.string(entry, "currency_id", "");
            String costFormula = ConfigNodes.string(entry, "cost_formula", "");
            RepairCurrencyCost currency = new RepairCurrencyCost(
                    ConfigNodes.string(entry, "provider", "auto"),
                    currencyId,
                    Numbers.tryParseDouble(ConfigNodes.get(entry, "amount"), 0D),
                    Numbers.tryParseDouble(ConfigNodes.get(entry, "base_cost"), 0D),
                    costFormula,
                    ConfigNodes.string(entry, "display_name", "")
            );
            if (currency.hasCost()) {
                currencies.add(currency);
            }
        }
        Boolean enabledValue = section.getBoolean("enabled");
        boolean enabled = enabledValue != null ? enabledValue : !currencies.isEmpty();
        String restore = section.getString("restore", "100%");
        return new RepairEconomyConfig(enabled, restore, currencies);
    }

    private DisabledDisplay parseDisabledDisplay(YamlSection section) {
        if (section == null) {
            return DisabledDisplay.empty();
        }
        return new DisabledDisplay(
                section.getString("name_prefix", ""),
                normalizedList(section.get("lore_append"))
        );
    }

    private Map<String, Object> parseVariables(List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"variables".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "variables"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Object> parseAttributes(List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"ea_attribute".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "ea_attributes"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<String> parseSkills(List<Map<?, ?>> effects) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.addAll(normalizedList(ConfigNodes.get(effect, "es_skills")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private Map<String, String> parseSkillTriggers(List<Map<?, ?>> effects) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergeSkillTriggers(result, ConfigNodes.get(effect, "es_skill_triggers"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private void mergeSkillTriggers(Map<String, String> target, Object raw) {
        if (target == null || raw == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            String skillId = Texts.normalizeId(entry.getKey());
            String triggerId = Texts.normalizeId(Texts.toStringSafe(entry.getValue())).replace('-', '_');
            if (Texts.isNotBlank(skillId) && Texts.isNotBlank(triggerId)) {
                target.put(skillId, triggerId);
            }
        }
    }

    private Object parseDisplayActions(List<Map<?, ?>> effects, String effectType, String topKey, String effectKey) {
        List<Object> actions = new ArrayList<>();
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !effectType.equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            appendDisplayActions(actions, ConfigNodes.get(effect, topKey));
            appendDisplayActions(actions, ConfigNodes.get(effect, effectKey));
        }
        return actions.isEmpty() ? List.of() : List.copyOf(actions);
    }

    private void appendDisplayActions(List<Object> actions, Object raw) {
        if (actions == null || raw == null) {
            return;
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    actions.add(entry);
                }
            }
            return;
        }
        actions.add(plain);
    }

    private void mergePlainMap(Map<String, Object> target, Object raw) {
        if (target == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                target.put(Texts.normalizeId(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
    }

    private Map<String, Integer> toIntegerMap(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            Integer value = Numbers.tryParseInt(entry.getValue(), null);
            if (Texts.isNotBlank(entry.getKey()) && value != null && value > 0) {
                result.put(Texts.toStringSafe(entry.getKey()).toLowerCase(Locale.ROOT), value);
            }
        }
        return result;
    }

    private List<String> normalizedList(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isNotBlank(entry)) {
                result.add(entry.trim());
            }
        }
        return result;
    }

    private boolean containsRandom(Object raw) {
        Object value = ConfigNodes.toPlainData(raw);
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type != null) {
                String normalized = Texts.normalizeId(Texts.toStringSafe(type)).replace('-', '_');
                if (List.of("random_text", "random_text_lines", "random_lines", "random_line",
                        "range", "uniform", "gaussian", "normal", "skew_normal", "triangle").contains(normalized)) {
                    return true;
                }
            }
            for (Object nested : map.values()) {
                if (containsRandom(nested)) {
                    return true;
                }
            }
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (containsRandom(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void warnRetiredFields(YamlSection root, String source) {
        Set<String> present = new LinkedHashSet<>();
        for (String key : RETIRED_TOP_LEVEL_KEYS) {
            if (root.get(key) != null) {
                present.add(key);
            }
        }
        YamlSection item = root.getSection("item");
        if (item != null && item.get("amount") != null) {
            present.add("item.amount");
        }
        for (Map<?, ?> effect : root.getMapList("effects")) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            if (ConfigNodes.get(effect, "skill_triggers") != null) {
                present.add("effects.skill_triggers");
            }
        }
        YamlSection repair = root.getSection("repair");
        if (repair != null) {
            for (Map<?, ?> material : repair.getMapList("materials")) {
                if (material == null) {
                    continue;
                }
                for (String key : RETIRED_REPAIR_MATERIAL_KEYS) {
                    if (ConfigNodes.get(material, key) != null) {
                        present.add("repair.materials." + key);
                    }
                }
            }
        }
        if (!present.isEmpty()) {
            warning("[loader] 物品定义 " + source + " 声明了已停用的字段 " + String.join(", ", present)
                    + "；这些字段会被忽略，请将基础物品声明为 'item.source' + 'item.components'，将效果写成 'effects' 条目"
                    + "，并把堆叠数量传给 give 命令");
        }
    }

    private void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}

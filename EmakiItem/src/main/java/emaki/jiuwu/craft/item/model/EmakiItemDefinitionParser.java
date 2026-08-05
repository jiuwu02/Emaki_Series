package emaki.jiuwu.craft.item.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;

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
import emaki.jiuwu.craft.corelib.item.LegacyConfiguredItemConverter;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class EmakiItemDefinitionParser {

    private final Logger logger;
    private final ConfiguredItemParser configuredItemParser;
    private final LegacyConfiguredItemConverter legacyConverter;

    public EmakiItemDefinitionParser(Logger logger) {
        this(logger, new ConfiguredItemParser());
    }

    public EmakiItemDefinitionParser(Logger logger, ConfiguredItemParser configuredItemParser) {
        this.logger = logger;
        this.configuredItemParser = configuredItemParser == null ? new ConfiguredItemParser() : configuredItemParser;
        this.legacyConverter = new LegacyConfiguredItemConverter(this.configuredItemParser);
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
            warning("Skipping item definition " + source + ": invalid or missing id.");
            return null;
        }
        ConfiguredItemDefinition itemDefinition;
        try {
            itemDefinition = parseConfiguredItem(root, id, source);
        } catch (IllegalArgumentException exception) {
            warning("Skipping item definition " + source + ": " + exception.getMessage());
            return null;
        }
        List<Map<?, ?>> effects = root.getMapList("effects");
        Map<String, Object> variables = parseVariables(root, effects);
        Map<String, Object> resolvedValidationVariables;
        try {
            resolvedValidationVariables = variables.isEmpty()
                    ? Map.of()
                    : ExpressionEngine.resolveMixedVariables(variables, Map.of());
        } catch (RuntimeException exception) {
            warning("Skipping item definition " + source + ": variable validation failed: " + exception.getMessage());
            return null;
        }
        if (!validateConfiguredItem(itemDefinition, source, resolvedValidationVariables)) {
            return null;
        }

        Map<String, Object> attributes = parseAttributes(root, effects);
        boolean random = containsRandom(itemDefinition.components().values().stream()
                .filter(patch -> patch.operation() == ItemComponentPatch.Operation.SET)
                .map(ItemComponentPatch::value)
                .toList())
                || containsRandom(root.get("name_actions"))
                || containsRandom(root.get("lore_actions"))
                || containsRandom(root.get("variables"))
                || containsRandom(effects);
        return new EmakiItemDefinition(
                id,
                itemDefinition,
                parseDisplayActions(root, effects, "name_action", "name_actions", "name_action"),
                parseDisplayActions(root, effects, "lore_action", "lore_actions", "lore_action"),
                variables,
                attributes,
                parseSkills(root, effects),
                parseSkillTriggers(root, effects),
                parseEquipSlot(root, id, source),
                parseSetMembership(root.getSection("set")),
                parseConditions(root),
                parseActions(root.getSection("actions")),
                parseUpdate(root.getSection("update"), id, source),
                parseRepair(root.getSection("repair")),
                random
        );
    }

    private ConfiguredItemDefinition parseConfiguredItem(YamlSection root, String itemId, String sourceLabel) {
        Object nestedItem = root.get("item");
        boolean hasNestedItem = nestedItem instanceof Map<?, ?> || nestedItem instanceof YamlSection || nestedItem instanceof String;
        Object configuredNode = hasNestedItem ? nestedItem : root;
        ConfiguredItemDefinition shared = configuredItemParser.parse(configuredNode);
        String itemSource = shared.source();
        if (Texts.isBlank(itemSource) && hasNestedItem && root.get("source") != null) {
            itemSource = configuredItemParser.parse(Map.of("source", root.get("source"))).source();
        }
        if (Texts.isBlank(itemSource)) {
            itemSource = legacyMaterialSource(root.getString("material", ""));
        }
        int amount = hasNestedItem && ConfigNodes.contains(configuredNode, "amount")
                ? shared.amount()
                : Math.max(1, root.getInt("amount", shared.amount()));

        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        patches.putAll(shared.components());
        removeLegacyOnlyComponentPatches(patches, configuredNode);
        ConfiguredItemDefinition converted = legacyConverter.convert(itemSource, amount, configuredNode, Map.of());
        patches.putAll(converted.components());
        YamlSection legacyComponents = ConfigNodes.section(configuredNode, "components");
        patches.putAll(parseComponents(legacyComponents, itemId).toComponentPatches());
        overlayLegacyTextComponents(patches, configuredNode);

        if (hasNestedItem) {
            ConfiguredItemDefinition rootLegacy = legacyConverter.convert(itemSource, amount, root, Map.of());
            rootLegacy.components().forEach(patches::putIfAbsent);
            parseComponents(root.getSection("components"), itemId).toComponentPatches().forEach(patches::putIfAbsent);
            overlayLegacyTextComponentsFallback(patches, root);
        }
        warnIgnoredRawComponent(configuredNode, sourceLabel);
        if (hasNestedItem) {
            warnIgnoredRawComponent(root, sourceLabel);
        }
        return new ConfiguredItemDefinition(itemSource, amount, patches);
    }

    private void removeLegacyOnlyComponentPatches(Map<String, ItemComponentPatch> patches, Object raw) {
        Object components = ConfigNodes.get(raw, "components");
        for (String legacyKey : List.of("raw", "item_flags", "hide_tooltip",
                "hidden_components", "display_name")) {
            if (ConfigNodes.contains(components, legacyKey)) {
                patches.remove("minecraft:" + legacyKey);
            }
        }
    }

    private void overlayLegacyTextComponents(Map<String, ItemComponentPatch> patches, Object raw) {
        Object displayName = ConfigNodes.get(raw, "display_name");
        if (displayName != null) {
            patches.put("minecraft:custom_name", ItemComponentPatch.set(ConfigNodes.toPlainData(displayName)));
        }
        Object itemName = ConfigNodes.get(raw, "item_name");
        if (itemName != null && Texts.isNotBlank(itemName)) {
            patches.put("minecraft:item_name", ItemComponentPatch.set(ConfigNodes.toPlainData(itemName)));
        }
        if (ConfigNodes.contains(raw, "lore")) {
            Object lore = ConfigNodes.get(raw, "lore");
            patches.put("minecraft:lore", ItemComponentPatch.set(ConfigNodes.toPlainData(lore == null ? List.of() : lore)));
        }
    }

    private void overlayLegacyTextComponentsFallback(Map<String, ItemComponentPatch> patches, Object raw) {
        Map<String, ItemComponentPatch> legacyText = new LinkedHashMap<>();
        overlayLegacyTextComponents(legacyText, raw);
        legacyText.forEach(patches::putIfAbsent);
    }

    private String legacyMaterialSource(String materialName) {
        Material material = ItemSourceUtil.resolveVanillaMaterial(materialName);
        return material == null || !material.isItem()
                ? null
                : "minecraft-" + material.name().toLowerCase(Locale.ROOT);
    }

    private boolean validateConfiguredItem(ConfiguredItemDefinition definition,
            String source,
            Map<String, Object> variables) {
        ItemBuildResult result = EmakiCoreLibApi.createConfiguredItem(resolveValidationDefinition(definition, variables));
        for (ItemBuildIssue issue : result.issues()) {
            warning("Item definition " + source + " [" + Texts.toStringSafe(issue.componentId()) + "]: " + issue.message());
        }
        if (!result.success() || result.hasErrors() || result.itemStack() == null) {
            warning("Skipping item definition " + source + ": item source or component validation failed.");
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

    private void warnIgnoredRawComponent(Object raw, String source) {
        Object components = ConfigNodes.get(raw, "components");
        if (Texts.isNotBlank(ConfigNodes.get(components, "raw"))) {
            warning("Item definition " + source + " uses legacy components.raw; the shared item model cannot safely normalize this free-form item string, so it was ignored.");
        }
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
            warning("Item definition " + source + " enables update for '" + itemId + "' but has no valid update.version; item updates are disabled.");
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
        warning("Item definition " + source + " configures unsupported equip_slot '" + configured
                + "' for '" + itemId + "'; falling back to 'all'.");
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
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            String restore = Texts.toStringSafe(ConfigNodes.get(entry, "restore"));
            if (!itemSources.isEmpty() && Texts.isNotBlank(restore)) {
                materials.add(new RepairMaterial(itemSources, amount, restore));
            }
        }
        RepairEconomyConfig economy = parseRepairEconomy(section.getSection("economy"));
        DisabledDisplay disabledDisplay = parseDisabledDisplay(section.getSection("disabled_display"));
        List<String> onDisabled = normalizedList(section.get("on_disabled"));
        List<String> onRepaired = normalizedList(section.get("on_repaired"));
        return new RepairConfig(true, materials, economy, disabledDisplay, onDisabled, onRepaired);
    }

    private List<ItemSourceRef> parseRepairItemSources(Map<?, ?> entry) {
        Object rawSources = ConfigNodes.get(entry, "item_sources");
        if (rawSources == null) {
            rawSources = ConfigNodes.get(entry, "item_source");
        }
        if (rawSources == null) {
            rawSources = ConfigNodes.get(entry, "item");
        }
        List<ItemSourceRef> result = new ArrayList<>();
        for (Object rawSource : ConfigNodes.asObjectList(rawSources)) {
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

    private Map<String, Object> parseVariables(YamlSection root, List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        mergePlainMap(result, root.get("variables"));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"variables".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "variables"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Map<String, Object> parseAttributes(YamlSection root, List<Map<?, ?>> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        mergePlainMap(result, root.get("ea_attributes"));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"ea_attribute".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergePlainMap(result, ConfigNodes.get(effect, "ea_attributes"));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<String> parseSkills(YamlSection root, List<Map<?, ?>> effects) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(normalizedList(root.get("es_skills")));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            result.addAll(normalizedList(ConfigNodes.get(effect, "es_skills")));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private Map<String, String> parseSkillTriggers(YamlSection root, List<Map<?, ?>> effects) {
        Map<String, String> result = new LinkedHashMap<>();
        mergeSkillTriggers(result, root.get("es_skill_triggers"));
        mergeSkillTriggers(result, root.get("skill_triggers"));
        for (Map<?, ?> effect : effects == null ? List.<Map<?, ?>>of() : effects) {
            if (effect == null || !"es_skill".equals(Texts.normalizeId(Texts.toStringSafe(ConfigNodes.get(effect, "type"))))) {
                continue;
            }
            mergeSkillTriggers(result, ConfigNodes.get(effect, "es_skill_triggers"));
            mergeSkillTriggers(result, ConfigNodes.get(effect, "skill_triggers"));
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

    private Object parseDisplayActions(YamlSection root, List<Map<?, ?>> effects, String effectType, String topKey, String effectKey) {
        List<Object> actions = new ArrayList<>();
        appendDisplayActions(actions, root == null ? null : root.get(topKey));
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

    private void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}

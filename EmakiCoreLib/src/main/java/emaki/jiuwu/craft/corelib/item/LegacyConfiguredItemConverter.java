package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;


public final class LegacyConfiguredItemConverter {

    private final ConfiguredItemParser parser;

    public LegacyConfiguredItemConverter(ConfiguredItemParser parser) {
        this.parser = parser == null ? new ConfiguredItemParser() : parser;
    }

    public ConfiguredItemDefinition convert(String source, Object raw) {
        return convert(source, 1, raw, Map.of());
    }

    public ConfiguredItemDefinition convert(String source,
            int amount,
            Object raw,
            Map<String, ?> replacements) {
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        copyGenericComponents(raw, patches);

        Object displayName = legacyValue(raw, "display_name");
        if (displayName != null) {
            String resolved = displayName instanceof Map<?, ?> || displayName instanceof YamlSection
                    ? ExpressionEngine.evaluateStringConfig(displayName, safeReplacements)
                    : Texts.formatTemplate(Texts.toStringSafe(displayName), safeReplacements);
            patches.put("minecraft:custom_name", ItemComponentPatch.set(resolved));
        }

        if (legacyContains(raw, "lore")) {
            Object lore = legacyValue(raw, "lore");
            Object resolvedLore = lore instanceof Map<?, ?> || lore instanceof YamlSection
                    ? ExpressionEngine.evaluateStringLinesConfig(lore, safeReplacements)
                    : replacePlain(ConfigNodes.toPlainData(lore), safeReplacements);
            patches.put("minecraft:lore", ItemComponentPatch.set(resolvedLore == null ? List.of() : resolvedLore));
        }

        String itemModel = legacyString(raw, "item_model", legacyString(raw, "item-model", null));
        if (Texts.isNotBlank(itemModel)) {
            patches.put("minecraft:item_model", ItemComponentPatch.set(Texts.formatTemplate(itemModel, safeReplacements)));
        } else {
            Object customModelDataRaw = legacyContains(raw, "custom_model_data")
                    ? legacyValue(raw, "custom_model_data")
                    : legacyValue(raw, "custommodeldata");
            Integer customModelData = Numbers.tryParseInt(customModelDataRaw, null);
            if (customModelData != null) {
                patches.put("minecraft:custom_model_data", ItemComponentPatch.set(Map.of(
                        "floats", List.of(customModelData.floatValue())
                )));
            }
        }

        Map<String, Integer> enchantments = parseEnchantments(legacyValue(raw, "enchantments"));
        if (!enchantments.isEmpty()) {
            patches.put("minecraft:enchantments", ItemComponentPatch.set(enchantments));
        }

        Object tooltipDisplayRaw = legacyValue(raw, "tooltip_display");
        Map<String, Object> tooltipDisplay = tooltipDisplayRaw instanceof Map<?, ?> || tooltipDisplayRaw instanceof YamlSection
                ? plainMap(tooltipDisplayRaw)
                : new LinkedHashMap<>();
        List<String> hiddenComponents = normalizeHiddenComponents(legacyValue(raw, "hidden_components"));
        boolean hideTooltip = legacyBoolean(raw, "hide_tooltip", false)
                || legacyBoolean(raw, "hide-tooltip", false)
                || Boolean.TRUE.equals(tooltipDisplayRaw);
        if (hiddenComponents.remove("minecraft:tooltip")) {
            hideTooltip = true;
        }
        if (!hiddenComponents.isEmpty()) {
            tooltipDisplay.put("hidden_components", hiddenComponents);
        }
        if (hideTooltip) {
            tooltipDisplay.put("hide_tooltip", true);
        }
        if (!tooltipDisplay.isEmpty()) {
            patches.put("minecraft:tooltip_display", ItemComponentPatch.set(tooltipDisplay));
        }

        return new ConfiguredItemDefinition(source, amount, patches);
    }

    private void copyGenericComponents(Object raw, Map<String, ItemComponentPatch> destination) {
        Object nested = ConfigNodes.get(raw, "components");
        if (!(nested instanceof Map<?, ?>) && !(nested instanceof YamlSection)) {
            return;
        }
        Map<String, Object> generic = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(nested).entrySet()) {
            String key = entry.getKey();
            if (key != null && !isLegacyOnlyComponentKey(key)) {
                generic.put(key, entry.getValue());
            }
        }
        if (generic.isEmpty()) {
            return;
        }
        destination.putAll(parser.parse(Map.of("components", generic)).components());
    }

    private boolean isLegacyOnlyComponentKey(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "display_name", "item-model", "custommodeldata", "hidden_components", "hide_tooltip", "hide-tooltip" -> true;
            default -> false;
        };
    }

    private Object legacyValue(Object raw, String key) {
        if (ConfigNodes.contains(raw, key)) {
            return ConfigNodes.get(raw, key);
        }
        Object nested = ConfigNodes.get(raw, "components");
        return ConfigNodes.get(nested, key);
    }

    private boolean legacyContains(Object raw, String key) {
        if (ConfigNodes.contains(raw, key)) {
            return true;
        }
        return ConfigNodes.contains(ConfigNodes.get(raw, "components"), key);
    }

    private String legacyString(Object raw, String key, String fallback) {
        Object value = legacyValue(raw, key);
        return value == null ? fallback : Texts.toStringSafe(value);
    }

    private boolean legacyBoolean(Object raw, String key, boolean fallback) {
        Object value = legacyValue(raw, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private Object replacePlain(Object value, Map<String, ?> replacements) {
        if (value instanceof String text) {
            return Texts.formatTemplate(text, replacements);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), replacePlain(entry.getValue(), replacements));
                }
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object entry : collection) {
                result.add(replacePlain(entry, replacements));
            }
            return result;
        }
        return value;
    }

    private Map<String, Integer> parseEnchantments(Object raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Integer level = Numbers.tryParseInt(entry.getValue(), null);
                if (entry.getKey() != null && level != null && level > 0) {
                    result.put(normalizeResourceId(String.valueOf(entry.getKey())), level);
                }
            }
            return result;
        }
        for (String text : Texts.asStringList(raw)) {
            if (Texts.isBlank(text)) {
                continue;
            }
            String id = text.trim();
            int level = 1;
            int separator = id.lastIndexOf(':');
            if (separator > 0) {
                Integer parsedLevel = Numbers.tryParseInt(id.substring(separator + 1), null);
                if (parsedLevel != null) {
                    level = parsedLevel;
                    id = id.substring(0, separator);
                }
            }
            if (level > 0) {
                result.put(normalizeResourceId(id), level);
            }
        }
        return result;
    }

    private List<String> normalizeHiddenComponents(Object raw) {
        List<String> result = new ArrayList<>();
        for (String entry : Texts.asStringList(raw)) {
            if (Texts.isBlank(entry)) {
                continue;
            }
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if ("tooltip".equals(normalized) || "*".equals(normalized)) {
                result.add("minecraft:tooltip");
            } else {
                result.add(normalizeResourceId(normalized));
            }
        }
        return result;
    }

    private String normalizeResourceId(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainMap(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return new LinkedHashMap<>();
    }
}

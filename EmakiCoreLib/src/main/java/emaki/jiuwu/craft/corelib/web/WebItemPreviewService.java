package emaki.jiuwu.craft.corelib.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.assembly.LoreOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.NameOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

final class WebItemPreviewService {

    private static final Pattern MINECRAFT_SOURCE = Pattern.compile("^(?:minecraft[-:])?([a-z0-9_]+)$", Pattern.CASE_INSENSITIVE);

    private final OperationTemplateRenderer templateRenderer = new OperationTemplateRenderer();
    private final NameOperationRegistry nameOperations = new NameOperationRegistry(templateRenderer);
    private final LoreOperationRegistry loreOperations = new LoreOperationRegistry(templateRenderer);

    Map<String, Object> actionTypes() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nameActions", List.copyOf(nameOperations.registeredActions()));
        result.put("loreActions", List.copyOf(loreOperations.registeredActions()));
        return result;
    }

    Map<String, Object> preview(String content, int previewLevel, String baseName, List<String> baseLore) {
        YamlSection yaml = YamlFiles.load(content == null ? "" : content);
        Map<String, Object> data = ConfigNodes.entries(yaml);
        String kind = inferKind(data);
        return switch (kind) {
            case "gem" -> previewGem(data, previewLevel, baseName, baseLore);
            case "gem_socket_item" -> previewGemSocketItem(data, baseName, baseLore);
            default -> previewGenericItem(data, baseName, baseLore);
        };
    }

    private Map<String, Object> previewGem(Map<String, Object> data, int previewLevel, String baseName, List<String> baseLore) {
        int level = Math.max(1, previewLevel);
        Map<String, Object> levelData = gemLevelData(data, level);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", Texts.toStringSafe(data.get("id")));
        variables.put("level", level);
        variables.put("current_level", level);
        variables.put("target_level", level);
        variables.put("display_name", firstText(levelData.get("display_name"), data.get("display_name"), data.get("id")));
        variables.putAll(resolveVariables(extractVariables(data, levelData), variables));

        List<Map<String, Object>> effectSummary = summarizeEffects(data, levelData, variables);
        Object nameActions = firstNonNull(levelData.get("name_actions"), data.get("name_actions"), effectsPayload(data, levelData, "name_action", "name_actions"));
        Object loreActions = firstNonNull(levelData.get("lore_actions"), data.get("lore_actions"), effectsPayload(data, levelData, "lore_action", "lore_actions"));
        String initialName = Texts.isBlank(baseName) ? Texts.toStringSafe(variables.get("display_name")) : baseName;
        List<String> initialLore = baseLore == null || baseLore.isEmpty()
                ? ConfigNodes.asObjectList(data.get("lore")).stream().map(Texts::toStringSafe).toList()
                : List.copyOf(baseLore);
        PreviewText previewText = applyOperations(initialName, initialLore, nameActions, loreActions, variables);

        Map<String, Object> result = baseResult("gem", data, previewText, variables);
        result.put("material", materialFromItemSources(data.get("item_sources")));
        result.put("level", level);
        result.put("levels", gemLevels(data));
        result.put("gemType", Texts.toStringSafe(data.get("gem_type")));
        result.put("socketCompatibility", ConfigNodes.asObjectList(data.get("socket_compatibility")));
        result.put("effects", effectSummary);
        result.put("upgrade", summarizeUpgrade(data));
        result.put("costs", Map.of(
                "inlay", summarizeCost(data.get("inlay_cost"), variables),
                "extract", summarizeCost(data.get("extract_cost"), variables)
        ));
        result.put("extractReturn", ConfigNodes.toPlainData(data.get("extract_return")));
        return result;
    }

    private Map<String, Object> previewGemSocketItem(Map<String, Object> data, String baseName, List<String> baseLore) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("item_definition_id", Texts.toStringSafe(data.get("id")));
        variables.put("opened_slots", ConfigNodes.asObjectList(data.get("default_open_slots")).size());
        variables.put("total_slots", ConfigNodes.asObjectList(data.get("slots")).size());
        variables.put("slot_icons", slotIcons(data));
        String initialName = Texts.isBlank(baseName) ? "<gray>预览装备</gray>" : baseName;
        List<String> initialLore = baseLore == null || baseLore.isEmpty()
                ? new ArrayList<>(List.of("<gray>原始装备 Lore</gray>"))
                : List.copyOf(baseLore);
        PreviewText previewText = applyOperations(initialName, initialLore, data.get("name_actions"), data.get("lore_actions"), variables);

        Map<String, Object> result = baseResult("gem_socket_item", data, previewText, variables);
        Map<String, Object> match = ConfigNodes.entries(ConfigNodes.get(data, "match"));
        result.put("material", materialFromItemSources(match.get("item_sources")));
        result.put("match", match);
        result.put("slots", ConfigNodes.toPlainData(data.get("slots")));
        result.put("defaultOpenSlots", ConfigNodes.asObjectList(data.get("default_open_slots")));
        result.put("allowedGemTypes", ConfigNodes.asObjectList(data.get("allowed_gem_types")));
        result.put("maxSameType", data.get("max_same_type"));
        result.put("maxSameId", data.get("max_same_id"));
        result.put("gui", ConfigNodes.toPlainData(data.get("gui")));
        return result;
    }

    private Map<String, Object> previewGenericItem(Map<String, Object> data, String baseName, List<String> baseLore) {
        Map<String, Object> variables = resolveVariables(extractVariables(data, Map.of()), Map.of());
        String initialName = Texts.isBlank(baseName) ? firstText(data.get("display_name"), data.get("item_name"), data.get("id")) : baseName;
        List<String> initialLore = baseLore == null || baseLore.isEmpty()
                ? ConfigNodes.asObjectList(data.get("lore")).stream().map(Texts::toStringSafe).toList()
                : List.copyOf(baseLore);
        PreviewText previewText = applyOperations(initialName, initialLore, data.get("name_actions"), data.get("lore_actions"), variables);
        Map<String, Object> result = baseResult("generic_item", data, previewText, variables);
        result.put("material", firstText(data.get("material"), data.get("item"), "stone"));
        result.put("effects", summarizeEffects(data, Map.of(), variables));
        return result;
    }

    private Map<String, Object> baseResult(String kind, Map<String, Object> data, PreviewText previewText, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("id", Texts.toStringSafe(data.get("id")));
        result.put("displayName", previewText.name());
        result.put("lore", previewText.lore());
        result.put("variables", variables);
        result.put("nameSteps", previewText.nameSteps());
        result.put("loreSteps", previewText.loreSteps());
        return result;
    }

    private PreviewText applyOperations(String baseName, List<String> baseLore, Object nameActions, Object loreActions, Map<String, Object> variables) {
        String safeBaseName = Texts.toStringSafe(baseName);
        List<String> safeLore = new ArrayList<>(baseLore == null ? List.of() : baseLore);
        List<Map<String, Object>> nameSteps = new ArrayList<>();
        String currentName = safeBaseName;
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(nameActions)) {
            String action = Texts.lower(operation.get("action"));
            Object rawValue = templateRenderer.resolveOperationValue(operation);
            String value = templateRenderer.renderTemplate(rawValue, variables);
            currentName = applyNamePreviewStep(currentName, action, value, operation, variables);
            nameSteps.add(Map.of("action", action, "value", value, "result", currentName));
        }
        List<Map<String, Object>> loreSteps = new ArrayList<>();
        List<String> currentLore = new ArrayList<>(safeLore);
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(loreActions)) {
            String action = Texts.lower(operation.get("action"));
            List<String> before = List.copyOf(currentLore);
            loreOperations.apply(currentLore, List.of(operation), variables);
            loreSteps.add(Map.of(
                    "action", action,
                    "anchor", templateRenderer.renderTemplate(templateRenderer.resolveSearchPattern(operation), variables),
                    "before", before,
                    "after", List.copyOf(currentLore)
            ));
        }
        return new PreviewText(currentName, currentLore, nameSteps, loreSteps);
    }

    private String applyNamePreviewStep(String currentName, String action, String value, Map<String, Object> operation, Map<String, Object> variables) {
        return switch (Texts.lower(action)) {
            case "replace" -> Texts.toStringSafe(value);
            case "prepend_prefix" -> Texts.toStringSafe(value) + Texts.toStringSafe(currentName);
            case "append_suffix" -> Texts.toStringSafe(currentName) + Texts.toStringSafe(value);
            case "regex_replace" -> OperationTemplateRenderer.replaceRegex(
                    Texts.toStringSafe(currentName),
                    Texts.toStringSafe(operation.get("regex_pattern")),
                    Texts.toStringSafe(operation.get("replacement")),
                    variables
            );
            default -> Texts.toStringSafe(currentName);
        };
    }

    private Map<String, Object> extractVariables(Map<String, Object> baseData, Map<String, Object> levelData) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.putAll(ConfigNodes.entries(baseData.get("variables")));
        raw.putAll(effectMap(baseData, "variables", "variables"));
        raw.putAll(ConfigNodes.entries(levelData.get("variables")));
        raw.putAll(effectMap(levelData, "variables", "variables"));
        return raw;
    }

    private Map<String, Object> resolveVariables(Map<String, Object> raw, Map<String, ?> context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) evalContext.putAll(context);
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Object value = entry.getValue();
            Object resolved = value;
            if (value instanceof Number || value instanceof String || value instanceof Map<?, ?>) {
                ExpressionEngine.NumericEvaluationResult numeric = ExpressionEngine.evaluateRandomConfigDetailed(value, evalContext);
                if (numeric.success()) {
                    resolved = numeric.value();
                } else if (value instanceof String text) {
                    resolved = ExpressionEngine.evaluateString(text, evalContext);
                }
            }
            result.put(entry.getKey(), resolved);
            evalContext.put(entry.getKey(), resolved);
        }
        return result;
    }

    private List<Map<String, Object>> summarizeEffects(Map<String, Object> baseData, Map<String, Object> levelData, Map<String, ?> variables) {
        List<Map<String, Object>> result = new ArrayList<>();
        appendEffectSummary(result, baseData, variables, "base");
        appendEffectSummary(result, levelData, variables, "level");
        return result;
    }

    private void appendEffectSummary(List<Map<String, Object>> result, Map<String, Object> data, Map<String, ?> variables, String source) {
        for (Object entry : ConfigNodes.asObjectList(data.get("effects"))) {
            Map<String, Object> effect = ConfigNodes.entries(entry);
            if (effect.isEmpty()) continue;
            String type = Texts.lower(effect.get("type"));
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", type);
            summary.put("source", source);
            summary.put("payload", ConfigNodes.toPlainData(effect));
            if ("variables".equals(type)) summary.put("resolved", resolveVariables(ConfigNodes.entries(effect.get("variables")), variables));
            if ("ea_attribute".equals(type)) summary.put("attributes", ConfigNodes.toPlainData(effect.get("ea_attributes")));
            if ("es_skill".equals(type)) summary.put("skills", firstNonNull(effect.get("es_skills"), effect.get("es_skill")));
            result.add(summary);
        }
        if (data.get("ea_attributes") != null) {
            result.add(Map.of("type", "ea_attribute", "source", source, "attributes", ConfigNodes.toPlainData(data.get("ea_attributes"))));
        }
    }

    private Map<String, Object> summarizeUpgrade(Map<String, Object> data) {
        Map<String, Object> upgrade = ConfigNodes.entries(data.get("upgrade"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", upgrade.get("enabled"));
        summary.put("maxLevel", upgrade.get("max_level"));
        summary.put("guiTemplate", upgrade.get("gui_template"));
        summary.put("failurePenalty", upgrade.get("failure_penalty"));
        summary.put("successRates", ConfigNodes.toPlainData(upgrade.get("success_rates")));
        List<Map<String, Object>> levels = new ArrayList<>();
        Map<String, Object> levelMap = ConfigNodes.entries(upgrade.get("levels"));
        for (Map.Entry<String, Object> entry : levelMap.entrySet()) {
            Map<String, Object> value = ConfigNodes.entries(entry.getValue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", entry.getKey());
            row.put("displayName", value.get("display_name"));
            row.put("successRate", firstNonNull(value.get("success_rate"), value.get("success_chance"), ConfigNodes.get(upgrade.get("success_rates"), entry.getKey())));
            row.put("failurePenalty", firstText(value.get("failure_penalty"), upgrade.get("failure_penalty"), "none"));
            row.put("materials", ConfigNodes.toPlainData(value.get("materials")));
            row.put("economy", ConfigNodes.toPlainData(firstNonNull(value.get("economy"), upgrade.get("economy"))));
            row.put("effects", summarizeEffects(Map.of(), value, Map.of("target_level", entry.getKey())));
            row.put("actions", ConfigNodes.toPlainData(value.get("actions")));
            levels.add(row);
        }
        summary.put("levels", levels);
        return summary;
    }

    private Object summarizeCost(Object cost, Map<String, Object> variables) {
        Map<String, Object> source = ConfigNodes.entries(cost);
        if (source.isEmpty()) return Map.of("currencies", List.of(), "materials", List.of());
        List<Map<String, Object>> currencies = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(source.get("currencies"))) {
            Map<String, Object> row = ConfigNodes.entries(raw);
            Map<String, Object> copy = new LinkedHashMap<>(row);
            Object amount = firstNonNull(row.get("amount"), row.get("base_cost"), row.get("cost_formula"));
            ExpressionEngine.NumericEvaluationResult resolved = ExpressionEngine.evaluateRandomConfigDetailed(amount, variables);
            copy.put("resolved_amount", resolved.success() ? resolved.value() : amount);
            currencies.add(copy);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currencies", currencies);
        result.put("materials", ConfigNodes.toPlainData(source.get("materials")) == null ? List.of() : ConfigNodes.toPlainData(source.get("materials")));
        return result;
    }

    private List<Integer> gemLevels(Map<String, Object> data) {
        List<Integer> levels = new ArrayList<>();
        levels.add(Math.max(1, number(data.get("level"), 1)));
        Map<String, Object> levelMap = ConfigNodes.entries(ConfigNodes.get(data.get("upgrade"), "levels"));
        for (String key : levelMap.keySet()) {
            try {
                levels.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return levels.stream().distinct().sorted().toList();
    }

    private Map<String, Object> gemLevelData(Map<String, Object> data, int level) {
        Object levels = ConfigNodes.get(data.get("upgrade"), "levels");
        return ConfigNodes.entries(ConfigNodes.get(levels, Integer.toString(level)));
    }

    private Object effectsPayload(Map<String, Object> baseData, Map<String, Object> levelData, String type, String key) {
        Object level = firstEffectPayload(levelData, type, key);
        return level == null ? firstEffectPayload(baseData, type, key) : level;
    }

    private Object firstEffectPayload(Map<String, Object> data, String type, String key) {
        for (Object entry : ConfigNodes.asObjectList(data.get("effects"))) {
            Map<String, Object> effect = ConfigNodes.entries(entry);
            if (type.equals(Texts.lower(effect.get("type")))) {
                return effect.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> effectMap(Map<String, Object> data, String type, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object entry : ConfigNodes.asObjectList(data.get("effects"))) {
            Map<String, Object> effect = ConfigNodes.entries(entry);
            if (type.equals(Texts.lower(effect.get("type")))) {
                result.putAll(ConfigNodes.entries(effect.get(key)));
            }
        }
        return result;
    }

    private String slotIcons(Map<String, Object> data) {
        List<Object> slots = ConfigNodes.asObjectList(data.get("slots"));
        List<Object> open = ConfigNodes.asObjectList(data.get("default_open_slots"));
        StringBuilder builder = new StringBuilder();
        for (Object raw : slots) {
            Object index = ConfigNodes.get(raw, "index");
            builder.append(open.stream().anyMatch(value -> Texts.toStringSafe(value).equals(Texts.toStringSafe(index))) ? '◆' : '◇');
        }
        return builder.toString();
    }

    private String materialFromItemSources(Object raw) {
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            String text = Texts.toStringSafe(entry).trim();
            Matcher matcher = MINECRAFT_SOURCE.matcher(text);
            if (matcher.matches()) return matcher.group(1);
        }
        return "stone";
    }

    private String inferKind(Map<String, Object> data) {
        if (data.containsKey("gem_type") || data.containsKey("socket_compatibility") || data.containsKey("inlay_cost")) {
            return "gem";
        }
        if (data.containsKey("slots") && data.containsKey("default_open_slots") && data.containsKey("allowed_gem_types")) {
            return "gem_socket_item";
        }
        return "generic_item";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (Texts.isNotBlank(value)) return Texts.toStringSafe(value);
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private int number(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record PreviewText(String name, List<String> lore, List<Map<String, Object>> nameSteps, List<Map<String, Object>> loreSteps) {}
}

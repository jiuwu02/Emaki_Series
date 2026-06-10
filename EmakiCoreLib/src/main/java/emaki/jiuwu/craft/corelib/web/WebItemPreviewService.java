package emaki.jiuwu.craft.corelib.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.LocalNameState;
import emaki.jiuwu.craft.corelib.assembly.LoreOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.NameOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlLoadException;
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
        Map<String, Object> data;
        try {
            YamlSection yaml = YamlFiles.load(content == null ? "" : content);
            data = ConfigNodes.entries(yaml);
        } catch (YamlLoadException exception) {
            throw ItemPreviewException.yaml(exception);
        } catch (RuntimeException exception) {
            throw ItemPreviewException.yaml(exception);
        }
        String kind = inferKind(data);
        return switch (kind) {
            case "gem" -> previewGem(data, previewLevel, baseName, baseLore);
            case "gem_socket_item" -> previewGemSocketItem(data, baseName, baseLore);
            default -> previewGenericItem(data, baseName, baseLore);
        };
    }

    private Map<String, Object> previewGem(Map<String, Object> data, int previewLevel, String baseName, List<String> baseLore) {
        int level = previewGemLevel(data, previewLevel);
        Map<String, Object> levelData = gemLevelData(data, level);
        Map<String, Object> effectiveData = effectiveGemData(data, levelData);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", Texts.toStringSafe(data.get("id")));
        variables.put("level", level);
        variables.put("current_level", level);
        variables.put("target_level", level);
        variables.put("display_name", firstText(levelData.get("display_name"), data.get("display_name"), data.get("id")));
        variables.putAll(resolveVariables(extractVariables(effectiveData), variables));

        List<Map<String, Object>> effectSummary = summarizeEffects(effectiveData, variables, effectiveData == levelData ? "level" : "base");
        Object nameActions = sectionActions(effectiveData, "name_action", "name_actions", "name_action");
        Object loreActions = sectionActions(effectiveData, "lore_action", "lore_actions", "lore_action");
        String initialName = Texts.isBlank(baseName) ? Texts.toStringSafe(variables.get("display_name")) : baseName;
        List<String> initialLore = baseLore == null || baseLore.isEmpty()
                ? stringLines(data.get("lore"), "lore")
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
        Map<String, Object> obtain = ConfigNodes.entries(data.get("obtain"));
        Object nameActions = firstNonNull(sectionActions(data, "name_action", "name_actions", "name_action"), obtain.get("name_actions"));
        Object loreActions = firstNonNull(sectionActions(data, "lore_action", "lore_actions", "lore_action"), obtain.get("lore_actions"));
        PreviewText previewText = applyOperations(initialName, initialLore, nameActions, loreActions, variables);

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
        Map<String, Object> variables = resolveVariables(extractVariables(data), Map.of());
        String configuredName = firstText(data.get("display_name"), data.get("item_name"), data.get("id"));
        String initialName = renderPreviewText(Texts.isBlank(configuredName) ? baseName : configuredName, variables);
        List<String> configuredLore = stringLines(data.get("lore"), "lore");
        List<String> initialLore = configuredLore.isEmpty()
                ? renderPreviewLines(baseLore, variables)
                : renderPreviewLines(configuredLore, variables);
        PreviewText previewText = applyOperations(initialName, initialLore,
                sectionActions(data, "name_action", "name_actions", "name_action"),
                sectionActions(data, "lore_action", "lore_actions", "lore_action"),
                variables);
        Map<String, Object> result = baseResult("generic_item", data, previewText, variables);
        result.put("material", firstText(data.get("material"), data.get("item"), "stone"));
        result.put("effects", summarizeEffects(data, variables, "base"));
        return result;
    }

    private Map<String, Object> baseResult(String kind, Map<String, Object> data, PreviewText previewText, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("id", Texts.toStringSafe(data.get("id")));
        result.put("baseName", previewText.baseName());
        result.put("baseLore", previewText.baseLore());
        result.put("displayName", previewText.name());
        result.put("lore", previewText.lore());
        result.put("variables", variables);
        result.put("nameSteps", previewText.nameSteps());
        result.put("loreSteps", previewText.loreSteps());
        return result;
    }

    private PreviewText applyOperations(String baseName, List<String> baseLore, Object nameActions, Object loreActions, Map<String, Object> variables) {
        validateLoreActionContent(loreActions);
        String safeBaseName = Texts.toStringSafe(baseName);
        List<String> safeLore = new ArrayList<>(baseLore == null ? List.of() : baseLore);
        List<Map<String, Object>> nameSteps = new ArrayList<>();
        LocalNameState nameState = new LocalNameState();
        String currentName = safeBaseName;
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(nameActions)) {
            String action = Texts.lower(operation.get("action"));
            Object rawValue = templateRenderer.resolveOperationValue(operation);
            String value = templateRenderer.renderTemplate(rawValue, variables);
            String before = currentName;
            nameOperations.apply(nameState, List.of(operation), variables);
            currentName = previewNameFromState(nameState, safeBaseName);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("action", action);
            step.put("value", value);
            step.put("before", before);
            step.put("after", currentName);
            step.put("result", currentName);
            nameSteps.add(step);
        }
        List<Map<String, Object>> loreSteps = new ArrayList<>();
        List<String> currentLore = new ArrayList<>(safeLore);
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(loreActions)) {
            String action = Texts.lower(operation.get("action"));
            List<String> before = List.copyOf(currentLore);
            List<String> content = templateRenderer.renderContent(operation, variables);
            loreOperations.apply(currentLore, List.of(operation), variables);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("action", action);
            step.put("anchor", templateRenderer.renderTemplate(templateRenderer.resolveSearchPattern(operation), variables));
            step.put("content", content);
            step.put("before", before);
            step.put("after", List.copyOf(currentLore));
            loreSteps.add(step);
        }
        return new PreviewText(safeBaseName, List.copyOf(safeLore), currentName, currentLore, nameSteps, loreSteps);
    }

    private String previewNameFromState(LocalNameState nameState, String originalName) {
        StringBuilder finalName = new StringBuilder();
        for (String prefix : nameState.prefixes()) {
            finalName.append(prefix);
        }
        if (nameState.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE && Texts.isNotBlank(nameState.baseNameTemplate())) {
            finalName.append(nameState.baseNameTemplate());
        } else {
            finalName.append(Texts.toStringSafe(originalName));
        }
        for (String postfix : nameState.postfixes()) {
            finalName.append(postfix);
        }
        return finalName.toString();
    }

    private Map<String, Object> effectiveGemData(Map<String, Object> baseData, Map<String, Object> levelData) {
        return ConfigNodes.asObjectList(levelData.get("effects")).isEmpty() ? baseData : levelData;
    }

    private Map<String, Object> extractVariables(Map<String, Object> data) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.putAll(ConfigNodes.entries(data.get("variables")));
        raw.putAll(effectMap(data, "variables", "variables"));
        return raw;
    }

    private Map<String, Object> resolveVariables(Map<String, Object> raw, Map<String, ?> context) {
        return ExpressionEngine.resolveMixedVariables(raw, context == null ? Map.of() : context);
    }

    private List<Map<String, Object>> summarizeEffects(Map<String, Object> data, Map<String, ?> variables, String source) {
        List<Map<String, Object>> result = new ArrayList<>();
        appendEffectSummary(result, data, variables, source);
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
            result.add(summary);
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
            row.put("successRate", firstNonNull(value.get("success_rate"), ConfigNodes.get(upgrade.get("success_rates"), entry.getKey())));
            row.put("failurePenalty", firstText(value.get("failure_penalty"), upgrade.get("failure_penalty"), "none"));
            row.put("materials", ConfigNodes.toPlainData(value.get("materials")));
            row.put("economy", ConfigNodes.toPlainData(firstNonNull(value.get("economy"), upgrade.get("economy"))));
            row.put("effects", summarizeEffects(value, Map.of("target_level", entry.getKey()), "level"));
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
        Map<String, Object> upgrade = ConfigNodes.entries(data.get("upgrade"));
        if (!truthy(upgrade.get("enabled"))) {
            return List.of();
        }
        int maxLevel = configuredMaxLevel(data, upgrade);
        List<Integer> levels = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            levels.add(level);
        }
        return levels;
    }

    private int previewGemLevel(Map<String, Object> data, int requestedLevel) {
        int baseLevel = Math.max(1, number(data.get("level"), 1));
        Map<String, Object> upgrade = ConfigNodes.entries(data.get("upgrade"));
        if (!truthy(upgrade.get("enabled"))) {
            return baseLevel;
        }
        int maxLevel = configuredMaxLevel(data, upgrade);
        return Math.max(1, Math.min(maxLevel, requestedLevel));
    }

    private int configuredMaxLevel(Map<String, Object> data, Map<String, Object> upgrade) {
        int baseLevel = Math.max(1, number(data.get("level"), 1));
        int maxLevel = number(upgrade.get("max_level"), 0);
        if (maxLevel <= 0) {
            for (String key : ConfigNodes.entries(upgrade.get("levels")).keySet()) {
                maxLevel = Math.max(maxLevel, number(key, 0));
            }
        }
        return Math.max(baseLevel, maxLevel);
    }

    private Map<String, Object> gemLevelData(Map<String, Object> data, int level) {
        Object levels = ConfigNodes.get(data.get("upgrade"), "levels");
        return ConfigNodes.entries(ConfigNodes.get(levels, Integer.toString(level)));
    }

    private Object sectionActions(Map<String, Object> data, String type, String topKey, String effectKey) {
        List<Object> actions = new ArrayList<>();
        appendActions(actions, data.get(topKey));
        for (Object entry : ConfigNodes.asObjectList(data.get("effects"))) {
            Map<String, Object> effect = ConfigNodes.entries(entry);
            if (type.equals(Texts.lower(effect.get("type")))) {
                appendActions(actions, effect.get(topKey));
                appendActions(actions, effect.get(effectKey));
            }
        }
        return actions.isEmpty() ? null : List.copyOf(actions);
    }

    private void appendActions(List<Object> actions, Object raw) {
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

    private void validateLoreActionContent(Object loreActions) {
        int operationIndex = 1;
        for (Map<String, Object> operation : templateRenderer.normalizeOperations(loreActions)) {
            if (operation.containsKey("content")) {
                stringLines(operation.get("content"), "lore_actions 第 " + operationIndex + " 项的 content");
            }
            operationIndex++;
        }
    }

    private static List<String> stringLines(Object raw, String label) {
        if (raw == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int line = 1;
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry instanceof String text) {
                result.add(text);
            } else {
                throw ItemPreviewException.loreType(label, line, entry);
            }
            line++;
        }
        return List.copyOf(result);
    }

    private String renderPreviewText(Object raw, Map<String, Object> variables) {
        return ExpressionEngine.evaluateStringConfig(raw, variables == null ? Map.of() : variables);
    }

    private List<String> renderPreviewLines(List<String> rawLines, Map<String, Object> variables) {
        if (rawLines == null || rawLines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String line : rawLines) {
            result.add(renderPreviewText(line, variables));
        }
        return List.copyOf(result);
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

    private boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = Texts.toStringSafe(value).trim();
        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text);
    }

    static final class ItemPreviewException extends RuntimeException {
        private final String errorType;
        private final String technicalDetails;

        private ItemPreviewException(String errorType, String message, String technicalDetails, Throwable cause) {
            super(message, cause);
            this.errorType = errorType;
            this.technicalDetails = technicalDetails == null ? "" : technicalDetails;
        }

        static ItemPreviewException yaml(Throwable cause) {
            return new ItemPreviewException(
                    "yaml_parse_error",
                    "物品预览失败：配置格式可能有误，请检查 name 或 lore 中的引号、冒号和 MiniMessage 标签。",
                    safeMessage(cause),
                    cause
            );
        }

        static ItemPreviewException loreType(String label, int line, Object raw) {
            String location = label == null || label.isBlank() ? "lore" : label;
            String rawType = raw == null ? "null" : raw.getClass().getSimpleName();
            String rawValue = Texts.toStringSafe(ConfigNodes.toPlainData(raw));
            return new ItemPreviewException(
                    "lore_type_error",
                    "物品预览失败：" + location + " 第 " + line + " 行不是文本。请用引号包裹包含冒号、引号或 MiniMessage 标签的内容。",
                    "Expected string at " + location + " line " + line + ", got " + rawType + ": " + rawValue,
                    null
            );
        }

        String errorType() {
            return errorType;
        }

        String technicalDetails() {
            return technicalDetails;
        }

        private static String safeMessage(Throwable throwable) {
            if (throwable == null) {
                return "unknown error";
            }
            String message = throwable.getMessage();
            if (Texts.isNotBlank(message)) {
                return message;
            }
            Throwable cause = throwable.getCause();
            if (cause != null && Texts.isNotBlank(cause.getMessage())) {
                return cause.getMessage();
            }
            return throwable.getClass().getSimpleName();
        }
    }

    private record PreviewText(String baseName,
            List<String> baseLore,
            String name,
            List<String> lore,
            List<Map<String, Object>> nameSteps,
            List<Map<String, Object>> loreSteps) {}
}

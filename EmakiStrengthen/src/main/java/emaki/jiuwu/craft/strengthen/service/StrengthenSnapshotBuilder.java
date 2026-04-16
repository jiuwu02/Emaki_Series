package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiPresentationEntry;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertValidationException;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenSnapshotBuilder {

    public EmakiItemLayerSnapshot buildLayerSnapshot(StrengthenRecipe recipe,
            StrengthenState state,
            String materialsSignature) {
        if (recipe == null || state == null) {
            return null;
        }
        Map<String, Double> stats = recipe.cumulativeStats(state.currentStar());
        return new EmakiItemLayerSnapshot(
                "strengthen",
                1,
                buildAudit(recipe, state, materialsSignature),
                buildStatContributions(stats),
                buildPresentation(recipe, state, stats)
        );
    }

    private Map<String, Object> buildAudit(StrengthenRecipe recipe, StrengthenState state, String materialsSignature) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe.id());
        audit.put("current_star", state.currentStar());
        audit.put("crack_level", state.crackLevel());
        audit.put("temper", state.temperLevel());
        audit.put("star", state.currentStar());
        audit.put("max_temper", recipe.limits().maxTemper());
        audit.put("success_count", state.successCount());
        audit.put("failure_count", state.failureCount());
        audit.put("first_reach_flags", new ArrayList<>(state.firstReachFlags()));
        audit.put("last_attempt_at", state.lastAttemptAt());
        audit.put("materials_signature", Texts.toStringSafe(materialsSignature));
        audit.put("base_source_signature", state.baseSourceSignature());
        return audit;
    }

    private List<EmakiStatContribution> buildStatContributions(Map<String, Double> stats) {
        List<EmakiStatContribution> result = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            result.add(new EmakiStatContribution(entry.getKey(), entry.getValue(), "strengthen:" + entry.getKey(), sequence++));
        }
        return result;
    }

    private List<EmakiPresentationEntry> buildPresentation(StrengthenRecipe recipe,
            StrengthenState state,
            Map<String, Double> stats) {
        Map<String, Object> variables = buildVariables(recipe, state, stats);
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        int sequence = 0;

        List<String> prependLines = new ArrayList<>();
        prependLines.addAll(recipe.presentation().lorePrepend());
        for (StrengthenRecipe.StarStage stage : recipe.reachedStages(state.currentStar())) {
            if (Texts.isNotBlank(stage.name())) {
                prependLines.add("<gold>星级效果: " + stage.name() + "</gold>");
            }
            prependLines.addAll(stage.presentation());
        }
        for (int index = prependLines.size() - 1; index >= 0; index--) {
            entries.add(new EmakiPresentationEntry(
                    "lore_prepend",
                    "",
                    renderTemplate(prependLines.get(index), variables),
                    sequence++,
                    "strengthen"
            ));
        }

        List<StrengthenRecipe.PresentationOperation> nameOperations = new ArrayList<>(recipe.presentation().nameOperations());
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            String statId = Texts.lower(entry.getKey());
            StrengthenRecipe.StatLineDefinition definition = recipe.statLines().get(statId);
            String renderedLine = renderStatLine(definition, statId, entry.getValue(), variables);
            if (definition == null || definition.loreOperations().isEmpty()) {
                entries.add(new EmakiPresentationEntry("lore_append", "", renderedLine, sequence++, "strengthen"));
            } else {
                for (StrengthenRecipe.PresentationOperation operation : definition.loreOperations()) {
                    sequence = appendLoreOperation(entries, operation, renderedLine, sequence, statId);
                }
            }
            if (definition != null && !definition.nameOperations().isEmpty()) {
                nameOperations.addAll(definition.nameOperations());
            }
        }

        for (StrengthenRecipe.PresentationOperation operation : nameOperations) {
            sequence = appendNameOperation(entries, operation, variables, state, sequence);
        }
        return entries;
    }

    private int appendLoreOperation(List<EmakiPresentationEntry> entries,
            StrengthenRecipe.PresentationOperation operation,
            String content,
            int sequence,
            String sourceId) {
        if (operation == null) {
            return sequence;
        }
        String type = Texts.lower(operation.type());
        String anchor = operation.string("anchor");
        String pattern = Texts.isNotBlank(operation.string("pattern")) ? operation.string("pattern") : operation.string("regex_pattern");
        return switch (type) {
            case "lore_search_insert" -> appendSearchInsert(entries, operation, content, sequence, sourceId);
            case "lore_insert_below" ->
                addLoreEntry(entries, "lore_insert_below", anchor, content, sequence, sourceId);
            case "lore_insert_above" ->
                addLoreEntry(entries, "lore_insert_above", anchor, content, sequence, sourceId);
            case "lore_prepend" ->
                addLoreEntry(entries, "lore_prepend", "", content, sequence, sourceId);
            case "lore_replace_line" ->
                addLoreEntry(entries, "lore_replace_line", pattern, content, sequence, sourceId);
            case "lore_regex_replace" ->
                addLoreEntry(entries, "lore_regex_replace", pattern, content, sequence, sourceId);
            default ->
                addLoreEntry(entries, "lore_append", "", content, sequence, sourceId);
        };
    }

    private int appendSearchInsert(List<EmakiPresentationEntry> entries,
            StrengthenRecipe.PresentationOperation operation,
            String content,
            int sequence,
            String sourceId) {
        String anchor = operation.string("anchor");
        String position = Texts.lower(operation.string("position"));
        String action = "above".equals(position) ? "search_insert_above" : "search_insert_below";
        try {
            SearchInsertConfig config = SearchInsertConfig.fromAction(
                    action,
                    anchor,
                    operation.bool("ignore_case", false),
                    List.of(content),
                    operation.bool("inherit_style", true),
                    operation.string("on_not_found")
            );
            entries.add(new EmakiPresentationEntry("lore_search_insert", config.toJson(), "", sequence, sourceId));
            return sequence + 1;
        } catch (SearchInsertValidationException ignored) {
            entries.add(new EmakiPresentationEntry("lore_append", "", content, sequence, sourceId));
            return sequence + 1;
        }
    }

    private int addLoreEntry(List<EmakiPresentationEntry> entries,
            String type,
            String searchPattern,
            String content,
            int sequence,
            String sourceId) {
        entries.add(new EmakiPresentationEntry(type, Texts.toStringSafe(searchPattern), content, sequence, sourceId));
        return sequence + 1;
    }

    private int appendNameOperation(List<EmakiPresentationEntry> entries,
            StrengthenRecipe.PresentationOperation operation,
            Map<String, Object> variables,
            StrengthenState state,
            int sequence) {
        if (operation == null || !conditionMatches(operation.string("condition"), state)) {
            return sequence;
        }
        String type = Texts.lower(operation.type());
        String renderedValue = renderTemplate(resolveOperationValue(operation), variables);
        String entryType = switch (type) {
            case "name_regex_replace" -> "name_regex_replace";
            case "name_prepend" -> "name_prepend";
            case "name_replace" -> "name_replace";
            default -> resolveConditionalNameType(operation.string("condition"));
        };
        entries.add(new EmakiPresentationEntry(
                entryType,
                renderTemplate(resolvePattern(operation), variables),
                renderedValue,
                sequence,
                "strengthen"
        ));
        return sequence + 1;
    }

    private String resolveConditionalNameType(String condition) {
        String normalized = Texts.lower(condition);
        if ("unchanged_from_previous".equals(normalized) || "if_unchanged".equals(normalized)) {
            return "name_append_if_unchanged";
        }
        if ("changed_from_previous".equals(normalized)) {
            return "name_append_if_changed";
        }
        return "name_append";
    }

    private boolean conditionMatches(String condition, StrengthenState state) {
        String normalized = Texts.lower(condition);
        if (Texts.isBlank(normalized)
                || "always".equals(normalized)
                || "unchanged_from_previous".equals(normalized)
                || "if_unchanged".equals(normalized)
                || "changed_from_previous".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("star_eq:")) {
            return state.currentStar() == Numbers.tryParseInt(normalized.substring("star_eq:".length()), Integer.MIN_VALUE);
        }
        if (normalized.startsWith("star_gte:")) {
            return state.currentStar() >= Numbers.tryParseInt(normalized.substring("star_gte:".length()), Integer.MAX_VALUE);
        }
        if (normalized.startsWith("star_lt:")) {
            return state.currentStar() < Numbers.tryParseInt(normalized.substring("star_lt:".length()), Integer.MIN_VALUE);
        }
        if (normalized.startsWith("milestone_reached:")) {
            int milestone = Numbers.tryParseInt(normalized.substring("milestone_reached:".length()), Integer.MIN_VALUE);
            return state.firstReachFlags().contains(milestone);
        }
        return true;
    }

    private String resolveOperationValue(StrengthenRecipe.PresentationOperation operation) {
        String value = operation.string("value");
        if (Texts.isNotBlank(value)) {
            return value;
        }
        value = operation.string("replacement");
        return Texts.isBlank(value) ? operation.string("content") : value;
    }

    private String resolvePattern(StrengthenRecipe.PresentationOperation operation) {
        String pattern = operation.string("pattern");
        return Texts.isBlank(pattern) ? operation.string("regex_pattern") : pattern;
    }

    private Map<String, Object> buildVariables(StrengthenRecipe recipe,
            StrengthenState state,
            Map<String, Double> stats) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (stats != null) {
            variables.putAll(stats);
        }
        variables.put("star", state.currentStar());
        variables.put("temper", state.temperLevel());
        variables.put("max_temper", recipe.limits().maxTemper());
        variables.put("temper_color", state.temperLevel() > 0 ? "<red>" : "<green>");
        return variables;
    }

    private String renderStatLine(StrengthenRecipe.StatLineDefinition definition,
            String statId,
            double value,
            Map<String, Object> variables) {
        Map<String, Object> renderVariables = new LinkedHashMap<>(variables);
        renderVariables.put("id", statId);
        renderVariables.put("sign", value >= 0D ? "+" : "-");
        renderVariables.put("value", Numbers.formatNumber(Math.abs(value), "0.##"));
        if (definition == null || Texts.isBlank(definition.template())) {
            return "<gray>" + humanize(statId) + ": <gold>"
                    + renderVariables.get("sign")
                    + renderVariables.get("value")
                    + "</gold></gray>";
        }
        return renderTemplate(definition.template(), renderVariables);
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        return ExpressionEngine.replaceVariables(Texts.toStringSafe(template), variables);
    }

    private String humanize(String statId) {
        String text = Texts.toStringSafe(statId).replace('_', ' ');
        return text.isBlank() ? "属性" : text;
    }
}

package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

public record SelectorDefinition(String id,
        String sourceId,
        Map<String, String> sourceArguments,
        int limit,
        ConditionGroup condition,
        boolean invalidAsFailure,
        String conditionProblem,
        Map<String, Object> conditionProblemArgs) {

    public static final String PREDICATES_FIELD = "predicates";
    public static final String CONFLICT_REASON = "action.select.condition_conflict";

    public SelectorDefinition {
        id = Texts.lower(id);
        sourceId = Texts.lower(sourceId);
        sourceArguments = sourceArguments == null ? Map.of() : Map.copyOf(sourceArguments);
        limit = Math.max(0, limit);
        condition = condition == null ? ConditionGroup.empty() : condition;
        conditionProblem = Texts.toStringSafe(conditionProblem);
        conditionProblemArgs = conditionProblemArgs == null ? Map.of() : Map.copyOf(conditionProblemArgs);
    }

    public static SelectorDefinition fromConfig(@Nullable String id, @Nullable YamlSection section) {
        if (section == null) {
            return plain(id, "", Map.of(), 0, ConditionGroup.empty(), true);
        }
        String sourceId = section.getString("source", "");
        Integer configuredLimit = section.getInt("limit", 0);
        Map<String, String> arguments = arguments(section.getSection("arguments"));
        int limit = configuredLimit == null ? 0 : configuredLimit;
        YamlSection conditionSection = section.getSection("condition");
        if (conditionSection == null) {
            return plain(id, sourceId, arguments, limit,
                    ConditionGroup.fromConfig(section.get("condition")), true);
        }
        Boolean invalidAsFailure = conditionSection.getBoolean("invalid_as_failure", true);
        boolean strict = invalidAsFailure == null || invalidAsFailure;
        String predicates = conditionSection.getString(PREDICATES_FIELD, "");
        ConditionGroup entries = ConditionGroup.fromConfig(conditionSection);
        if (Texts.isBlank(predicates)) {
            return plain(id, sourceId, arguments, limit, entries, strict);
        }
        if (!entries.emptyGroup()) {
            return new SelectorDefinition(id, sourceId, arguments, limit, entries, strict,
                    CONFLICT_REASON, Map.of("selector", Texts.toStringSafe(id), "field", PREDICATES_FIELD));
        }
        TargetPredicateParser.Result parsed = TargetPredicateParser.parse(predicates);
        if (parsed instanceof TargetPredicateParser.Result.Parsed ok) {
            return plain(id, sourceId, arguments, limit, ok.group(), strict);
        }
        TargetPredicateParser.Result.Invalid invalid = (TargetPredicateParser.Result.Invalid) parsed;
        return new SelectorDefinition(id, sourceId, arguments, limit, ConditionGroup.empty(), strict,
                invalid.reasonKey(), invalid.args());
    }

    public static List<SelectorDefinition> parseAll(@Nullable YamlSection selectorsSection) {
        if (selectorsSection == null || selectorsSection.isEmpty()) {
            return List.of();
        }
        return selectorsSection.getKeys(false).stream()
                .map(key -> fromConfig(key, selectorsSection.getSection(key)))
                .toList();
    }

    public boolean defined() {
        return !sourceId.isEmpty();
    }

    public boolean conditioned() {
        return !condition.emptyGroup();
    }

    public boolean broken() {
        return !conditionProblem.isEmpty();
    }

    private static SelectorDefinition plain(String id,
            String sourceId,
            Map<String, String> arguments,
            int limit,
            ConditionGroup condition,
            boolean invalidAsFailure) {
        return new SelectorDefinition(id, sourceId, arguments, limit, condition, invalidAsFailure, "", Map.of());
    }

    private static Map<String, String> arguments(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
                continue;
            }
            arguments.put(Texts.lower(key), String.valueOf(value));
        }
        return arguments;
    }

    public @NotNull Map<String, Object> problemArguments() {
        if (conditionProblemArgs.containsKey("selector")) {
            return conditionProblemArgs;
        }
        Map<String, Object> arguments = new LinkedHashMap<>(conditionProblemArgs);
        arguments.put("selector", id);
        return Map.copyOf(arguments);
    }
}
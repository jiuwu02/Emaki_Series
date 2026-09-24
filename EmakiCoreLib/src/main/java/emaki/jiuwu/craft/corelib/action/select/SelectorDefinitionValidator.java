package emaki.jiuwu.craft.corelib.action.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;

public final class SelectorDefinitionValidator {

    public record Finding(@NotNull String path,
            @NotNull ConfigPrecheckSeverity severity,
            @NotNull String key,
            @NotNull Map<String, Object> replacements) {
    }

    public interface Environment {

        boolean knowsSource(@Nullable String sourceId);
    }

    private static final Set<String> NUMERIC_TYPES =
            Set.of("health", "health_percent", "distance", "level", "food");

    private static final Set<String> VALUE_TYPES =
            Set.of("entity_type", "world", "gamemode", "permission", "scoreboard_tag");

    private static final Set<String> FLAG_TYPES = Set.of("player", "dead");

    private SelectorDefinitionValidator() {
    }

    public static List<Finding> validate(@Nullable List<SelectorDefinition> definitions,
            @NotNull Environment environment) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (SelectorDefinition definition : definitions) {
            validateSelector(definition, environment, findings);
        }
        return List.copyOf(findings);
    }

    private static void validateSelector(SelectorDefinition definition,
            Environment environment,
            List<Finding> findings) {
        String path = "action.selectors." + Texts.toStringSafe(definition.id());
        if (!definition.defined()) {
            findings.add(new Finding(path, ConfigPrecheckSeverity.ERROR, "selector_missing_source",
                    Map.of("selector", definition.id())));
            return;
        }
        if (ConfiguredSelectorRepository.SELECT_SOURCE_ID.equals(definition.sourceId())) {
            findings.add(new Finding(path, ConfigPrecheckSeverity.ERROR, "selector_recursive_source",
                    Map.of("selector", definition.id())));
            return;
        }
        if (!environment.knowsSource(definition.sourceId())) {
            findings.add(new Finding(path, ConfigPrecheckSeverity.WARN, "selector_unknown_source",
                    Map.of("selector", definition.id(), "source", definition.sourceId())));
        }
        if (definition.broken()) {
            findings.add(new Finding(path + ".condition", ConfigPrecheckSeverity.ERROR,
                    definition.conditionProblem(), definition.problemArguments()));
        }
        validateGroup(definition.condition(), path + ".condition", environment, findings);
    }

    private static void validateGroup(ConditionGroup group,
            String path,
            Environment environment,
            List<Finding> findings) {
        if (group == null || group.emptyGroup()) {
            return;
        }
        int index = 0;
        for (ConditionNode node : group.conditions()) {
            String nodePath = path + ".entries[" + index + "]";
            index++;
            if (node == null) {
                continue;
            }
            if (node.groupNode()) {
                validateGroup(node.group(), nodePath, environment, findings);
                continue;
            }
            validateNode(node, nodePath, environment, findings);
        }
    }

    private static void validateNode(ConditionNode node,
            String path,
            Environment environment,
            List<Finding> findings) {
        String type = Texts.lower(node.type());
        if (Texts.isNotBlank(node.expression()) || "expression".equals(type)) {
            return;
        }
        if (NUMERIC_TYPES.contains(type)) {
            requireField(node, "value", path, type, findings);
            requireField(node, "op", path, type, findings);
            return;
        }
        if (VALUE_TYPES.contains(type) || FLAG_TYPES.contains(type)) {
            requireField(node, "value", path, type, findings);
            return;
        }
        if ("state".equals(type)) {
            String state = Texts.lower(Texts.toStringSafe(node.data().get("key")));
            if (state.isEmpty() || !TargetFactsReader.knownStates().contains(state)) {
                findings.add(new Finding(path, ConfigPrecheckSeverity.WARN, "selector_condition_invalid_state",
                        Map.of("state", Texts.toStringSafe(node.data().get("key")),
                                "states", String.join(", ", TargetFactsReader.knownStates()))));
            }
            return;
        }
        if ("potion_effect".equals(type)) {
            requireField(node, "effect", path, type, findings);
        }
    }

    private static void requireField(ConditionNode node,
            String field,
            String path,
            String type,
            List<Finding> findings) {
        Object value = node.data().get(field);
        if (value != null && Texts.isNotBlank(String.valueOf(value))) {
            return;
        }
        findings.add(new Finding(path, ConfigPrecheckSeverity.WARN, "selector_condition_missing_field",
                Map.of("type", type, "field", field)));
    }
}
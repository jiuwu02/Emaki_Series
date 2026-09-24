package emaki.jiuwu.craft.corelib.action.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;

@DisplayName("选择器配置解析与预检")
class SelectorDefinitionTest {

    @Test
    @DisplayName("完整定义解析源、参数、上限与条件组")
    void parsesFullDefinition() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("elite_zombies", new MapYamlSection(raw(
                "source", "nearby",
                "arguments", Map.of("radius", 16, "limit", 5, "include_players", false),
                "limit", 2,
                "condition", Map.of(
                        "type", "all_of",
                        "invalid_as_failure", false,
                        "entries", List.of(Map.of("type", "entity_type", "value", "ZOMBIE"))))));

        assertEquals("elite_zombies", definition.id());
        assertEquals("nearby", definition.sourceId());
        assertEquals("16", definition.sourceArguments().get("radius"));
        assertEquals("false", definition.sourceArguments().get("include_players"));
        assertEquals(2, definition.limit());
        assertFalse(definition.invalidAsFailure());
        assertEquals("all_of", definition.condition().conditionType());
        assertEquals(1, definition.condition().conditions().size());
        assertTrue(definition.defined());
        assertTrue(definition.conditioned());
    }

    @Test
    @DisplayName("缺 source 的定义仍进入模型，但标记为未定义")
    void missingSourceIsUndefined() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("broken", new MapYamlSection(raw(
                "limit", 3)));
        assertFalse(definition.defined());
        assertNull(definition.sourceArguments().get("radius"));
    }

    @Test
    @DisplayName("非标量源参数被忽略，只保留标量")
    void ignoresNonScalarArguments() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("mixed", new MapYamlSection(raw(
                "source", "nearby",
                "arguments", Map.of("radius", 8, "types", List.of("ZOMBIE"), "extra", Map.of("a", 1)))));
        assertEquals(1, definition.sourceArguments().size());
        assertEquals("8", definition.sourceArguments().get("radius"));
    }

    @Test
    @DisplayName("仓库按 id 大小写不敏感查找，未知 id 返回空")
    void repositoryLookup() {
        ConfiguredSelectorRepository repository = ConfiguredSelectorRepository.build(List.of(
                definition("elite_zombies", "nearby"),
                definition("boss_targets", "nearby_players")));

        assertEquals(List.of("boss_targets", "elite_zombies"), repository.ids());
        assertEquals("nearby_players", repository.find("BOSS_TARGETS").sourceId());
        assertNull(repository.find("missing"));
        assertNull(repository.find(null));
    }

    @Test
    @DisplayName("预检报告缺 source、自引用与未注册源")
    void validatorReportsSourceProblems() {
        List<SelectorDefinitionValidator.Finding> findings = SelectorDefinitionValidator.validate(
                List.of(definition("no_source", null),
                        definition("self_reference", "select"),
                        definition("unknown_source", "not_registered")),
                sourceId -> "nearby".equals(sourceId));

        assertEquals(3, findings.size());
        assertEquals(ConfigPrecheckSeverity.ERROR, findings.get(0).severity());
        assertEquals("selector_missing_source", findings.get(0).key());

        assertEquals(ConfigPrecheckSeverity.ERROR, findings.get(1).severity());
        assertEquals("selector_recursive_source", findings.get(1).key());

        assertEquals(ConfigPrecheckSeverity.WARN, findings.get(2).severity());
        assertEquals("selector_unknown_source", findings.get(2).key());
        assertEquals("action.selectors.unknown_source", findings.get(2).path());
    }

    @Test
    @DisplayName("预检报告条件节点缺字段，并给出嵌套路径")
    void validatorReportsMissingConditionFields() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("partial", new MapYamlSection(raw(
                "source", "nearby",
                "condition", Map.of("type", "all_of", "entries", List.of(
                        Map.of("type", "any_of", "entries", List.of(Map.of("type", "health", "value", 5))),
                        Map.of("type", "state", "key", "dancing"))))));

        List<SelectorDefinitionValidator.Finding> findings = SelectorDefinitionValidator.validate(
                List.of(definition), sourceId -> true);

        assertEquals(2, findings.size());
        assertEquals("selector_condition_missing_field", findings.get(0).key());
        assertEquals("action.selectors.partial.condition.entries[0].entries[0]", findings.get(0).path());
        assertEquals("selector_condition_invalid_state", findings.get(1).key());
        assertEquals("action.selectors.partial.condition.entries[1]", findings.get(1).path());
    }

    @Test
    @DisplayName("结构完整的定义不产生预检问题")
    void validatorAcceptsCompleteDefinition() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("complete", new MapYamlSection(raw(
                "source", "nearby",
                "condition", Map.of("type", "all_of", "entries", List.of(
                        Map.of("type", "entity_type", "value", List.of("ZOMBIE")),
                        Map.of("type", "health_percent", "op", "<=", "value", 50),
                        Map.of("type", "potion_effect", "effect", "speed"))))));

        assertTrue(SelectorDefinitionValidator.validate(List.of(definition), sourceId -> true).isEmpty());
    }

    @Test
    @DisplayName("condition.predicates 简写解析为可求值条件组")
    void parsesPredicateShorthand() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("elites", new MapYamlSection(raw(
                "source", "nearby",
                "condition", Map.of(
                        "predicates", "entity_type=ZOMBIE health_percent<=50",
                        "invalid_as_failure", false))));

        assertTrue(definition.defined());
        assertTrue(definition.conditioned());
        assertFalse(definition.broken());
        assertFalse(definition.invalidAsFailure());
    }

    @Test
    @DisplayName("predicates 与 entries 同时写被判为配置冲突")
    void predicatesAndEntriesConflict() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("conflicted", new MapYamlSection(raw(
                "source", "nearby",
                "condition", Map.of(
                        "predicates", "entity_type=ZOMBIE",
                        "entries", List.of(Map.of("type", "entity_type", "value", "SKELETON"))))));

        assertTrue(definition.broken());
        assertEquals(SelectorDefinition.CONFLICT_REASON, definition.conditionProblem());

        List<SelectorDefinitionValidator.Finding> findings = SelectorDefinitionValidator.validate(
                List.of(definition), sourceId -> true);
        assertEquals(1, findings.size());
        assertEquals(ConfigPrecheckSeverity.ERROR, findings.get(0).severity());
        assertEquals(SelectorDefinition.CONFLICT_REASON, findings.get(0).key());
        assertEquals("action.selectors.conflicted.condition", findings.get(0).path());
    }

    @Test
    @DisplayName("无法解析的谓词串在预检报错并带出出错 token")
    void invalidPredicateIsReported() {
        SelectorDefinition definition = SelectorDefinition.fromConfig("broken_predicate", new MapYamlSection(raw(
                "source", "nearby",
                "condition", Map.of("predicates", "helth>=50"))));

        assertTrue(definition.broken());
        assertEquals("action.gate.filter.invalid_condition", definition.conditionProblem());
        assertEquals("helth>=50", definition.problemArguments().get("token"));
        assertEquals("broken_predicate", definition.problemArguments().get("selector"));

        List<SelectorDefinitionValidator.Finding> findings = SelectorDefinitionValidator.validate(
                List.of(definition), sourceId -> true);
        assertEquals(1, findings.size());
        assertEquals(ConfigPrecheckSeverity.ERROR, findings.get(0).severity());
    }

    private static SelectorDefinition definition(String id, String sourceId) {
        return new SelectorDefinition(id, sourceId, Map.of(), 0, ConditionGroup.empty(), true, "", Map.of());
    }

    private static Map<String, Object> raw(Object... pairs) {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            raw.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return raw;
    }
}
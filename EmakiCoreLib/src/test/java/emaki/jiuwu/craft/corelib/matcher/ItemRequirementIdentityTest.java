package emaki.jiuwu.craft.corelib.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("匹配节点的稳定身份与字段职责")
class ItemRequirementIdentityTest {

    private static Map<String, Object> node(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static Map<String, Object> componentMatcher(String value) {
        return node("type", "component", "component", "custom_name", "operator", "contains", "value", value);
    }

    @Test
    @DisplayName("显式 id 优先")
    void explicitIdWins() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node(
                "id", "My-Requirement",
                "item_sources", List.of("minecraft-iron_ingot"),
                "matcher", componentMatcher("iron")));
        assertEquals("my-requirement", requirement.identity());
        assertEquals("my-requirement", requirement.canonicalIdentity());
        assertTrue(requirement.hasCanonicalIdentity());
    }

    @Test
    @DisplayName("替代身份键都可使用")
    void alternateIdentityKeysAreAccepted() {
        for (String key : List.of("material_id", "count_key", "slot_id", "audit_id")) {
            ItemRequirement requirement = ItemRequirement.fromConfig(node(key, "Custom-Key"));
            assertEquals("custom-key", requirement.identity());
        }
    }

    @Test
    @DisplayName("来源身份不随 matcher 内容变化")
    void sourceIdentityIsIndependentOfMatcherContent() {
        ItemRequirement first = ItemRequirement.fromConfig(node(
                "item_sources", List.of("minecraft-iron_ingot"),
                "matcher", componentMatcher("iron")));
        ItemRequirement second = ItemRequirement.fromConfig(node(
                "item_sources", List.of("minecraft-iron_ingot"),
                "matcher", componentMatcher("steel")));
        assertEquals(first.identity(), second.identity());
        assertEquals("minecraft-iron_ingot", first.identity());
        assertEquals("", first.canonicalIdentity());
        assertTrue(first.hasDerivedIdentity());
    }

    @Test
    @DisplayName("多来源身份按声明顺序去重")
    void multiSourceIdentityIsDeterministic() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node(
                "item_sources", List.of("minecraft-iron_ingot", "minecraft-gold_ingot", "minecraft-iron_ingot")));
        assertEquals("minecraft-iron_ingot+minecraft-gold_ingot", requirement.identity());
        assertEquals(2, requirement.sources().size());
    }

    @Test
    @DisplayName("仅 matcher 时使用派生身份")
    void matcherOnlyIdentityIsMarkedDerived() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node("matcher", componentMatcher("iron")));
        assertTrue(requirement.identity().startsWith("matcher-"));
    }

    @Test
    @DisplayName("仅 matcher 的派生身份会随内容变化")
    void derivedIdentityDriftsWithMatcherContent() {
        ItemRequirement first = ItemRequirement.fromConfig(node("matcher", componentMatcher("iron")));
        ItemRequirement second = ItemRequirement.fromConfig(node("matcher", componentMatcher("steel")));
        assertNotEquals(first.identity(), second.identity());
    }

    @Test
    @DisplayName("来源会去重并过滤无法解析的条目")
    void sourcesAreDeduplicatedAndValidated() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node(
                "item_sources", List.of("minecraft-iron_ingot", "minecraft-iron_ingot", "not-a-real-source")));
        assertEquals(1, requirement.sources().size());
        assertEquals("minecraft-iron_ingot", requirement.identity());
    }

    @Test
    @DisplayName("空需求没有判定条件")
    void emptyRequirement() {
        ItemRequirement requirement = ItemRequirement.fromConfig(Map.of());
        assertTrue(requirement.empty());
        assertFalse(requirement.declaresSources());
        assertFalse(requirement.declaresMatcher());
    }

    @Test
    @DisplayName("没有来源时来源条件不限制")
    void noSourcesMeansSourceUnconstrained() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node("matcher", componentMatcher("iron")));
        assertTrue(requirement.matchesSource(null));
    }

    @Test
    @DisplayName("未知来源不会被声明为有效来源")
    void declaredSourcesRejectUnknownSource() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node("item_sources", List.of("unknown-provider-item")));
        assertTrue(requirement.sources().isEmpty());
        assertTrue(requirement.empty());
    }

    @Test
    @DisplayName("自定义来源键和 matcher 键可解析")
    void customKeysAreSupported() {
        ItemRequirement requirement = ItemRequirement.fromConfig(
                node("custom_sources", List.of("minecraft-iron_ingot"), "predicate", componentMatcher("iron")),
                "custom_sources",
                "predicate");
        assertEquals("minecraft-iron_ingot", requirement.identity());
        assertTrue(requirement.declaresSources());
        assertTrue(requirement.declaresMatcher());
    }
}

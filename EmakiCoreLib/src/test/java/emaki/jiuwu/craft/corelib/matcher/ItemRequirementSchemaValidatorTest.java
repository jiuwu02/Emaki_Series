package emaki.jiuwu.craft.corelib.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator.Role;

class ItemRequirementSchemaValidatorTest {

    private static Map<String, Object> node(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static Map<String, Object> matcher() {
        return node("type", "component", "component", "custom_name", "operator", "contains", "value", "iron");
    }

    private static boolean hasError(List<ConfigPrecheckIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity().blocking());
    }

    @Test
    void sourceOnlyIsValid() {
        assertFalse(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "item_sources", List.of("minecraft-iron_ingot")), Role.INPUT)));
    }

    @Test
    void matcherOnlyIsValid() {
        assertFalse(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "matcher", matcher()), Role.INPUT)));
    }

    @Test
    void sourceAndMatcherAreValidAndSemanticallySeparate() {
        assertFalse(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "item_sources", List.of("minecraft-iron_ingot"), "matcher", matcher()), Role.INPUT)));
    }

    @Test
    void matcherRejectsAllSourceAliases() {
        for (String key : List.of("item_source", "item_sources", "source", "sources")) {
            Map<String, Object> nested = node("type", "all_of", "matchers", List.of(
                    node("type", "component", "component", "custom_name"),
                    node(key, List.of("minecraft-iron_ingot"))));
            assertTrue(hasError(ItemRequirementSchemaValidator.validate("test", "input",
                    node("matcher", nested), Role.INPUT)), key);
        }
    }

    @Test
    void topLevelAliasesConflict() {
        assertTrue(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "item_sources", List.of("minecraft-iron_ingot"),
                "sources", List.of("minecraft-gold_ingot"),
                "matcher", matcher(),
                "item_matcher", matcher()), Role.INPUT)));
    }

    @Test
    void topLevelMatchersAliasIsRejected() {
        assertTrue(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "item_sources", List.of("minecraft-iron_ingot"), "matchers", matcher()), Role.INPUT)));
    }

    @Test
    void customKeysStillReceiveFullValidation() {
        ItemRequirement requirement = ItemRequirement.fromConfig(node(
                "custom_sources", List.of("minecraft-iron_ingot"),
                "predicate", node("type", "item_source")), "custom_sources", "predicate");
        assertTrue(requirement.empty());
    }

    @Test
    void identityAmountAndOutputRulesAreValidated() {
        assertTrue(hasError(ItemRequirementSchemaValidator.validate("test", "input", node(
                "id", 42, "amount", 0, "item_sources", List.of("minecraft-iron_ingot")), Role.MATERIAL)));
        assertTrue(hasError(ItemRequirementSchemaValidator.validate("test", "output", node(
                "item_sources", List.of("minecraft-iron_ingot", "minecraft-gold_ingot")), Role.OUTPUT)));
        assertFalse(hasError(ItemRequirementSchemaValidator.validate("test", "output", node(
                "item_sources", List.of("minecraft-iron_ingot")), Role.OUTPUT)));
    }

    @Test
    void duplicateIdentitiesAreRejected() {
        List<ConfigPrecheckIssue> issues = ItemRequirementSchemaValidator.validateAll("test", "inputs", List.of(
                node("id", "Iron", "item_sources", List.of("minecraft-iron_ingot")),
                node("id", "iron", "item_sources", List.of("minecraft-gold_ingot"))), Role.INPUT);
        assertTrue(hasError(issues));
    }

    @Test
    void allocationIdentityIsRetained() {
        ItemRequirement requirement = new ItemRequirement(List.of(), null, "derived", "canonical");
        MaterialRequest request = new MaterialRequest(requirement, 2, "material", "requirement", "shared", "slot", "audit");
        assertTrue(request.identity().equals("material"));
        assertTrue(requirement.canonicalIdentity().equals("canonical"));
    }
}

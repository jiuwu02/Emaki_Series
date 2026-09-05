package emaki.jiuwu.craft.strengthen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;

public final class MaterialIdentityPlannerTest {

    @Test
    void associatesInputsOnlyThroughMatcherPredicate() {
        List<MaterialIdentityPlanner.Definition> definitions = List.of(
                new MaterialIdentityPlanner.Definition(0, "ruby", "ruby", 1, false, false, 0, true),
                new MaterialIdentityPlanner.Definition(1, "sapphire", "sapphire", 1, false, false, 0, true));
        List<MaterialIdentityPlanner.Input> inputs = List.of(
                new MaterialIdentityPlanner.Input(0, 1),
                new MaterialIdentityPlanner.Input(1, 1));
        MaterialIdentityPlanner.Plan plan = MaterialIdentityPlanner.plan(definitions, inputs,
                (definition, input) -> definition.order() == input.index());
        assertTrue(plan.satisfied());
        assertEquals(1, plan.consumedForMaterial("ruby"));
        assertEquals(1, plan.consumedForMaterial("sapphire"));
    }

    @Test
    void aggregatesOnlyByCountKey() {
        List<MaterialIdentityPlanner.Definition> definitions = List.of(
                new MaterialIdentityPlanner.Definition(0, "ruby_a", "gem_pool", 1, false, false, 0, true),
                new MaterialIdentityPlanner.Definition(1, "ruby_b", "gem_pool", 1, false, false, 0, true));
        List<MaterialIdentityPlanner.Input> inputs = List.of(
                new MaterialIdentityPlanner.Input(0, 1),
                new MaterialIdentityPlanner.Input(1, 1));
        MaterialIdentityPlanner.Plan plan = MaterialIdentityPlanner.plan(definitions, inputs,
                (definition, input) -> definition.order() == input.index());
        assertTrue(plan.satisfied());
        assertEquals(2, plan.consumedByCountKey().getOrDefault("gem_pool", 0));
        assertEquals(1, plan.consumedForMaterial("ruby_a"));
        assertEquals(1, plan.consumedForMaterial("ruby_b"));
    }

    @Test
    void distinguishesMissingRequiredAndOptionalDefinitions() {
        MaterialIdentityPlanner.Definition required = new MaterialIdentityPlanner.Definition(
                0, "required", "required", 1, false, false, 0, false);
        MaterialIdentityPlanner.Definition optional = new MaterialIdentityPlanner.Definition(
                1, "optional", "optional", 1, true, false, 0, false);

        assertTrue(MaterialIdentityPlanner.plan(List.of(optional), List.of(), (definition, input) -> false).satisfied());
        assertFalse(MaterialIdentityPlanner.plan(List.of(required), List.of(),
                (definition, input) -> false).satisfied());
    }

    @Test
    void preservesOptionalProtectionTemperAndRefundIdentity() {
        List<MaterialIdentityPlanner.Definition> definitions = List.of(
                new MaterialIdentityPlanner.Definition(0, "base", "base", 2, false, false, 2, false),
                new MaterialIdentityPlanner.Definition(1, "guard", "guard", 1, true, true, 7, false));
        List<MaterialIdentityPlanner.Input> inputs = List.of(
                new MaterialIdentityPlanner.Input(4, 3),
                new MaterialIdentityPlanner.Input(7, 2));
        MaterialIdentityPlanner.Plan plan = MaterialIdentityPlanner.plan(definitions, inputs,
                (definition, input) -> definition.order() == 0 ? input.index() == 4 : input.index() == 7);
        assertTrue(plan.satisfied());
        assertTrue(plan.protectionApplied());
        assertEquals(4, plan.temperBonus());
        assertEquals(2, plan.consumedFromInput(4));
        assertEquals(0, plan.consumedFromInput(7));
    }

    @Test
    void preservesSignatureIdentity() {
        MaterialSignatureEntry ruby = new MaterialSignatureEntry("ruby", "gems", 1);
        MaterialSignatureEntry sapphire = new MaterialSignatureEntry("sapphire", "gems", 1);
        assertNotEquals(ruby.row(), sapphire.row());
    }

    @Test
    void aggregatesSignatureByMaterialAndCountIdentityOnly() {
        AttemptMaterial first = new AttemptMaterial("minecraft-gem", 1, 1, true, false, 0, 1,
                "ruby", "gems", 0, "minecraft-gem");
        AttemptMaterial second = new AttemptMaterial("custom-ruby", 1, 1, true, false, 0, 2,
                "ruby", "gems", 7, "custom-ruby");
        List<Object> expected = List.of(Map.of(
                "material_id", "ruby",
                "count_key", "gems",
                "amount", 3));

        assertEquals(expected, StrengthenAttemptService.materialSignatureRows(List.of(first, second)));
        assertEquals(expected, StrengthenAttemptService.materialSignatureRows(List.of(second, first)));
    }

    @Test
    void projectsExactDefinitionIdentityWithoutInputGroupingLeakage() {
        List<MaterialIdentityPlanner.Definition> definitions = List.of(
                new MaterialIdentityPlanner.Definition(0, "ruby", "ruby_count", 1, false, false, 0, false),
                new MaterialIdentityPlanner.Definition(1, "sapphire", "sapphire_count", 1, false, false, 0, false));
        MaterialIdentityPlanner.Plan groupedPlan = MaterialIdentityPlanner.plan(definitions,
                List.of(new MaterialIdentityPlanner.Input(4, 2)), (definition, input) -> true);
        List<AttemptMaterial> grouped = MaterialAttemptProjection.project(definitions,
                List.of(new MaterialAttemptProjection.Input(4, 2, "minecraft-gem", "source-a")), groupedPlan);

        assertEquals(2, grouped.size());
        assertEquals(List.of("ruby", "sapphire"), grouped.stream().map(AttemptMaterial::materialId).toList());
        assertEquals(List.of("ruby_count", "sapphire_count"), grouped.stream().map(AttemptMaterial::countKey).toList());
        assertEquals(List.of(4, 4), grouped.stream().map(AttemptMaterial::inputIndex).toList());
    }

    @Test
    void keepsSignatureStableAcrossStackSplitSlotAndAuditTokenChanges() {
        List<MaterialIdentityPlanner.Definition> definitions = List.of(
                new MaterialIdentityPlanner.Definition(0, "ruby", "ruby_count", 1, false, false, 0, false),
                new MaterialIdentityPlanner.Definition(1, "sapphire", "sapphire_count", 1, false, false, 0, false));
        MaterialIdentityPlanner.Plan groupedPlan = MaterialIdentityPlanner.plan(definitions,
                List.of(new MaterialIdentityPlanner.Input(4, 2)), (definition, input) -> true);
        List<AttemptMaterial> grouped = MaterialAttemptProjection.project(definitions,
                List.of(new MaterialAttemptProjection.Input(4, 2, "minecraft-gem", "source-a")), groupedPlan);

        List<MaterialIdentityPlanner.Input> splitInputs = List.of(
                new MaterialIdentityPlanner.Input(9, 1),
                new MaterialIdentityPlanner.Input(2, 1));
        MaterialIdentityPlanner.Plan splitPlan = MaterialIdentityPlanner.plan(definitions, splitInputs,
                (definition, input) -> true);
        List<AttemptMaterial> split = MaterialAttemptProjection.project(definitions, List.of(
                new MaterialAttemptProjection.Input(9, 1, "custom-ruby", "source-b"),
                new MaterialAttemptProjection.Input(2, 1, "custom-sapphire", "source-c")), splitPlan);

        assertEquals(StrengthenAttemptService.materialSignatureRows(grouped),
                StrengthenAttemptService.materialSignatureRows(split));
    }
}

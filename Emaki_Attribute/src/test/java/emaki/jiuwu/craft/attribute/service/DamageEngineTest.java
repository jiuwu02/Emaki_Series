package emaki.jiuwu.craft.attribute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DamageEngineTest {

    @Test
    void resolvesFlatPercentBeforeCriticalMultiplier() {
        DamageStageDefinition attack = new DamageStageDefinition(
            "attack",
            DamageStageKind.FLAT_PERCENT,
            DamageStageSource.ATTACKER,
            DamageStageMode.ADD,
            List.of("flat"),
            List.of("percent"),
            List.of(),
            List.of(),
            "",
            null,
            null,
            null,
            null,
            null,
            null
        );
        DamageStageDefinition crit = new DamageStageDefinition(
            "crit",
            DamageStageKind.CUSTOM,
            DamageStageSource.ATTACKER,
            DamageStageMode.ADD,
            List.of(),
            List.of(),
            List.of("crit_rate"),
            List.of("crit_damage"),
            "{input} * (1 + ({crit} * ({multiplier} / 100)))",
            null,
            null,
            0D,
            100D,
            -100D,
            100000D
        );
        DamageTypeDefinition typeDefinition = new DamageTypeDefinition(
            "test",
            "Test",
            List.of(),
            Set.of(),
            false,
            List.of(attack, crit),
            null
        );
        AttributeSnapshot attacker = new AttributeSnapshot(
            AttributeSnapshot.CURRENT_SCHEMA_VERSION,
            "attacker",
            Map.of(
                "flat", 10D,
                "percent", 50D,
                "crit_rate", 100D,
                "crit_damage", 50D
            ),
            System.currentTimeMillis()
        );
        DamageRequest request = new DamageRequest("test", 10D, attacker, AttributeSnapshot.empty("target"), Map.of());

        DamageResult result = new DamageEngine().resolve(request, typeDefinition, 0D);

        assertEquals(30D, result.stageValues().get("attack"), 0.0001D);
        assertEquals(45D, result.finalDamage(), 0.0001D);
        assertTrue(result.critical());
    }

    @Test
    void resolvesTargetDefenseAfterAttackerStages() {
        DamageStageDefinition attack = new DamageStageDefinition(
            "attack",
            DamageStageKind.FLAT_PERCENT,
            DamageStageSource.ATTACKER,
            DamageStageMode.ADD,
            List.of("flat"),
            List.of("percent"),
            List.of(),
            List.of(),
            "",
            null,
            null,
            null,
            null,
            null,
            null
        );
        DamageStageDefinition crit = new DamageStageDefinition(
            "crit",
            DamageStageKind.CUSTOM,
            DamageStageSource.ATTACKER,
            DamageStageMode.ADD,
            List.of(),
            List.of(),
            List.of("crit_rate"),
            List.of("crit_damage"),
            "{input} * (1 + ({crit} * ({multiplier} / 100)))",
            null,
            null,
            0D,
            100D,
            -100D,
            100000D
        );
        DamageStageDefinition defense = new DamageStageDefinition(
            "defense",
            DamageStageKind.FLAT_PERCENT,
            DamageStageSource.TARGET,
            DamageStageMode.SUBTRACT,
            List.of("defense"),
            List.of(),
            List.of(),
            List.of(),
            "",
            null,
            null,
            null,
            null,
            null,
            null
        );
        DamageTypeDefinition typeDefinition = new DamageTypeDefinition(
            "physical",
            "Physical",
            List.of(),
            Set.of(),
            true,
            List.of(attack, crit, defense),
            null
        );
        AttributeSnapshot attacker = new AttributeSnapshot(
            AttributeSnapshot.CURRENT_SCHEMA_VERSION,
            "attacker",
            Map.of(
                "flat", 10D,
                "percent", 50D,
                "crit_rate", 100D,
                "crit_damage", 50D
            ),
            System.currentTimeMillis()
        );
        AttributeSnapshot target = new AttributeSnapshot(
            AttributeSnapshot.CURRENT_SCHEMA_VERSION,
            "target",
            Map.of("defense", 8D),
            System.currentTimeMillis()
        );
        DamageRequest request = new DamageRequest("physical", 10D, attacker, target, Map.of());

        DamageResult result = new DamageEngine().resolve(request, typeDefinition, 0D);

        assertEquals(37D, result.finalDamage(), 0.0001D);
        assertEquals(37D, result.stageValues().get("defense"), 0.0001D);
        assertTrue(result.critical());
    }
}

package emaki.jiuwu.craft.attribute.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DamageTypeDefinitionTest {

    @Test
    void parsesSharedAndDedicatedDamageMessages() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "physical");
        raw.put("display_name", "物理伤害");
        raw.put("message", "<gray>{attacker} -> {target}</gray>");
        raw.put("attacker_message", "<green>you</green>");
        raw.put("stages", List.of());

        DamageTypeDefinition definition = DamageTypeDefinition.fromMap(raw);

        assertEquals("<green>you</green>", definition.attackerMessage());
        assertEquals("<gray>{attacker} -> {target}</gray>", definition.targetMessage());
        assertTrue(definition.hasAttackerMessage());
        assertTrue(definition.hasTargetMessage());
    }
}

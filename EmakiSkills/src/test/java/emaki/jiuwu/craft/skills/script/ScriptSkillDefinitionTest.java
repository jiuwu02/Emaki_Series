package emaki.jiuwu.craft.skills.script;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.skills.api.ExternalSkillDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptSkillDefinitionTest {

    @Test
    void mapsEveryPayloadFieldIntoStableDefinitionValues() {
        SkillScriptPayload payload = new SkillScriptPayload(
                "Script Burst",
                "<red>烈焰爆发</red>",
                Arrays.asList("<gray>第一行</gray>", "  ", null),
                "BLAZE_POWDER",
                "PASSIVE",
                List.of("Attack", "timer", "Attack"),
                40L,
                15L,
                List.of("Ultimate", "ultimate"),
                List.of(" 烈焰爆发 ", " "),
                " PDC_ID ",
                " attack ",
                7,
                Boolean.FALSE,
                Boolean.FALSE,
                Map.of("cast", List.of(" self | play_sound sound=x ")),
                Map.of("hit", List.of("inherited | damage amount=1")),
                " MythicName ");

        ScriptSkillDefinition definition = ScriptSkillDefinition.create(payload);

        assertNotNull(definition);
        assertEquals("script_burst", definition.id());
        assertEquals("<red>烈焰爆发</red>", definition.displayName());
        assertEquals(List.of("<gray>第一行</gray>"), definition.description());
        assertEquals("BLAZE_POWDER", definition.iconMaterial());
        assertEquals("PASSIVE", definition.activationType());
        assertEquals(List.of("attack", "timer"), definition.passiveTriggers());
        assertEquals(40L, definition.cooldownTicks());
        assertEquals(15L, definition.globalCooldownTicks());
        assertEquals(Map.of("cast", List.of("self | play_sound sound=x")), definition.scriptLines());
        assertEquals(Map.of("hit", List.of("inherited | damage amount=1")), definition.scriptConditions());
        assertEquals(List.of("ultimate"), definition.tags());
        assertEquals(List.of("烈焰爆发"), definition.loreAliases());
        assertEquals("PDC_ID", definition.pdcSkillId());
        assertEquals("attack", definition.uiCategory());
        assertEquals(7, definition.sortOrder());
        assertFalse(definition.showInSlots());
        assertFalse(definition.enabled());
        assertEquals("MythicName", definition.mythicSkill());
    }

    @Test
    void rejectsBlankId() {
        assertNull(ScriptSkillDefinition.create(SkillScriptPayload.EMPTY));
        assertNull(ScriptSkillDefinition.create(null));
        assertNull(ScriptSkillDefinition.create(payloadWithId("   ")));
        assertNull(ScriptSkillDefinition.create(payloadWithId("")));
    }

    @Test
    void normalizesIdLikeYamlSkillIds() {
        ScriptSkillDefinition definition = ScriptSkillDefinition.create(payloadWithId("  Fire Storm  "));
        assertNotNull(definition);
        assertEquals("fire_storm", definition.id());
    }

    @Test
    void collectsOnlyKnownPhasesCaseInsensitivelyAndIgnoresUnknownKeys() {
        SkillScriptPayload payload = payloadWithId("phase_check");
        SkillScriptPayload withPhases = new SkillScriptPayload(
                payload.id(),
                payload.displayName(),
                payload.description(),
                payload.iconMaterial(),
                payload.activationType(),
                payload.passiveTriggers(),
                payload.cooldownTicks(),
                payload.globalCooldownTicks(),
                payload.tags(),
                payload.loreAliases(),
                payload.pdcSkillId(),
                payload.uiCategory(),
                payload.sortOrder(),
                payload.showInSlots(),
                payload.enabled(),
                new java.util.LinkedHashMap<>(Map.of(
                        "Cast", List.of("self | spawn_particle particle=flame count=1"),
                        " HIT ", List.of("inherited | damage amount=1"),
                        "miss", List.of("  "),
                        "FAIL", List.of("send_message text=\"x\""),
                        "unknown", List.of("ignored | action"),
                        "fail-phase", List.of("ignored | action"))),
                payload.scriptConditions(),
                payload.mythicSkill());

        ScriptSkillDefinition definition = ScriptSkillDefinition.create(withPhases);

        assertNotNull(definition);
        Map<String, List<String>> lines = definition.scriptLines();
        assertEquals(3, lines.size());
        assertEquals(List.of("self | spawn_particle particle=flame count=1"), lines.get("cast"));
        assertEquals(List.of("inherited | damage amount=1"), lines.get("hit"));
        assertEquals(List.of("send_message text=\"x\""), lines.get("fail"));
        assertFalse(lines.containsKey("unknown"));
        assertFalse(lines.containsKey("fail-phase"));
        assertFalse(lines.containsKey("miss"));
    }

    @Test
    void fallsBackActivationTypeToActiveOnIllegalValues() {
        for (String illegal : new String[]{"weird", "", "act ive", "ACTIVE-PASSIVE"}) {
            ScriptSkillDefinition definition = ScriptSkillDefinition.create(payloadWithActivation(illegal));
            assertNotNull(definition);
            assertEquals("ACTIVE", definition.activationType(), () -> "activation: " + illegal);
        }
        assertEquals("PASSIVE", ScriptSkillDefinition.create(payloadWithActivation("passive")).activationType());
        assertEquals("PASSIVE", ScriptSkillDefinition.create(payloadWithActivation(" Passive ")).activationType());
    }

    @Test
    void appliesContractDefaultsWhenOptionalFieldsMissing() {
        SkillScriptPayload payload = new SkillScriptPayload(
                "defaults_check",
                "",
                null,
                "",
                "",
                null,
                -5L,
                -1L,
                null,
                null,
                "",
                "",
                0,
                null,
                null,
                Map.of(),
                Map.of(),
                "");

        ScriptSkillDefinition definition = ScriptSkillDefinition.create(payload);

        assertNotNull(definition);
        assertEquals("defaults_check", definition.displayName());
        assertEquals("ACTIVE", definition.activationType());
        assertEquals(0L, definition.cooldownTicks());
        assertEquals(0L, definition.globalCooldownTicks());
        assertEquals(List.of(), definition.description());
        assertEquals(List.of(), definition.passiveTriggers());
        assertEquals(List.of(), definition.tags());
        assertEquals(List.of(), definition.loreAliases());
        assertEquals("default", definition.uiCategory());
        assertTrue(definition.showInSlots());
        assertTrue(definition.enabled());
        assertEquals(Map.of(), definition.scriptLines());
        assertEquals(Map.of(), definition.scriptConditions());
        assertEquals(0, definition.sortOrder());
    }

    @Test
    void exposesInterfaceDefaultsConsistently() {
        ScriptSkillDefinition definition = ScriptSkillDefinition.create(payloadWithId("iface_check"));
        assertNotNull(definition);
        ExternalSkillDefinition asInterface = definition;
        assertEquals(definition.id(), asInterface.id());
        assertEquals(definition.scriptLines(), asInterface.scriptLines());
        assertEquals(definition.enabled(), asInterface.enabled());
    }

    private static SkillScriptPayload payloadWithId(String id) {
        return new SkillScriptPayload(
                id,
                "",
                List.of(),
                "",
                "",
                List.of(),
                0L,
                0L,
                List.of(),
                List.of(),
                "",
                "",
                0,
                null,
                null,
                Map.of(),
                Map.of(),
                "");
    }

    private static SkillScriptPayload payloadWithActivation(String activationType) {
        return new SkillScriptPayload(
                "activation_check",
                "",
                List.of(),
                "",
                activationType,
                List.of(),
                0L,
                0L,
                List.of(),
                List.of(),
                "",
                "",
                0,
                null,
                null,
                Map.of(),
                Map.of(),
                "");
    }
}

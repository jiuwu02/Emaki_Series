package emaki.jiuwu.craft.skills.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.CastAttemptResult.FailureReason;
import org.junit.jupiter.api.Test;

class CastAttemptResultTest {

    @Test
    void replacementsAreDefensivelyCopiedAndImmutable() {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("remaining", "1.5");

        CastAttemptResult result = CastAttemptResult.fail(
                FailureReason.GLOBAL_COOLDOWN_ACTIVE,
                "cast.global_cooldown",
                replacements
        );
        replacements.put("remaining", "9.9");

        assertEquals("1.5", result.replacements().get("remaining"));
        assertThrows(UnsupportedOperationException.class, result.replacements()::clear);
    }

    @Test
    void compatibilityFactoriesUseEmptyReplacements() {
        CastAttemptResult success = CastAttemptResult.ok();
        CastAttemptResult failure = CastAttemptResult.fail(
                FailureReason.CANCELLED,
                "cast.cancelled"
        );

        assertTrue(success.success());
        assertTrue(success.replacements().isEmpty());
        assertFalse(failure.success());
        assertTrue(failure.replacements().isEmpty());
    }

    @Test
    void replacementsRenderCooldownAndResourceTemplatesWithoutRawPlaceholders() {
        CastAttemptResult cooldown = CastAttemptResult.fail(
                FailureReason.SKILL_COOLDOWN_ACTIVE,
                "cast.skill_cooldown",
                Map.of("remaining", "2.5", "skill", "烈焰斩")
        );
        CastAttemptResult resource = CastAttemptResult.fail(
                FailureReason.RESOURCE_INSUFFICIENT,
                "cast.resource_insufficient",
                Map.of("message", "法力不足，需要 30 点法力")
        );

        String cooldownMessage = Texts.formatTemplate(
                "<yellow>技能 %skill% 冷却中，还剩 %remaining% 秒。</yellow>",
                cooldown.replacements()
        );
        String resourceMessage = Texts.formatTemplate(
                "<red>%message%</red>",
                resource.replacements()
        );

        assertEquals("<yellow>技能 烈焰斩 冷却中，还剩 2.5 秒。</yellow>", cooldownMessage);
        assertEquals("<red>法力不足，需要 30 点法力</red>", resourceMessage);
        assertFalse(cooldownMessage.contains("%skill%"));
        assertFalse(cooldownMessage.contains("%remaining%"));
        assertFalse(resourceMessage.contains("%message%"));
    }
}

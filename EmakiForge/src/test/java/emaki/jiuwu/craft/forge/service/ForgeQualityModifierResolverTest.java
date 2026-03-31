package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeQualityModifierResolverTest {

    private final ForgeQualityModifierResolver resolver = new ForgeQualityModifierResolver();

    @Test
    void forceModifierOverridesMinimumAndKeepsHighestForcedTier() {
        QualitySettings settings = settings();
        QualitySettings.QualityTier resolved = resolver.applyModifiers(
            settings,
            settings.findTier("平庸"),
            List.of(
                new ForgeMaterial.QualityModifier("minimum", "无暇"),
                new ForgeMaterial.QualityModifier("force", "优质"),
                new ForgeMaterial.QualityModifier("force", "完美")
            )
        );
        assertEquals("完美", resolved.name());
        assertTrue(resolver.hasForceModifier(List.of(
            new ForgeMaterial.QualityModifier("minimum", "无暇"),
            new ForgeMaterial.QualityModifier("force", "完美")
        )));
    }

    @Test
    void minimumModifierRaisesFloorWithoutOverridingHigherStoredTier() {
        QualitySettings settings = settings();
        QualitySettings.QualityTier raised = resolver.applyModifiers(
            settings,
            settings.findTier("平庸"),
            List.of(new ForgeMaterial.QualityModifier("minimum", "无暇"))
        );
        QualitySettings.QualityTier kept = resolver.applyModifiers(
            settings,
            settings.findTier("完美"),
            List.of(new ForgeMaterial.QualityModifier("minimum", "无暇"))
        );
        assertEquals("无暇", raised.name());
        assertEquals("完美", kept.name());
        assertFalse(resolver.hasForceModifier(List.of(new ForgeMaterial.QualityModifier("minimum", "无暇"))));
    }

    private QualitySettings settings() {
        return new QualitySettings(
            List.of(
                new QualitySettings.QualityTier("平庸", 60, 1.0D),
                new QualitySettings.QualityTier("精良", 30, 1.05D),
                new QualitySettings.QualityTier("优质", 5, 1.1D),
                new QualitySettings.QualityTier("无暇", 2, 1.15D),
                new QualitySettings.QualityTier("完美", 1, 1.2D)
            ),
            "平庸",
            false,
            10,
            "平庸",
            false,
            Map.of(),
            Map.of(),
            Map.of()
        );
    }
}

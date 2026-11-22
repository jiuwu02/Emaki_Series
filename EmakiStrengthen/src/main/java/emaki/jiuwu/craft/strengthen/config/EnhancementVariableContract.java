package emaki.jiuwu.craft.strengthen.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.strengthen.enhancement.EnhancementTargetVariables;

public final class EnhancementVariableContract {

    private EnhancementVariableContract() {
    }

    public static List<String> violations() {
        List<String> violations = new ArrayList<>();
        checkMultiplierCoercion(violations);
        checkDefaults(violations);
        return List.copyOf(violations);
    }

    private static void checkMultiplierCoercion(List<String> violations) {
        assertMultiplier(violations, "2.5", 2.5D, true);
        assertMultiplier(violations, 2.5D, 2.5D, true);
        assertMultiplier(violations, "0", 0D, true);
        assertMultiplier(violations, "abc", EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER, false);
        assertMultiplier(violations, "-1", EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER, false);
        assertMultiplier(violations, Double.NaN, EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER, false);
        assertMultiplier(violations, Double.POSITIVE_INFINITY,
                EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER, false);
        assertMultiplier(violations, null, EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER, false);
    }

    private static void assertMultiplier(List<String> violations,
            Object raw,
            double expectedValue,
            boolean expectedValid) {
        double value = EnhancementTargetVariables.coerceQualityMultiplier(raw);
        boolean valid = EnhancementTargetVariables.validQualityMultiplier(raw);
        if (Double.compare(value, expectedValue) != 0) {
            violations.add("品质倍率 '" + raw + "' 期望为 " + expectedValue
                    + ", 实际为 " + value);
        }
        if (valid != expectedValid) {
            violations.add("品质倍率有效性 '" + raw + "' 期望为 " + expectedValid
                    + ", 实际为 " + valid);
        }
    }

    private static void checkDefaults(List<String> violations) {
        EnhancementTargetVariables.Snapshot snapshot = EnhancementTargetVariables.capture(null, null, null);
        Map<String, Object> variables = snapshot.variables();
        for (String name : List.of("forge_quality_id", "forge_quality_display",
                "forge_quality_multiplier", "forge_recipe_id")) {
            if (!variables.containsKey(name)) {
                violations.add("默认变量 '" + name + "' 在空捕获中缺失");
            }
        }
        Object multiplier = variables.get("forge_quality_multiplier");
        if (!(multiplier instanceof Number number)
                || Double.compare(number.doubleValue(), EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER) != 0) {
            violations.add("默认 forge_quality_multiplier 期望为 "
                    + EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER + ", 实际为 " + multiplier);
        }
        Object multiplierValid = variables.get(EnhancementTargetVariables.VARIABLE_MULTIPLIER_VALID);
        if (!(multiplierValid instanceof Number validFlag) || validFlag.intValue() != 1) {
            violations.add("默认 " + EnhancementTargetVariables.VARIABLE_MULTIPLIER_VALID
                    + " 期望为 1, 实际为 " + multiplierValid);
        }
        Object readErrors = variables.get(EnhancementTargetVariables.VARIABLE_PDC_READ_ERRORS);
        if (!(readErrors instanceof Number errorCount) || errorCount.intValue() != 0) {
            violations.add("默认 " + EnhancementTargetVariables.VARIABLE_PDC_READ_ERRORS
                    + " 期望为 0, 实际为 " + readErrors);
        }
        if (!snapshot.unreadablePdcKeys().isEmpty()) {
            violations.add("空捕获不应报告任何不可读的 PDC 键");
        }
    }
}

package emaki.jiuwu.craft.strengthen.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.strengthen.enhancement.EnhancementTargetVariables;

public final class EnhancementVariableContract {

    private static final Map<String, List<String>> EXPECTED_ALIASES = Map.of(
            EnhancementTargetVariables.FORGE_PATH_QUALITY_ID,
            List.of("forge_quality_id", "forge.quality_id", "quality_id"),
            EnhancementTargetVariables.FORGE_PATH_QUALITY_DISPLAY,
            List.of("forge_quality_display", "forge.quality_display", "quality_display"),
            EnhancementTargetVariables.FORGE_PATH_QUALITY_MULTIPLIER,
            List.of("forge_quality_multiplier", "forge.quality_multiplier", "quality_multiplier"),
            EnhancementTargetVariables.FORGE_PATH_RECIPE_ID,
            List.of("forge_recipe_id", "forge.forge_recipe_id"));

    private EnhancementVariableContract() {
    }

    public static List<String> violations() {
        List<String> violations = new ArrayList<>();
        checkAliasContract(violations);
        checkMultiplierCoercion(violations);
        checkDefaults(violations);
        return List.copyOf(violations);
    }

    private static void checkAliasContract(List<String> violations) {
        Map<String, List<String>> actual = EnhancementTargetVariables.forgeAliasContract();
        if (!actual.keySet().equals(EXPECTED_ALIASES.keySet())) {
            violations.add("forge alias paths expected " + EXPECTED_ALIASES.keySet()
                    + " but was " + actual.keySet());
            return;
        }
        EXPECTED_ALIASES.forEach((path, expected) -> {
            List<String> resolved = EnhancementTargetVariables.forgeAliases(path);
            if (!expected.equals(resolved)) {
                violations.add("forge alias set for '" + path + "' expected " + expected
                        + " but was " + resolved);
            }
        });
        if (!EnhancementTargetVariables.forgeAliases("forge.unknown_key").isEmpty()) {
            violations.add("unknown forge path must not resolve to any alias");
        }
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
            violations.add("quality multiplier for '" + raw + "' expected " + expectedValue
                    + " but was " + value);
        }
        if (valid != expectedValid) {
            violations.add("quality multiplier validity for '" + raw + "' expected " + expectedValid
                    + " but was " + valid);
        }
    }

    private static void checkDefaults(List<String> violations) {
        EnhancementTargetVariables.Snapshot snapshot = EnhancementTargetVariables.capture(null, null, null);
        Map<String, Object> variables = snapshot.variables();
        for (List<String> names : EXPECTED_ALIASES.values()) {
            for (String name : names) {
                if (!variables.containsKey(name)) {
                    violations.add("default variable '" + name + "' is missing from a blank capture");
                }
            }
        }
        Object multiplier = variables.get("forge_quality_multiplier");
        if (!(multiplier instanceof Number number)
                || Double.compare(number.doubleValue(), EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER) != 0) {
            violations.add("default forge_quality_multiplier expected "
                    + EnhancementTargetVariables.DEFAULT_QUALITY_MULTIPLIER + " but was " + multiplier);
        }
        Object multiplierValid = variables.get(EnhancementTargetVariables.VARIABLE_MULTIPLIER_VALID);
        if (!(multiplierValid instanceof Number validFlag) || validFlag.intValue() != 1) {
            violations.add("default " + EnhancementTargetVariables.VARIABLE_MULTIPLIER_VALID
                    + " expected 1 but was " + multiplierValid);
        }
        Object readErrors = variables.get(EnhancementTargetVariables.VARIABLE_PDC_READ_ERRORS);
        if (!(readErrors instanceof Number errorCount) || errorCount.intValue() != 0) {
            violations.add("default " + EnhancementTargetVariables.VARIABLE_PDC_READ_ERRORS
                    + " expected 0 but was " + readErrors);
        }
        if (!snapshot.unreadablePdcKeys().isEmpty()) {
            violations.add("blank capture must report no unreadable pdc keys");
        }
    }
}

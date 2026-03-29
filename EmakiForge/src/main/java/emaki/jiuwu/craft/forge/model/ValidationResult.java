package emaki.jiuwu.craft.forge.model;

import java.util.Map;

public record ValidationResult(boolean success, String errorKey, Map<String, Object> replacements) {

    public static ValidationResult ok() {
        return new ValidationResult(true, null, Map.of());
    }

    public static ValidationResult fail(String errorKey) {
        return new ValidationResult(false, errorKey, Map.of());
    }

    public static ValidationResult fail(String errorKey, Map<String, Object> replacements) {
        return new ValidationResult(false, errorKey, replacements == null ? Map.of() : replacements);
    }
}

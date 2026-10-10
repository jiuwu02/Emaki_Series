package emaki.jiuwu.craft.storage.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

final class UnlockCostsValidator {

    private UnlockCostsValidator() {
    }

    static EditorValidator validator() {
        return EditorValidator.allOf(EditorValidator.yamlOnly(), UnlockCostsValidator::structure);
    }

    private static String structure(YamlTextDocument candidate, String docId, Path source) {
        try {
            Map<String, Object> root = candidate.root();
            Object tiers = root.get("tiers");
            if (tiers != null && !tiersValid(tiers)) {
                return "unlock_tiers_invalid";
            }
            Object fallback = root.get("fallback");
            if (fallback != null && !fallbackValid(fallback)) {
                return "unlock_fallback_invalid";
            }
            Object batch = root.get("batch");
            if (batch != null && !batchValid(batch)) {
                return "unlock_batch_invalid";
            }
            return null;
        } catch (RuntimeException failure) {
            return "unlock_structure_invalid";
        }
    }

    private static boolean tiersValid(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> tier)) {
                return false;
            }
            if (!rangeValid(tier.get("count_range"))) {
                return false;
            }
            boolean hasCurrency = tier.containsKey("currency");
            boolean hasItem = tier.containsKey("item");
            if (hasCurrency && !currencyValid(tier.get("currency"))) {
                return false;
            }
            if (hasItem && !itemValid(tier.get("item"))) {
                return false;
            }
            if (!hasCurrency && !hasItem) {
                return false;
            }
        }
        return true;
    }

    private static boolean fallbackValid(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        boolean hasCurrency = map.containsKey("currency");
        boolean hasItem = map.containsKey("item");
        if (hasCurrency && !currencyValid(map.get("currency"))) {
            return false;
        }
        if (hasItem && !itemValid(map.get("item"))) {
            return false;
        }
        if (!hasCurrency && !hasItem) {
            return false;
        }
        Object maxAmount = map.get("max_amount");
        return maxAmount instanceof Number number && number.doubleValue() > 0.0d;
    }

    private static boolean batchValid(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        Object enabled = map.get("enabled");
        if (enabled != null && !(enabled instanceof Boolean)) {
            return false;
        }
        Object options = map.get("options");
        if (options == null) {
            return true;
        }
        if (!(options instanceof List<?> list)) {
            return false;
        }
        for (Object option : list) {
            if (!(option instanceof Number number) || number.intValue() <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean currencyValid(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        Object type = map.get("type");
        if (type != null) {
            String normalized = Texts.toStringSafe(type).trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()
                    && !"vault".equals(normalized)
                    && !"excellent".equals(normalized)) {
                return false;
            }
        }
        Object amount = map.get("amount");
        return amount instanceof Number
                || (amount instanceof String expression && !expression.isBlank());
    }

    private static boolean itemValid(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        Object source = map.get("source");
        if (!(source instanceof String text) || text.isBlank()) {
            return false;
        }
        Object amount = map.get("amount");
        if (amount == null) {
            return true;
        }
        return amount instanceof Number number && number.intValue() > 0;
    }

    private static boolean rangeValid(Object raw) {
        if (raw == null) {
            return false;
        }
        String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        int dash = normalized.indexOf('-');
        try {
            if (dash < 0) {
                return Integer.parseInt(normalized) > 0;
            }
            int min = Integer.parseInt(normalized.substring(0, dash).trim());
            int max = Integer.parseInt(normalized.substring(dash + 1).trim());
            return min > 0 && max >= min;
        } catch (NumberFormatException failure) {
            return false;
        }
    }
}

package emaki.jiuwu.craft.accessory.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record AccessorySlotSourceConfig(boolean pdcEnabled,
        String pdcKey,
        boolean loreEnabled,
        List<Pattern> lorePatterns,
        Map<String, String> patternErrors,
        String separator) {

    public static final String DEFAULT_SEPARATOR = ",";

    public static AccessorySlotSourceConfig defaults() {
        return new AccessorySlotSourceConfig(
                false, "", false, List.of(), Map.of(), DEFAULT_SEPARATOR);
    }

    public static AccessorySlotSourceConfig of(boolean pdcEnabled,
            String pdcKey,
            boolean loreEnabled,
            List<String> rawPatterns,
            String separator) {
        List<Pattern> compiled = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        if (rawPatterns != null) {
            for (String raw : rawPatterns) {
                String candidate = Texts.trim(raw);
                if (Texts.isBlank(candidate)) {
                    continue;
                }
                try {
                    compiled.add(Pattern.compile(candidate,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (RuntimeException exception) {
                    errors.put(candidate, Texts.toStringSafe(exception.getMessage()));
                }
            }
        }
        return new AccessorySlotSourceConfig(
                pdcEnabled, pdcKey, loreEnabled, compiled, errors, separator);
    }

    public AccessorySlotSourceConfig {
        pdcKey = Texts.trim(pdcKey);
        lorePatterns = lorePatterns == null ? List.of() : List.copyOf(lorePatterns);
        patternErrors = patternErrors == null ? Map.of() : Map.copyOf(patternErrors);
        separator = Texts.isBlank(separator) ? DEFAULT_SEPARATOR : separator;
    }

    public boolean pdcUsable() {
        return pdcEnabled && Texts.isNotBlank(pdcKey);
    }

    public boolean loreUsable() {
        return loreEnabled && !lorePatterns.isEmpty();
    }

}

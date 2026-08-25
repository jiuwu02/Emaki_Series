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
        String separator,
        Map<String, String> aliases) {

    public static final String DEFAULT_SEPARATOR = ",";

    public static AccessorySlotSourceConfig defaults() {
        return new AccessorySlotSourceConfig(
                false, "", false, List.of(), Map.of(), DEFAULT_SEPARATOR, Map.of());
    }

    public static AccessorySlotSourceConfig of(boolean pdcEnabled,
            String pdcKey,
            boolean loreEnabled,
            List<String> rawPatterns,
            String separator,
            Map<String, String> aliases) {
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
                pdcEnabled, pdcKey, loreEnabled, compiled, errors, separator, aliases);
    }

    public AccessorySlotSourceConfig {
        pdcKey = Texts.trim(pdcKey);
        lorePatterns = lorePatterns == null ? List.of() : List.copyOf(lorePatterns);
        patternErrors = patternErrors == null ? Map.of() : Map.copyOf(patternErrors);
        separator = Texts.isBlank(separator) ? DEFAULT_SEPARATOR : separator;
        aliases = aliases == null ? Map.of() : normalizeAliases(aliases);
    }

    public boolean pdcUsable() {
        return pdcEnabled && Texts.isNotBlank(pdcKey);
    }

    public boolean loreUsable() {
        return loreEnabled && !lorePatterns.isEmpty();
    }

    public String resolveAlias(String declared) {
        String candidate = Texts.trim(declared);
        if (Texts.isBlank(candidate)) {
            return "";
        }
        String mapped = aliases.get(candidate);
        if (mapped == null) {
            mapped = aliases.get(Texts.lower(candidate));
        }
        return Texts.isNotBlank(mapped) ? mapped : Texts.normalizeId(candidate);
    }

    private static Map<String, String> normalizeAliases(Map<String, String> raw) {
        Map<String, String> normalized = new LinkedHashMap<>();
        raw.forEach((display, partId) -> {
            String source = Texts.trim(display);
            String target = Texts.normalizeId(partId);
            if (Texts.isNotBlank(source) && Texts.isNotBlank(target)) {
                normalized.put(source, target);
            }
        });
        return Map.copyOf(normalized);
    }
}

package emaki.jiuwu.craft.forge.model;

import java.util.Locale;

public record ForgeMaterialIdentity(String materialId, String countKey, String auditId) {
    public static ForgeMaterialIdentity resolve(String materialId, String countKey, String auditId,
            String sourceIdentity, String matcherIdentity) {
        String resolvedMaterial = first(materialId, sourceIdentity, matcherIdentity);
        return new ForgeMaterialIdentity(
                resolvedMaterial,
                first(countKey, resolvedMaterial),
                first(auditId, resolvedMaterial));
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }
}

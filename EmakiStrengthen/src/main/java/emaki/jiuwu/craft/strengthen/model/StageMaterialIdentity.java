package emaki.jiuwu.craft.strengthen.model;

import java.util.List;
import java.util.Locale;

public record StageMaterialIdentity(String materialId, String countKey) {

    public StageMaterialIdentity {
        materialId = normalize(materialId);
        countKey = normalize(countKey);
        if (countKey.isEmpty()) {
            countKey = materialId;
        }
    }

    public static StageMaterialIdentity resolve(String declaredMaterialId,
            String legacyId,
            String declaredCountKey,
            List<String> sourceTokens,
            int index) {
        String materialId = firstNonBlank(declaredMaterialId, legacyId, declaredCountKey,
                sourceTokens == null || sourceTokens.isEmpty() ? "" : sourceTokens.getFirst(),
                "material_" + (Math.max(0, index) + 1));
        return new StageMaterialIdentity(materialId, firstNonBlank(declaredCountKey, materialId));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

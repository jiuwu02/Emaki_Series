package emaki.jiuwu.craft.strengthen.model;

import java.util.Locale;

public final class StageMaterialRuleKey {

    private StageMaterialRuleKey() {
    }

    public static String of(String branchPath, int targetStar, String materialId) {
        return normalize(branchPath) + "@" + targetStar + "|" + normalize(materialId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.strengthen.enhancement.cost.TargetCompareEnum;

public record StarStageMaterialRule(@NotNull TargetCompareEnum targetCompare, @Nullable Matcher matcher) {

    public StarStageMaterialRule {
        targetCompare = targetCompare == null ? TargetCompareEnum.NONE : targetCompare;
    }

    public static @NotNull StarStageMaterialRule inert() {
        return new StarStageMaterialRule(TargetCompareEnum.NONE, null);
    }

    public boolean constrains() {
        return targetCompare != TargetCompareEnum.NONE || matcher != null;
    }

    public static @NotNull StarStageMaterialRule fromRawEntry(@Nullable Map<?, ?> rawEntry) {
        if (rawEntry == null) {
            return inert();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawEntry.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        YamlSection section = new MapYamlSection(converted);
        TargetCompareEnum compare = TargetCompareEnum.fromStringOrDefault(
                section.getString("target_compare", ""), TargetCompareEnum.NONE);
        YamlSection matcherSection = section.getSection("matcher");
        Matcher matcher = matcherSection == null ? null : Matcher.fromConfig(matcherSection);
        return new StarStageMaterialRule(compare, matcher);
    }

    public static @NotNull String key(int targetStar, @Nullable String materialId) {
        return key("", targetStar, materialId);
    }

    public static @NotNull String key(@Nullable String branchPath, int targetStar, @Nullable String materialId) {
        return StageMaterialRuleKey.of(branchPath, targetStar, materialId);
    }

    public static @NotNull List<String> illegalCompareTokens(@Nullable Map<?, ?> rawEntry) {
        if (rawEntry == null) {
            return List.of();
        }
        Object raw = null;
        for (Map.Entry<?, ?> entry : rawEntry.entrySet()) {
            if (entry.getKey() != null && "target_compare".equals(String.valueOf(entry.getKey()))) {
                raw = entry.getValue();
            }
        }
        if (raw == null) {
            return List.of();
        }
        String token = Texts.toStringSafe(raw);
        if (Texts.isBlank(token)) {
            return List.of();
        }
        TargetCompareEnum resolved = TargetCompareEnum.fromStringOrDefault(token, TargetCompareEnum.NONE);
        return resolved == TargetCompareEnum.NONE && !token.isBlank() ? List.of(token) : List.of();
    }
}

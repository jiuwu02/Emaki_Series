package emaki.jiuwu.craft.attribute.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

public record AttributeDefinition(String id,
        String displayName,
        AttributeValueKind valueKind,
        AttributeTargetType targetType,
        String targetId,
        String mmoItemsStatId,
        double defaultValue,
        Double minValue,
        Double maxValue,
        boolean allowNegative,
        int priority,
        String loreFormatId,
        List<String> lorePatterns,
        String description,
        double attributePower,
        List<String> tags,
        TemporaryStackMode temporaryStackMode) {

    public AttributeDefinition {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : Texts.toStringSafe(displayName).trim();
        valueKind = valueKind == null ? AttributeValueKind.FLAT : valueKind;
        targetType = targetType == null ? AttributeTargetType.GENERIC : targetType;
        targetId = Texts.toStringSafe(targetId).trim().toLowerCase(Locale.ROOT);
        mmoItemsStatId = Texts.toStringSafe(mmoItemsStatId).trim();
        loreFormatId = Texts.toStringSafe(loreFormatId).trim().toLowerCase(Locale.ROOT);
        lorePatterns = lorePatterns == null ? List.of() : List.copyOf(lorePatterns);
        description = Texts.toStringSafe(description).trim();
        attributePower = Double.isNaN(attributePower) ? 1D : attributePower;
        tags = normalizeTags(tags);
        temporaryStackMode = temporaryStackMode == null ? TemporaryStackMode.REPLACE : temporaryStackMode;
    }

    public double clamp(double value) {
        double result = value;
        if (minValue != null) {
            result = Math.max(result, minValue);
        }
        if (maxValue != null) {
            result = Math.min(result, maxValue);
        }
        if (!allowNegative && result < 0D) {
            result = 0D;
        }
        return result;
    }

    public boolean isPercentLike() {
        return valueKind == AttributeValueKind.PERCENT || valueKind == AttributeValueKind.CHANCE;
    }

    public boolean isResourceValue() {
        return targetType == AttributeTargetType.RESOURCE;
    }

    public boolean isVanillaMappedValue() {
        return targetType == AttributeTargetType.VANILLA;
    }

    public boolean hasTag(String tag) {
        if (Texts.isBlank(tag) || tags.isEmpty()) {
            return false;
        }
        return tags.contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (Texts.isBlank(tag)) {
                continue;
            }
            String value = tag.trim().toLowerCase(Locale.ROOT);
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private static String normalizeId(String value) {
        return Texts.normalizeId(value);
    }
}

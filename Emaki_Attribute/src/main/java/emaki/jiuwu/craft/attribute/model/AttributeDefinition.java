package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record AttributeDefinition(String id,
                                  String displayName,
                                  List<String> aliases,
                                  AttributeValueKind valueKind,
                                  AttributeTargetType targetType,
                                  String targetId,
                                  double defaultValue,
                                  Double minValue,
                                  Double maxValue,
                                  boolean allowNegative,
                                  int priority,
                                  String loreFormatId,
                                  List<String> lorePatterns,
                                  String description,
                                  double attributePower) {

    public AttributeDefinition {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : Texts.toStringSafe(displayName).trim();
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        valueKind = valueKind == null ? AttributeValueKind.FLAT : valueKind;
        targetType = targetType == null ? AttributeTargetType.GENERIC : targetType;
        targetId = Texts.toStringSafe(targetId).trim().toLowerCase(Locale.ROOT);
        loreFormatId = Texts.toStringSafe(loreFormatId).trim().toLowerCase(Locale.ROOT);
        lorePatterns = lorePatterns == null ? List.of() : List.copyOf(lorePatterns);
        description = Texts.toStringSafe(description).trim();
        attributePower = Double.isNaN(attributePower) ? 1D : attributePower;
    }

    public boolean matchesAlias(String candidate) {
        if (Texts.isBlank(candidate)) {
            return false;
        }
        String normalized = normalizeId(candidate);
        if (Objects.equals(id, normalized)) {
            return true;
        }
        for (String alias : aliases) {
            if (Objects.equals(normalizeId(alias), normalized)) {
                return true;
            }
        }
        return false;
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

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}

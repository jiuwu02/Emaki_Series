package emaki.jiuwu.craft.attribute.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record RecoveryDefinition(DamageStageSource source,
        DamageStageSource resistanceSource,
        List<String> flatAttributes,
        List<String> percentAttributes,
        List<String> resistanceAttributes,
        String expression,
        Double minResult,
        Double maxResult) {

    public RecoveryDefinition        {
        source = source == null ? DamageStageSource.ATTACKER : source;
        resistanceSource = resistanceSource == null ? DamageStageSource.TARGET : resistanceSource;
        flatAttributes = flatAttributes == null ? List.of() : List.copyOf(flatAttributes);
        percentAttributes = percentAttributes == null ? List.of() : List.copyOf(percentAttributes);
        resistanceAttributes = resistanceAttributes == null ? List.of() : List.copyOf(resistanceAttributes);
        expression = Texts.toStringSafe(expression).trim();
    }

    public static RecoveryDefinition fromMap(Object raw) {
        return fromMap(raw, null);
    }

    public static RecoveryDefinition fromMap(Object raw, Function<String, String> attributeNormalizer) {
        if (raw == null) {
            return null;
        }
        return new RecoveryDefinition(
                parseEnum(ConfigNodes.string(raw, "source", "ATTACKER"), DamageStageSource.ATTACKER),
                parseEnum(ConfigNodes.string(raw, "resistance_source", "TARGET"), DamageStageSource.TARGET),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "flat_attributes")), attributeNormalizer),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "percent_attributes")), attributeNormalizer),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "resistance_attributes")), attributeNormalizer),
                ConfigNodes.string(raw, "expression", null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "min_result"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "max_result"), null)
        );
    }

    private static List<String> normalizeAttributes(List<String> ids, Function<String, String> attributeNormalizer) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            String resolved = attributeNormalizer == null ? id : attributeNormalizer.apply(id);
            String normalizedId = normalizeId(resolved);
            if (!normalizedId.isBlank()) {
                normalized.add(normalizedId);
            }
        }
        return List.copyOf(normalized);
    }

    private static <E extends Enum<E>> E parseEnum(String value, E defaultValue) {
        if (Texts.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(defaultValue.getDeclaringClass(), value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}

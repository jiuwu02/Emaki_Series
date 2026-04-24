package emaki.jiuwu.craft.attribute.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public record DamageStageDefinition(String id,
        DamageStageKind kind,
        DamageStageSource source,
        DamageStageMode mode,
        List<String> flatAttributes,
        List<String> percentAttributes,
        List<String> chanceAttributes,
        List<String> multiplierAttributes,
        String expression,
        Double minResult,
        Double maxResult,
        Double minChance,
        Double maxChance,
        Double minMultiplier,
        Double maxMultiplier) {

    private static final Map<DamageStageDefinition, AttributeSignatureCache> ATTRIBUTE_SIGNATURE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public DamageStageDefinition               {
        id = Texts.normalizeId(id);
        kind = kind == null ? DamageStageKind.FLAT_PERCENT : kind;
        source = source == null ? DamageStageSource.ATTACKER : source;
        mode = mode == null ? DamageStageMode.ADD : mode;
        flatAttributes = flatAttributes == null ? List.of() : List.copyOf(flatAttributes);
        percentAttributes = percentAttributes == null ? List.of() : List.copyOf(percentAttributes);
        chanceAttributes = chanceAttributes == null ? List.of() : List.copyOf(chanceAttributes);
        multiplierAttributes = multiplierAttributes == null ? List.of() : List.copyOf(multiplierAttributes);
        expression = Texts.toStringSafe(expression).trim();
    }

    public static DamageStageDefinition fromMap(Object raw) {
        return fromMap(raw, null);
    }

    public static DamageStageDefinition fromMap(Object raw, Function<String, String> attributeNormalizer) {
        if (raw == null) {
            return null;
        }
        String id = ConfigNodes.string(raw, "id", "stage");
        DamageStageKind kind = parseEnum(ConfigNodes.string(raw, "kind", "FLAT_PERCENT"), DamageStageKind.FLAT_PERCENT);
        DamageStageSource source = parseEnum(ConfigNodes.string(raw, "source", "ATTACKER"), DamageStageSource.ATTACKER);
        DamageStageMode mode = parseEnum(ConfigNodes.string(raw, "mode", "ADD"), DamageStageMode.ADD);
        return new DamageStageDefinition(
                id,
                kind,
                source,
                mode,
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "flat_attributes")), attributeNormalizer),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "percent_attributes")), attributeNormalizer),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "chance_attributes")), attributeNormalizer),
                normalizeAttributes(Texts.asStringList(ConfigNodes.get(raw, "multiplier_attributes")), attributeNormalizer),
                ConfigNodes.string(raw, "expression", null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "min_result"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "max_result"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "min_chance"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "max_chance"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "min_multiplier"), null),
                Numbers.tryParseDouble(ConfigNodes.get(raw, "max_multiplier"), null)
        );
    }

    public String flatAttributesSignature() {
        return attributeSignatureCache().flatAttributesSignature();
    }

    public String percentAttributesSignature() {
        return attributeSignatureCache().percentAttributesSignature();
    }

    public String chanceAttributesSignature() {
        return attributeSignatureCache().chanceAttributesSignature();
    }

    public String multiplierAttributesSignature() {
        return attributeSignatureCache().multiplierAttributesSignature();
    }

    private AttributeSignatureCache attributeSignatureCache() {
        return ATTRIBUTE_SIGNATURE_CACHE.computeIfAbsent(
                this,
                definition -> new AttributeSignatureCache(
                        SignatureUtil.stableSignature(definition.flatAttributes()),
                        SignatureUtil.stableSignature(definition.percentAttributes()),
                        SignatureUtil.stableSignature(definition.chanceAttributes()),
                        SignatureUtil.stableSignature(definition.multiplierAttributes())
                )
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
            String normalizedId = Texts.normalizeId(resolved);
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

    private record AttributeSignatureCache(String flatAttributesSignature,
            String percentAttributesSignature,
            String chanceAttributesSignature,
            String multiplierAttributesSignature) {

    }
}


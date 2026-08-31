package emaki.jiuwu.craft.attribute.config;

import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ScalingCurveConfig(String attributeId, double threshold, String curveType, double factor) {

    public static final String CURVE_LOGARITHMIC = "logarithmic";
    public static final String CURVE_SQRT = "sqrt";
    public static final String CURVE_PIECEWISE_LINEAR = "piecewise_linear";
    public static final String CURVE_LINEAR = "linear";

    private static final Set<String> SUPPORTED_CURVE_TYPES =
            Set.of(CURVE_LOGARITHMIC, CURVE_SQRT, CURVE_PIECEWISE_LINEAR, CURVE_LINEAR);

    public ScalingCurveConfig {
        attributeId = Texts.normalizeId(attributeId);
        threshold = Math.max(0D, threshold);
        curveType = Texts.isBlank(curveType) ? CURVE_LOGARITHMIC : Texts.lower(curveType);

        factor = isLinearCurve(curveType)
                ? Math.max(0D, factor)
                : (factor <= 0D ? 1D : factor);
    }

    public static boolean isSupportedCurveType(String curveType) {
        return SUPPORTED_CURVE_TYPES.contains(Texts.lower(curveType));
    }

    private static boolean isLinearCurve(String curveType) {
        return CURVE_PIECEWISE_LINEAR.equals(curveType) || CURVE_LINEAR.equals(curveType);
    }

    public static ScalingCurveConfig fromConfig(YamlSection section, String fallbackAttributeId) {
        if (section == null) {
            return null;
        }

        Double threshold = section.getDouble("threshold", 0D);
        Double factor = section.getDouble("factor", 1D);
        return new ScalingCurveConfig(
                section.getString("attribute", fallbackAttributeId),
                threshold == null ? 0D : threshold,
                section.getString("curve_type", CURVE_LOGARITHMIC),
                factor == null ? 1D : factor
        );
    }
}

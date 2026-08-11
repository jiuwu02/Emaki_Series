package emaki.jiuwu.craft.attribute.config;

import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * One diminishing-returns rule: the part of an attribute above {@code threshold} is folded through
 * {@code curveType} so stacking cannot inflate without bound.
 */
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
        // `factor` means different things per curve, so the floor does too. For the linear discount it is a
        // retention ratio where 0 legitimately means "drop the whole excess"; clamping that up to 1 would
        // invert the author's intent into "no decay at all". The log/sqrt curves divide by it, so there 0 is
        // not a usable value and has to be lifted.
        factor = isLinearCurve(curveType)
                ? Math.max(0D, factor)
                : (factor <= 0D ? 1D : factor);
    }

    /** Whether {@code curveType} names a curve this module implements. */
    public static boolean isSupportedCurveType(String curveType) {
        return SUPPORTED_CURVE_TYPES.contains(Texts.lower(curveType));
    }

    private static boolean isLinearCurve(String curveType) {
        return CURVE_PIECEWISE_LINEAR.equals(curveType) || CURVE_LINEAR.equals(curveType);
    }

    /**
     * Reads one curve entry. The section key is the fallback attribute id so a rule can be written either as
     * {@code physical_attack: {threshold: 500}} or with an explicit {@code attribute} field.
     */
    public static ScalingCurveConfig fromConfig(YamlSection section, String fallbackAttributeId) {
        if (section == null) {
            return null;
        }
        // getDouble is boxed and returns null when the key holds a non-numeric value, so the defaults are
        // reapplied here rather than passed through to the primitive components.
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

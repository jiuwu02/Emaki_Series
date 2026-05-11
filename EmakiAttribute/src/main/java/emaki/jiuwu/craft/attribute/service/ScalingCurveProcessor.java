package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;

/**
 * Applies diminishing-return curves to attribute values that exceed configured thresholds.
 * <p>
 * Supported curve types:
 * <ul>
 *   <li>{@code logarithmic} — excess is scaled by {@code factor * ln(1 + excess / factor)}</li>
 *   <li>{@code sqrt} — excess is scaled by {@code factor * sqrt(excess / factor)}</li>
 *   <li>{@code piecewise_linear} — excess is multiplied by a flat ratio (e.g. 0.5)</li>
 * </ul>
 */
final class ScalingCurveProcessor {

    ScalingCurveProcessor() {
    }

    /**
     * Apply scaling curves to the given attribute values in-place.
     *
     * @param values mutable map of attribute id → raw value
     * @param curves the configured curves (may be null or empty)
     */
    void apply(Map<String, Double> values, List<ScalingCurveConfig> curves) {
        if (values == null || values.isEmpty() || curves == null || curves.isEmpty()) {
            return;
        }
        for (ScalingCurveConfig curve : curves) {
            if (curve == null || curve.attributeId().isEmpty()) {
                continue;
            }
            Double rawValue = values.get(curve.attributeId());
            if (rawValue == null || rawValue <= curve.threshold()) {
                continue;
            }
            double excess = rawValue - curve.threshold();
            double scaledExcess = applyFunction(excess, curve.curveType(), curve.factor());
            values.put(curve.attributeId(), curve.threshold() + scaledExcess);
        }
    }

    private double applyFunction(double excess, String curveType, double factor) {
        return switch (curveType) {
            case "sqrt" -> factor * Math.sqrt(excess / factor);
            case "piecewise_linear", "linear" -> excess * factor;
            case "logarithmic" -> factor * Math.log1p(excess / factor);
            default -> factor * Math.log1p(excess / factor);
        };
    }
}

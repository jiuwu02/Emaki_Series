package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.config.ScalingCurveConfig;

final class ScalingCurveProcessor {

    ScalingCurveProcessor() {
    }

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
            case ScalingCurveConfig.CURVE_SQRT -> factor * Math.sqrt(excess / factor);
            case ScalingCurveConfig.CURVE_PIECEWISE_LINEAR, ScalingCurveConfig.CURVE_LINEAR -> excess * factor;
            // Unknown types land here as well. The config precheck reports them, so the fallback only has to
            // stay predictable rather than also diagnose.
            default -> factor * Math.log1p(excess / factor);
        };
    }
}

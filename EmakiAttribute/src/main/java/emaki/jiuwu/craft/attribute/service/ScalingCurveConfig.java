package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ScalingCurveConfig(String attributeId, double threshold, String curveType, double factor) {

    public ScalingCurveConfig {
        attributeId = Texts.normalizeId(attributeId);
        threshold = Math.max(0D, threshold);
        curveType = Texts.isBlank(curveType) ? "logarithmic" : Texts.lower(curveType);
        factor = factor <= 0D ? 1D : factor;
    }
}

package emaki.jiuwu.craft.attribute.registry;

import java.util.Map;

public interface DerivedAttributeProvider {

    String attributeId();

    double compute(Map<String, Double> baseValues);
}

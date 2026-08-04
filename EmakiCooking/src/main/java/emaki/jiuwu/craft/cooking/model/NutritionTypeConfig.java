package emaki.jiuwu.craft.cooking.model;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;







public final class NutritionTypeConfig {

    private final String id;
    private final String displayName;
    private final double min;
    private final double max;
    private final double defaultValue;

    public NutritionTypeConfig(String id, String displayName, double min, double max, double defaultValue) {
        this.id = Texts.normalizeId(id);
        this.displayName = Texts.isBlank(displayName) ? this.id : displayName;
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        this.min = low;
        this.max = high;
        this.defaultValue = clamp(defaultValue, low, high);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double defaultValue() {
        return defaultValue;
    }




    public double clamp(double value) {
        return clamp(value, min, max);
    }

    private static double clamp(double value, double low, double high) {
        if (value < low) {
            return low;
        }
        return Math.min(value, high);
    }




    public static NutritionTypeConfig parse(YamlSection section, String fallbackId) {
        if (section == null) {
            return new NutritionTypeConfig(fallbackId, fallbackId, 0D, 100D, 0D);
        }
        String id = section.getString("id", fallbackId);
        String displayName = section.getString("display_name", id);
        double min = section.getDouble("min", 0D);
        double max = section.getDouble("max", 100D);
        double defaultValue = section.getDouble("default", min);
        return new NutritionTypeConfig(id, displayName, min, max, defaultValue);
    }
}

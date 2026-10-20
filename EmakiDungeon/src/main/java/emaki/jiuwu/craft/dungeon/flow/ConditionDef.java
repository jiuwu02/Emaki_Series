package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.Map;

public record ConditionDef(
        String type,
        Map<String, Object> params,
        String logic,
        List<ConditionDef> children
) {

    public ConditionDef {
        type = type == null ? "" : type;
        params = params == null ? Map.of() : Map.copyOf(params);
        logic = logic == null || logic.isEmpty() ? "AND" : logic;
        children = children == null ? List.of() : List.copyOf(children);
    }

    public boolean composite() {
        return !children.isEmpty();
    }

    public String param(String key, String fallback) {
        Object value = params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public int paramInt(String key, int fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public double paramDouble(String key, double fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public boolean paramBoolean(String key, boolean fallback) {
        Object value = params.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}

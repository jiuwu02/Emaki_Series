package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.Map;

public record FlowActionDef(
        String type,
        Map<String, Object> params
) {

    public FlowActionDef {
        type = type == null ? "" : type;
        params = params == null ? Map.of() : Map.copyOf(params);
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

    public List<String> paramList(String key) {
        Object value = params.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}

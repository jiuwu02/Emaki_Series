package emaki.jiuwu.craft.dummy.scenario;

import java.util.Map;

public record ScenarioStep(String action, Map<String, Object> params) {

    public String param(String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public Double numberParam(String key) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public int timeoutSeconds(int fallbackSeconds) {
        Integer timeout = params.get("timeout") instanceof Number number ? number.intValue() : null;
        if (timeout == null && params.get("timeout") instanceof String text) {
            try {
                timeout = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                timeout = null;
            }
        }
        return timeout == null || timeout <= 0 ? fallbackSeconds : timeout;
    }

    public boolean continueOnFail() {
        return "continue".equals(param("on_fail"));
    }

    public String summary() {
        StringBuilder text = new StringBuilder(96);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
    }
}

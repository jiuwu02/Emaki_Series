package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.util.List;

public final class WebPluginApiRequest {

    private final String moduleId;
    private final String routeId;
    private final String method;
    private final String body;

    WebPluginApiRequest(String moduleId, String routeId, String method, String body) {
        this.moduleId = moduleId;
        this.routeId = routeId;
        this.method = method;
        this.body = body == null ? "" : body;
    }

    public String moduleId() {
        return moduleId;
    }

    public String routeId() {
        return routeId;
    }

    public String method() {
        return method;
    }

    public String body() {
        return body;
    }

    public String string(String key) {
        return WebJson.extractString(body, key);
    }

    public int integer(String key, int fallback) {
        Object value = WebJson.extractValue(body, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> stringList(String key) {
        Object value = WebJson.extractValue(body, key);
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    public void requirePost() throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            throw new IOException("Method Not Allowed");
        }
    }
}

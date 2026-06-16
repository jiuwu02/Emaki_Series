package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebPluginApiRequest {

    private final String moduleId;
    private final String routeId;
    private final String method;
    private final String body;
    private final boolean configWriteAllowed;
    private final String actor;
    private final WebConsoleService webConsoleService;
    private Object parsedBody;

    WebPluginApiRequest(String moduleId,
            String routeId,
            String method,
            String body,
            boolean configWriteAllowed,
            String actor,
            WebConsoleService webConsoleService) {
        this.moduleId = moduleId;
        this.routeId = routeId;
        this.method = method;
        this.body = body == null ? "" : body;
        this.configWriteAllowed = configWriteAllowed;
        this.actor = actor == null || actor.isBlank() ? "web" : actor;
        this.webConsoleService = webConsoleService;
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

    public String actor() {
        return actor;
    }

    public String string(String key) {
        Object value = value(key);
        return value == null ? "" : String.valueOf(value);
    }

    public int integer(String key, int fallback) {
        Object value = value(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public boolean bool(String key, boolean fallback) {
        Object value = value(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    public Object value(String key) {
        Object parsed = parsedBody();
        if (parsed instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    public Object nestedValue(String... path) {
        Object current = parsedBody();
        if (path == null || path.length == 0) {
            return current;
        }
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map) || key == null) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map(String key) {
        Object value = value(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    public List<String> stringList(String key) {
        Object value = value(key);
        return value instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    public Map<String, Long> longMap(String key) {
        Object value = value(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Long parsed = toLong(entry.getValue());
            if (parsed != null) {
                result.put(String.valueOf(entry.getKey()), parsed);
            }
        }
        return result;
    }

    public void requirePost() throws IOException {
        if (!"POST".equalsIgnoreCase(method)) {
            throw new WebPluginApiException(405, "Method Not Allowed");
        }
    }

    public void requireConfigWriteAllowed() throws IOException {
        if (!configWriteAllowed) {
            throw new WebPluginApiException(403, "当前已关闭 Web 配置写入权限。", Map.of("errorType", "config_write_disabled"));
        }
    }

    public long saveModuleConfig(String moduleId,
            String path,
            String kind,
            String content,
            Long expectedRevision,
            String operation) throws IOException {
        requireConfigWriteAllowed();
        if (webConsoleService == null) {
            throw new IOException("Web Console 服务不可用");
        }
        return webConsoleService.saveModuleConfigFromPlugin(moduleId, path, kind, content, expectedRevision, operation, actor);
    }

    private Object parsedBody() {
        if (parsedBody == null) {
            parsedBody = WebJson.parse(body);
        }
        return parsedBody;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class WebPluginApiException extends IOException {
        private final int status;
        private final Map<String, ?> details;

        public WebPluginApiException(int status, String message) {
            this(status, message, Map.of());
        }

        public WebPluginApiException(int status, String message, Map<String, ?> details) {
            super(message);
            this.status = status;
            this.details = details == null ? Map.of() : details;
        }

        public int status() {
            return status;
        }

        public Map<String, ?> details() {
            return details;
        }
    }
}

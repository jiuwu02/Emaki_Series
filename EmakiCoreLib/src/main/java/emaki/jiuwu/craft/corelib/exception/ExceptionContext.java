package emaki.jiuwu.craft.corelib.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExceptionContext {

    private final Map<String, Object> data;

    private ExceptionContext(Map<String, Object> data) {
        this.data = Map.copyOf(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExceptionContext empty() {
        return new Builder().build();
    }

    public static ExceptionContext of(String key, Object value) {
        return builder().with(key, value).build();
    }

    public static ExceptionContext of(String key1, Object value1, String key2, Object value2) {
        return builder().with(key1, value1).with(key2, value2).build();
    }

    public Map<String, Object> asMap() {
        return data;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getAsString(String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public int size() {
        return data.size();
    }

    public static final class Builder {

        private final Map<String, Object> data = new LinkedHashMap<>();

        public Builder with(String key, Object value) {
            if (key != null && value != null) {
                data.put(key, value);
            }
            return this;
        }

        public Builder withAll(Map<String, Object> map) {
            if (map != null) {
                data.putAll(map);
            }
            return this;
        }

        public Builder withPlugin(String pluginName) {
            return with("plugin", pluginName);
        }

        public Builder withOperation(String operation) {
            return with("operation", operation);
        }

        public Builder withFile(String fileName) {
            return with("file", fileName);
        }

        public Builder withPath(String path) {
            return with("path", path);
        }

        public Builder withPlayer(String playerName) {
            return with("player", playerName);
        }

        public Builder withEntity(String entityId) {
            return with("entity", entityId);
        }

        public Builder withAttribute(String attributeId) {
            return with("attribute", attributeId);
        }

        public Builder withRecipe(String recipeId) {
            return with("recipe", recipeId);
        }

        public ExceptionContext build() {
            return new ExceptionContext(data);
        }
    }
}

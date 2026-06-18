package emaki.jiuwu.craft.corelib.event.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record EmakiDomainEventContext(Map<String, Object> values) {

    public EmakiDomainEventContext {
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    public static EmakiDomainEventContext empty() {
        return new EmakiDomainEventContext(Map.of());
    }

    public Object get(String key) {
        return values.get(key);
    }
}

package emaki.jiuwu.craft.corelib.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;

public interface RuntimeComponents extends EmakiServiceRegistry {

    Map<Class<?>, Object> services();

    static Map<Class<?>, Object> services(Component... components) {
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        if (components == null || components.length == 0) {
            return Map.of();
        }
        for (Component component : components) {
            if (component == null || component.type() == null || component.value() == null) {
                continue;
            }
            services.put(component.type(), component.value());
        }
        return Map.copyOf(services);
    }

    static Component component(Class<?> type, Object value) {
        return new Component(type, value);
    }

    default <T> T service(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object value = services().get(type);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    default <T> T getService(Class<T> type) {
        return service(type);
    }

    record Component(Class<?> type, Object value) {
    }
}

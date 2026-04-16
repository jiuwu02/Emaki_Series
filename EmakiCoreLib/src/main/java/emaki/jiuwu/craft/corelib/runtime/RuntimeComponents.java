package emaki.jiuwu.craft.corelib.runtime;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;

public interface RuntimeComponents extends EmakiServiceRegistry {

    default Map<Class<?>, Object> services() {
        RecordComponent[] components = getClass().getRecordComponents();
        if (components == null || components.length == 0) {
            return Map.of();
        }
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        for (RecordComponent component : components) {
            if (component == null || component.getAccessor() == null) {
                continue;
            }
            try {
                component.getAccessor().setAccessible(true);
                Object value = component.getAccessor().invoke(this);
                if (value != null) {
                    services.put(component.getType(), value);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to expose runtime component: " + component.getName(), exception);
            }
        }
        return Map.copyOf(services);
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
}

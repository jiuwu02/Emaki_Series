package emaki.jiuwu.craft.corelib.runtime;

import java.util.Map;

public interface RuntimeComponents {

    Map<Class<?>, Object> services();

    default <T> T service(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object value = services().get(type);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}

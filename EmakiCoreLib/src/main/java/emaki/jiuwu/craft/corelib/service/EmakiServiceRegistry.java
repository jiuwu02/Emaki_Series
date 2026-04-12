package emaki.jiuwu.craft.corelib.service;

public interface EmakiServiceRegistry {

    <T> T getService(Class<T> type);

    default <T> T requireService(Class<T> type) {
        T service = getService(type);
        if (service != null) {
            return service;
        }
        String name = type == null ? "<unknown>" : type.getName();
        throw new IllegalStateException("Required service is not registered: " + name);
    }
}

package emaki.jiuwu.craft.corelib.exception;

import java.util.Map;

public final class RegistryException extends FrameworkException {

    public RegistryException(String message) {
        super("REGISTRY_ERROR", message);
    }

    public RegistryException(String message, Map<String, Object> context) {
        super("REGISTRY_ERROR", message, context);
    }

    public RegistryException(String message, Throwable cause) {
        super("REGISTRY_ERROR", message, cause);
    }

    public RegistryException(String message, Throwable cause, Map<String, Object> context) {
        super("REGISTRY_ERROR", message, cause, context);
    }

    public static RegistryException alreadyRegistered(String registryName, String id) {
        return new RegistryException(
            "Item already registered",
            ExceptionContext.builder()
                .with("registry", registryName)
                .with("id", id)
                .build()
                .asMap()
        );
    }

    public static RegistryException notFound(String registryName, String id) {
        return new RegistryException(
            "Item not found in registry",
            ExceptionContext.builder()
                .with("registry", registryName)
                .with("id", id)
                .build()
                .asMap()
        );
    }

    public static RegistryException registrationFailed(String registryName, String id, Throwable cause) {
        return new RegistryException(
            "Failed to register item",
            cause,
            ExceptionContext.builder()
                .with("registry", registryName)
                .with("id", id)
                .build()
                .asMap()
        );
    }
}

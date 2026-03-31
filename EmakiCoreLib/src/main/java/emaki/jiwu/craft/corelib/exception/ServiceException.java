package emaki.jiuwu.craft.corelib.exception;

import java.util.Map;

public final class ServiceException extends FrameworkException {

    public ServiceException(String message) {
        super("SERVICE_ERROR", message);
    }

    public ServiceException(String message, Map<String, Object> context) {
        super("SERVICE_ERROR", message, context);
    }

    public ServiceException(String message, Throwable cause) {
        super("SERVICE_ERROR", message, cause);
    }

    public ServiceException(String message, Throwable cause, Map<String, Object> context) {
        super("SERVICE_ERROR", message, cause, context);
    }

    public static ServiceException notInitialized(String serviceName) {
        return new ServiceException(
            "Service not initialized",
            ExceptionContext.of("service", serviceName).asMap()
        );
    }

    public static ServiceException operationFailed(String serviceName, String operation, Throwable cause) {
        return new ServiceException(
            "Service operation failed",
            cause,
            ExceptionContext.builder()
                .with("service", serviceName)
                .with("operation", operation)
                .build()
                .asMap()
        );
    }

    public static ServiceException invalidState(String serviceName, String expectedState, String actualState) {
        return new ServiceException(
            "Invalid service state",
            ExceptionContext.builder()
                .with("service", serviceName)
                .with("expected", expectedState)
                .with("actual", actualState)
                .build()
                .asMap()
        );
    }
}

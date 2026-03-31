package emaki.jiuwu.craft.corelib.exception;

import java.util.Map;

public class FrameworkException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> context;

    public FrameworkException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = Map.of();
    }

    public FrameworkException(String errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context == null ? Map.of() : Map.copyOf(context);
    }

    public FrameworkException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = Map.of();
    }

    public FrameworkException(String errorCode, String message, Throwable cause, Map<String, Object> context) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context == null ? Map.of() : Map.copyOf(context);
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }

    public String contextAsString() {
        if (context.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        String result = "[" + errorCode + "] " + getMessage();
        if (!context.isEmpty()) {
            result += " {" + contextAsString() + "}";
        }
        if (getCause() != null) {
            result += " (caused by: " + getCause().getClass().getSimpleName() + ": " + getCause().getMessage() + ")";
        }
        return result;
    }
}

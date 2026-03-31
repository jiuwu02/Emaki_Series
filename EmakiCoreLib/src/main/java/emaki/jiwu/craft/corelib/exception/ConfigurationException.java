package emaki.jiuwu.craft.corelib.exception;

import java.util.Map;

public final class ConfigurationException extends FrameworkException {

    public ConfigurationException(String message) {
        super("CONFIG_ERROR", message);
    }

    public ConfigurationException(String message, Map<String, Object> context) {
        super("CONFIG_ERROR", message, context);
    }

    public ConfigurationException(String message, Throwable cause) {
        super("CONFIG_ERROR", message, cause);
    }

    public ConfigurationException(String message, Throwable cause, Map<String, Object> context) {
        super("CONFIG_ERROR", message, cause, context);
    }

    public static ConfigurationException missingRequired(String configPath) {
        return new ConfigurationException(
            "Missing required configuration",
            ExceptionContext.of("path", configPath).asMap()
        );
    }

    public static ConfigurationException invalidValue(String configPath, String value, String expected) {
        return new ConfigurationException(
            "Invalid configuration value",
            ExceptionContext.builder()
                .with("path", configPath)
                .with("value", value)
                .with("expected", expected)
                .build()
                .asMap()
        );
    }

    public static ConfigurationException fileNotFound(String fileName) {
        return new ConfigurationException(
            "Configuration file not found",
            ExceptionContext.of("file", fileName).asMap()
        );
    }

    public static ConfigurationException parseError(String fileName, Throwable cause) {
        return new ConfigurationException(
            "Failed to parse configuration file",
            cause,
            ExceptionContext.of("file", fileName).asMap()
        );
    }
}

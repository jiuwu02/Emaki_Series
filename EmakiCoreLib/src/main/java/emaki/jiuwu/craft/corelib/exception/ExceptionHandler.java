package emaki.jiuwu.craft.corelib.exception;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class ExceptionHandler {

    private final Plugin plugin;
    private final Logger logger;
    private final LogMessages messages;

    public ExceptionHandler(Plugin plugin, LogMessages messages) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messages = messages;
    }

    public void handle(FrameworkException exception) {
        handle(exception, Level.WARNING);
    }

    public void handle(FrameworkException exception, Level level) {
        logException(exception, level);
        notifyUser(exception);
    }

    public void handleUnexpected(Exception exception, String operation) {
        FrameworkException wrapped = new FrameworkException(
            "UNEXPECTED_ERROR",
            "Unexpected error during " + operation,
            exception,
            ExceptionContext.of("operation", operation).asMap()
        );
        handle(wrapped, Level.SEVERE);
    }

    private void logException(FrameworkException exception, Level level) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(exception.errorCode()).append(": ").append(exception.getMessage());
        if (!exception.context().isEmpty()) {
            logMessage.append(" | Context: ").append(exception.contextAsString());
        }
        if (exception.getCause() != null) {
            logger.log(level, logMessage.toString(), exception.getCause());
        } else {
            logger.log(level, logMessage.toString());
        }
    }

    private void notifyUser(FrameworkException exception) {
        if (messages == null) {
            return;
        }
        String messageKey = "error." + exception.errorCode().toLowerCase().replace("_", ".");
        String userMessage = messages.get(messageKey, toPlaceholderMap(exception.context()));
        if (Texts.isBlank(userMessage) || userMessage.equals(messageKey)) {
            userMessage = messages.get("error.generic", toPlaceholderMap(exception.context()));
        }
        if (!Texts.isBlank(userMessage)) {
            plugin.getServer().getConsoleSender().sendMessage(userMessage);
        }
    }

    private Map<String, String> toPlaceholderMap(Map<String, Object> context) {
        return context.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> String.valueOf(e.getValue())
            ));
    }

    public static ExceptionHandler create(Plugin plugin, LogMessages messages) {
        return new ExceptionHandler(plugin, messages);
    }
}

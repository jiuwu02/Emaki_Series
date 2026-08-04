package emaki.jiuwu.craft.corelib.config.precheck;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

public abstract class AbstractModuleConfigPrecheckContributor implements ConfigPrecheckContributor {

    private static final String MESSAGE_PREFIX = "console.config_precheck.messages.";

    private final String module;
    private final Supplier<? extends LogMessages> messagesSupplier;

    protected AbstractModuleConfigPrecheckContributor(String module) {
        this(module, () -> null);
    }

    protected AbstractModuleConfigPrecheckContributor(String module, Supplier<? extends LogMessages> messagesSupplier) {
        this.module = Texts.lower(Objects.requireNonNull(module, "module"));
        this.messagesSupplier = messagesSupplier == null ? () -> null : messagesSupplier;
    }

    @Override
    public final String module() {
        return module;
    }

    protected final String message(String key) {
        return message(key, Map.of());
    }

    protected final String message(String key, Map<String, ?> replacements) {
        String messageKey = MESSAGE_PREFIX + Texts.toStringSafe(key);
        LogMessages messages = messagesSupplier.get();
        if (messages == null) {
            return messageKey;
        }
        return messages.message(messageKey, replacements == null ? Map.of() : replacements);
    }

    protected final void checkFile(File file, String path, List<ConfigPrecheckIssue> issues) {
        if (file == null || !file.exists()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "required_file_missing", issues);
            return;
        }
        if (!file.isFile()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "path_not_file", issues);
            return;
        }
        if (!file.canRead()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "file_not_readable", issues);
        }
    }

    protected final void checkDirectory(File directory, String path, List<ConfigPrecheckIssue> issues) {
        checkDirectory(
                directory,
                path,
                message("required_directory_missing"),
                message("directory_not_readable"),
                issues
        );
    }

    protected final void checkDirectory(File directory,
            String path,
            String missingMessage,
            String unreadableMessage,
            List<ConfigPrecheckIssue> issues) {
        if (directory == null || !directory.exists()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, missingMessage, issues);
            return;
        }
        if (!directory.isDirectory()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "path_not_directory", issues);
            return;
        }
        if (!directory.canRead()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, unreadableMessage, issues);
        }
    }

    protected final void addLoaderIssues(String path, List<String> loaderIssues, List<ConfigPrecheckIssue> issues) {
        if (loaderIssues == null || loaderIssues.isEmpty()) {
            return;
        }
        for (String issue : loaderIssues) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, issue, issues);
        }
    }

    protected final void addSuccessIssue(List<ConfigPrecheckIssue> issues, String path, String message) {
        addIssue(path, ConfigPrecheckSeverity.INFO, message, issues);
    }

    protected final void addMessageIssue(String path,
            ConfigPrecheckSeverity severity,
            String key,
            List<ConfigPrecheckIssue> issues) {
        addMessageIssue(path, severity, key, Map.of(), issues);
    }

    protected final void addMessageIssue(String path,
            ConfigPrecheckSeverity severity,
            String key,
            Map<String, ?> replacements,
            List<ConfigPrecheckIssue> issues) {
        addIssue(path, severity, message(key, replacements), issues);
    }

    protected final void addIssue(String path,
            ConfigPrecheckSeverity severity,
            String message,
            List<ConfigPrecheckIssue> issues) {
        if (issues == null) {
            return;
        }
        issues.add(ConfigPrecheckIssue.of(module(), path, severity, message));
    }
}

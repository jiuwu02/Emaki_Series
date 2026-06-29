package emaki.jiuwu.craft.corelib.config.precheck;

import java.io.File;
import java.util.List;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.text.Texts;

public abstract class AbstractModuleConfigPrecheckContributor implements ConfigPrecheckContributor {

    private static final String REQUIRED_FILE_MISSING = "Required file does not exist.";
    private static final String PATH_NOT_FILE = "Path is not a file.";
    private static final String FILE_NOT_READABLE = "File is not readable.";
    private static final String REQUIRED_DIRECTORY_MISSING = "Required directory does not exist.";
    private static final String PATH_NOT_DIRECTORY = "Path is not a directory.";
    private static final String DIRECTORY_NOT_READABLE = "Directory is not readable.";

    private final String module;

    protected AbstractModuleConfigPrecheckContributor(String module) {
        this.module = Texts.lower(Objects.requireNonNull(module, "module"));
    }

    @Override
    public final String module() {
        return module;
    }

    protected final void checkFile(File file, String path, List<ConfigPrecheckIssue> issues) {
        if (file == null || !file.exists()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, REQUIRED_FILE_MISSING, issues);
            return;
        }
        if (!file.isFile()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, PATH_NOT_FILE, issues);
            return;
        }
        if (!file.canRead()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, FILE_NOT_READABLE, issues);
        }
    }

    protected final void checkDirectory(File directory, String path, List<ConfigPrecheckIssue> issues) {
        checkDirectory(directory, path, REQUIRED_DIRECTORY_MISSING, DIRECTORY_NOT_READABLE, issues);
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
            addIssue(path, ConfigPrecheckSeverity.ERROR, PATH_NOT_DIRECTORY, issues);
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

    protected final void addIssue(String path, ConfigPrecheckSeverity severity, String message, List<ConfigPrecheckIssue> issues) {
        if (issues == null) {
            return;
        }
        issues.add(ConfigPrecheckIssue.of(module(), path, severity, message));
    }
}

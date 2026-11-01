package emaki.jiuwu.craft.corelib.config.precheck;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.DiagnosticRenderer;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public abstract class AbstractModuleConfigPrecheckContributor implements ConfigPrecheckContributor {

    private static final String MESSAGE_PREFIX = "console.config_precheck.messages.";

    private final String module;
    private final Supplier<? extends LogMessages> messagesSupplier;
    private @Nullable JavaPlugin owningPlugin;
    private boolean owningPluginResolved;

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

    @Override
    public boolean supportsFix() {
        return true;
    }

    @Override
    public boolean applyFix(ConfigPrecheckFixAction fix) {
        JavaPlugin plugin = owningPlugin();
        try {
            return switch (fix.kind()) {
                case CREATE_DIRECTORY -> YamlFiles.ensureDirectory(fix.target().toPath());
                case COPY_RESOURCE -> plugin != null
                        && YamlFiles.copyResourceIfMissing(plugin, fix.resource(), fix.target());
                case MERGE_KEYS -> plugin != null
                        && YamlFiles.mergeMissingKeys(plugin, fix.target(), fix.resource()) > 0;
            };
        } catch (IOException | RuntimeException exception) {
            LogMessages messages = messagesSupplier.get();
            if (messages != null) {
                messages.warning(MESSAGE_PREFIX + "fix_failed", Map.of(
                        "path", fix.target().getPath(),
                        "kind", fix.kind().name(),
                        "error", Texts.toStringSafe(exception.getMessage())));
            } else if (plugin != null) {
                plugin.getLogger().warning("[config] 自动修复 " + fix.target().getPath()
                        + " 失败: " + exception.getMessage());
            }
            return false;
        }
    }

    protected final @Nullable JavaPlugin owningPlugin() {
        if (!owningPluginResolved) {
            owningPluginResolved = true;
            try {
                owningPlugin = JavaPlugin.getProvidingPlugin(getClass());
            } catch (IllegalArgumentException | IllegalStateException | LinkageError _) {
                owningPlugin = null;
            }
        }
        return owningPlugin;
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

    protected final String renderDiagnostic(CompileDiagnostic diagnostic) {
        if (diagnostic == null) {
            return "";
        }
        LogMessages messages = messagesSupplier.get();
        if (messages == null) {
            return diagnostic.toString();
        }
        return new DiagnosticRenderer((key, replacements, fallback) -> {
            String resolved = messages.message(key, replacements == null ? Map.of() : replacements);
            return Texts.isBlank(resolved) || key.equals(resolved) ? fallback : resolved;
        }).render(diagnostic);
    }

    protected final void checkFile(File file, String path, List<ConfigPrecheckIssue> issues) {
        if (file == null) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "required_file_missing", issues);
            return;
        }
        if (!file.exists()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, message("required_file_missing"),
                    new ConfigPrecheckFixAction(ConfigPrecheckFixAction.Kind.COPY_RESOURCE, file, path), issues);
            return;
        }
        if (!file.isFile()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "path_not_file", issues);
            return;
        }
        if (!file.canRead()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "file_not_readable", issues);
            return;
        }
        int missing = countMissingConfigKeys(file, path);
        if (missing > 0) {
            addIssue(path, ConfigPrecheckSeverity.WARN,
                    message("config_keys_missing", Map.of("count", missing)),
                    new ConfigPrecheckFixAction(ConfigPrecheckFixAction.Kind.MERGE_KEYS, file, path), issues);
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
        if (directory == null) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, missingMessage, null, issues);
            return;
        }
        if (!directory.exists()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, missingMessage,
                    new ConfigPrecheckFixAction(ConfigPrecheckFixAction.Kind.CREATE_DIRECTORY, directory, path),
                    issues);
            return;
        }
        if (!directory.isDirectory()) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "path_not_directory", issues);
            return;
        }
        if (!directory.canRead()) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, unreadableMessage, null, issues);
        }
    }

    protected final void addLoaderIssues(String path, List<String> loaderIssues, List<ConfigPrecheckIssue> issues) {
        if (loaderIssues == null || loaderIssues.isEmpty()) {
            return;
        }
        for (String issue : loaderIssues) {
            addIssue(path, ConfigPrecheckSeverity.ERROR, issue, null, issues);
        }
    }

    protected final void addSuccessIssue(List<ConfigPrecheckIssue> issues, String path, String message) {
        addIssue(path, ConfigPrecheckSeverity.INFO, message, null, issues);
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
        addIssue(path, severity, message(key, replacements), null, issues);
    }

    protected final void addIssue(String path,
            ConfigPrecheckSeverity severity,
            String message,
            List<ConfigPrecheckIssue> issues) {
        addIssue(path, severity, message, null, issues);
    }

    private void addIssue(String path,
            ConfigPrecheckSeverity severity,
            String message,
            @Nullable ConfigPrecheckFixAction fix,
            List<ConfigPrecheckIssue> issues) {
        if (issues == null) {
            return;
        }
        if (fix == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, severity, message));
            return;
        }
        issues.add(new ConfigPrecheckIssue(module(), path, severity, message,
                message("fixable_hint"), fix));
    }

    private int countMissingConfigKeys(File file, String resourcePath) {
        JavaPlugin plugin = owningPlugin();
        if (plugin == null) {
            return 0;
        }
        try {
            YamlSection runtime = YamlFiles.load(file);
            YamlSection defaults = YamlFiles.loadResource(plugin, resourcePath);
            return YamlFiles.countMissingKeys(runtime, defaults);
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("[config] 跳过缺键检查 " + resourcePath + ": " + exception.getMessage());
            return 0;
        }
    }
}

package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class YamlConfigLoader<T> {

    private final JavaPlugin plugin;
    private final String relativePath;
    private final String resourcePath;
    private final String versionKey;
    private final Supplier<T> defaultsSupplier;
    private final Function<YamlSection, T> parser;
    private final String loadFailureKey;
    private volatile T current;

    public YamlConfigLoader(JavaPlugin plugin,
            String relativePath,
            String versionKey,
            Supplier<T> defaultsSupplier,
            Function<YamlSection, T> parser) {
        this(plugin, relativePath, relativePath, versionKey, defaultsSupplier, parser, "console.loader_config_load_error");
    }

    public YamlConfigLoader(JavaPlugin plugin,
            String relativePath,
            String resourcePath,
            String versionKey,
            Supplier<T> defaultsSupplier,
            Function<YamlSection, T> parser,
            String loadFailureKey) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.relativePath = Texts.isBlank(relativePath) ? "config.yml" : relativePath;
        this.resourcePath = Texts.isBlank(resourcePath) ? this.relativePath : resourcePath;
        this.versionKey = Texts.toStringSafe(versionKey);
        this.defaultsSupplier = defaultsSupplier == null ? () -> null : defaultsSupplier;
        this.parser = Objects.requireNonNull(parser, "parser");
        this.loadFailureKey = Texts.isBlank(loadFailureKey) ? "console.loader_config_load_error" : loadFailureKey;
        this.current = this.defaultsSupplier.get();
    }

    public T load() {
        try {
            File file = file();
            YamlSection configuration;
            if (Texts.isBlank(versionKey)) {
                YamlFiles.copyResourceIfMissing(plugin, resourcePath, file);
                configuration = YamlFiles.load(file);
            } else {
                VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(plugin, file, resourcePath, versionKey);
                configuration = versionedFile == null ? null : versionedFile.root();
            }
            if (configuration == null || configuration.isEmpty()) {
                current = defaultsSupplier.get();
                return current;
            }
            current = parser.apply(configuration);
        } catch (Exception exception) {
            warning(loadFailureKey, Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
            current = defaultsSupplier.get();
        }
        return current;
    }

    public T current() {
        return current;
    }

    public void overrideCurrent(T current) {
        this.current = current;
    }

    public File file() {
        return plugin.getDataFolder().toPath().resolve(Path.of(relativePath)).toFile();
    }

    private void warning(String key, Map<String, ?> replacements) {
        LogMessages messages = messages();
        if (messages != null) {
            messages.warning(key, replacements);
        }
    }

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }
}

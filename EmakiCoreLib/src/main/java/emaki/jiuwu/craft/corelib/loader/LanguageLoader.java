package emaki.jiuwu.craft.corelib.loader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class LanguageLoader {

    private final JavaPlugin plugin;
    private final String languageDirectory;
    private final String bundledDirectory;
    private final Map<String, YamlSection> languages = new LinkedHashMap<>();
    private final YamlSection bundledFallback;
    private String currentLanguage;
    private final String fallbackLanguage;

    public LanguageLoader(JavaPlugin plugin) {
        this(plugin, "lang", "lang", "zh_CN", "zh_CN");
    }

    public LanguageLoader(JavaPlugin plugin,
            String languageDirectory,
            String bundledDirectory,
            String currentLanguage,
            String fallbackLanguage) {
        this.plugin = plugin;
        this.languageDirectory = normalizeDirectory(languageDirectory, "lang");
        this.bundledDirectory = normalizeDirectory(bundledDirectory, "lang");
        this.currentLanguage = Texts.isBlank(currentLanguage) ? "zh_CN" : currentLanguage;
        this.fallbackLanguage = Texts.isBlank(fallbackLanguage) ? "zh_CN" : fallbackLanguage;
        this.bundledFallback = loadBundledFallbackLanguage();
        if (bundledFallback != null) {
            languages.put(this.fallbackLanguage, bundledFallback);
        }
    }

    public synchronized int load() {
        languages.clear();
        if (bundledFallback != null) {
            languages.put(fallbackLanguage, bundledFallback);
        }
        File directory = dataPath(languageDirectory).toFile();
        if (!directory.exists()) {
            try {
                YamlFiles.ensureDirectory(directory.toPath());
            } catch (IOException exception) {
                warning("loader.lang_directory_create_failed", Map.of("path", directory.getPath()));
            }
        }
        Set<String> loggedUpdates = new LinkedHashSet<>();
        File fallbackFile = dataPath(languageDirectory, fallbackLanguage + ".yml").toFile();
        try {
            syncVersionedLanguage(fallbackFile, bundledPath(fallbackLanguage), loggedUpdates);
            syncBundledLanguages(loggedUpdates);
        } catch (IOException exception) {
            warning("loader.bundled_language_load_failed", Map.of("error", Texts.toStringSafe(exception.getMessage())));
        }
        if (!fallbackFile.exists()) {
            warning("loader.bundled_resource_missing", Map.of(
                    "type", "语言",
                    "path", fallbackFile.getPath(),
                    "resource", bundledPath(fallbackLanguage)
            ));
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            String langId = file.getName().replace(".yml", "").replace(".yaml", "");
            try {
                VersionedYamlFile versionedFile = syncVersionedLanguage(file, bundledPath(langId), loggedUpdates);
                YamlSection loaded = versionedFile == null || versionedFile.root() == null
                        ? YamlFiles.load(file)
                        : versionedFile.root().copy();
                if (bundledFallback != null && fallbackLanguage.equals(langId)) {
                    YamlFiles.mergeMissingValues(loaded, bundledFallback);
                }
                languages.put(langId, loaded);
            } catch (IOException exception) {
                warning("loader.bundled_language_load_failed", Map.of("error", Texts.toStringSafe(exception.getMessage())));
                languages.put(langId, YamlFiles.load(file));
            }
            if (!file.exists()) {
                warning("loader.bundled_resource_missing", Map.of(
                        "type", "语言",
                        "path", file.getPath(),
                        "resource", bundledPath(langId)
                ));
            }
        }
        return languages.size();
    }

    private void syncBundledLanguages(Set<String> loggedUpdates) throws IOException {
        for (String bundledResource : YamlFiles.listResourcePaths(plugin, bundledDirectory)) {
            String fileName = Path.of(bundledResource).getFileName().toString();
            if (Texts.isBlank(fileName)) {
                continue;
            }
            File target = dataPath(languageDirectory, fileName).toFile();
            syncVersionedLanguage(target, bundledResource, loggedUpdates);
        }
    }

    private VersionedYamlFile syncVersionedLanguage(File target, String bundledResource, Set<String> loggedUpdates) throws IOException {
        VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(plugin, target, bundledResource, "version");
        logVersionUpdate(target, versionedFile, loggedUpdates);
        return versionedFile;
    }

    private void logVersionUpdate(File target, VersionedYamlFile versionedFile, Set<String> loggedUpdates) {
        if (target == null || versionedFile == null || !versionedFile.versionUpdated()) {
            return;
        }
        String key = target.getAbsolutePath();
        if (loggedUpdates != null && !loggedUpdates.add(key)) {
            return;
        }
        LogMessages messages = messages();
        if (messages != null) {
            messages.info("console.versioned_file_updated", Map.of(
                    "path", languageDirectory + "/" + target.getName(),
                    "old_version", versionedFile.previousVersion().isBlank() ? "unknown" : versionedFile.previousVersion(),
                    "new_version", versionedFile.updatedVersion()
            ));
        }
    }

    public synchronized boolean setLanguage(String language) {
        if (Texts.isBlank(language) || !languages.containsKey(language)) {
            return false;
        }
        currentLanguage = language;
        return true;
    }

    public synchronized Object getValue(String key) {
        Object value = getNestedValue(currentLanguage, key);
        if (value == null && !currentLanguage.equals(fallbackLanguage)) {
            value = getNestedValue(fallbackLanguage, key);
        }
        return value;
    }

    public synchronized String getMessage(String key) {
        return getMessage(key, Map.of());
    }

    public synchronized String getMessage(String key, Map<String, ?> replacements) {
        Object value = getValue(key);
        if (value == null) {
            return key;
        }
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        return value instanceof String text
                ? Texts.formatTemplate(text, safeReplacements)
                : ExpressionEngine.evaluateStringConfig(value, safeReplacements);
    }

    public synchronized YamlSection getSection(String key) {
        Object value = getValue(key);
        if (value instanceof Map<?, ?> map) {
            return new MapYamlSection(MapYamlSection.normalizeMap(map));
        }
        return value instanceof YamlSection section ? section : null;
    }

    public synchronized String currentLanguage() {
        return currentLanguage;
    }

    private YamlSection loadBundledFallbackLanguage() {
        VersionedYamlFile versionedFile = YamlFiles.loadVersionedResource(plugin, bundledPath(fallbackLanguage));
        return versionedFile == null || versionedFile.root() == null ? null : versionedFile.root().copy();
    }

    private Object getNestedValue(String language, String dottedPath) {
        YamlSection configuration = languages.get(language);
        if (configuration == null) {
            return null;
        }
        Object direct = configuration.get(dottedPath);
        if (direct != null) {
            return direct;
        }
        return configuration.getSection(dottedPath);
    }

    private Path dataPath(String first, String... more) {
        return plugin.getDataFolder().toPath().resolve(Path.of(first, more));
    }

    private String bundledPath(String language) {
        return bundledDirectory + "/" + language + ".yml";
    }

    private String normalizeDirectory(String value, String fallback) {
        String normalized = Texts.toStringSafe(value).trim().replace('\\', '/');
        if (normalized.isBlank()) {
            return fallback;
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? fallback : normalized;
    }

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }

    private void warning(String key, Map<String, ?> replacements) {
        LogMessages messages = messages();
        if (messages != null) {
            messages.warning(key, replacements);
        }
    }
}

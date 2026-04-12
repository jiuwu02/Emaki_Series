package emaki.jiuwu.craft.attribute.loader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class LanguageLoader {

    private final EmakiAttributePlugin plugin;
    private final Map<String, YamlSection> languages = new LinkedHashMap<>();
    private final YamlSection bundledFallback;
    private String currentLanguage = "zh_CN";
    private String fallbackLanguage = "zh_CN";

    public LanguageLoader(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
        this.bundledFallback = loadBundledFallbackLanguage();
        if (bundledFallback != null) {
            languages.put(fallbackLanguage, bundledFallback);
        }
    }

    public synchronized int load() {
        languages.clear();
        if (bundledFallback != null) {
            languages.put(fallbackLanguage, bundledFallback);
        }
        File directory = plugin.dataPath("lang").toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.messageService().warning("loader.lang_directory_create_failed", Map.of("path", directory.getPath()));
        }
        File fallbackFile = plugin.dataPath("lang", fallbackLanguage + ".yml").toFile();
        try {
            YamlFiles.syncVersionedResource(plugin, fallbackFile, "lang/" + fallbackLanguage + ".yml", "lang_version");
        } catch (IOException exception) {
            plugin.messageService().warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
        }
        if (!fallbackFile.exists()) {
            plugin.messageService().warning("loader.bundled_resource_missing", Map.of(
                    "type", "语言",
                    "path", fallbackFile.getPath(),
                    "resource", "lang/" + fallbackLanguage + ".yml"
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
                VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(plugin, file, "lang/" + langId + ".yml", "lang_version");
                YamlSection loaded = versionedFile == null || versionedFile.root() == null
                        ? YamlFiles.load(file)
                        : versionedFile.root().copy();
                if (bundledFallback != null && fallbackLanguage.equals(langId)) {
                    YamlFiles.mergeMissingValues(loaded, bundledFallback);
                }
                languages.put(langId, loaded);
            } catch (IOException exception) {
                plugin.messageService().warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
                languages.put(langId, YamlFiles.load(file));
            }
            if (!file.exists()) {
                plugin.messageService().warning("loader.bundled_resource_missing", Map.of(
                        "type", "语言",
                        "path", file.getPath(),
                        "resource", "lang/" + langId + ".yml"
                ));
            }
        }
        return languages.size();
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
        Object value = getValue(key);
        return value == null ? key : Texts.toStringSafe(value);
    }

    public synchronized String getMessage(String key, Map<String, ?> replacements) {
        return Texts.formatTemplate(getMessage(key), replacements);
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
        VersionedYamlFile versionedFile = YamlFiles.loadVersionedResource(plugin, "lang/zh_CN.yml");
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
}

package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class LanguageLoader {

    private final EmakiForgePlugin plugin;
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<>();
    private String currentLanguage = "zh_CN";
    private String fallbackLanguage = "zh_CN";
    private final YamlConfiguration bundledFallback;

    public LanguageLoader(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.bundledFallback = YamlFiles.loadResource(plugin, "lang/zh_CN.yml");
        if (bundledFallback != null) {
            languages.put(fallbackLanguage, bundledFallback);
        }
    }

    public int load() {
        languages.clear();
        if (bundledFallback != null) {
            languages.put(fallbackLanguage, bundledFallback);
        }
        File directory = plugin.dataPath("lang").toFile();
        if (!directory.exists()) {
            try {
                YamlFiles.ensureDirectory(directory.toPath());
            } catch (IOException exception) {
                plugin.messageService().warning("loader.lang_directory_create_failed", Map.of("path", directory.getPath()));
            }
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            String langId = file.getName().replace(".yml", "").replace(".yaml", "");
            try {
                YamlFiles.syncVersionedResource(plugin, file, "lang/" + langId + ".yml", "lang_version");
            } catch (IOException exception) {
                plugin.messageService().warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
            }
            languages.put(langId, YamlFiles.load(file));
        }
        return languages.size();
    }

    public boolean setLanguage(String language) {
        if (!languages.containsKey(language)) {
            return false;
        }
        currentLanguage = language;
        return true;
    }

    public Object getValue(String key) {
        Object value = getNestedValue(currentLanguage, key);
        if (value == null && !currentLanguage.equals(fallbackLanguage)) {
            value = getNestedValue(fallbackLanguage, key);
        }
        return value;
    }

    public String getMessage(String key) {
        Object value = getValue(key);
        return value == null ? key : Texts.toStringSafe(value);
    }

    public String getMessage(String key, Map<String, ?> replacements) {
        return Texts.formatTemplate(getMessage(key), replacements);
    }

    public ConfigurationSection getSection(String key) {
        Object value = getValue(key);
        return value instanceof ConfigurationSection section ? section : null;
    }

    public String currentLanguage() {
        return currentLanguage;
    }

    private Object getNestedValue(String language, String dottedPath) {
        YamlConfiguration configuration = languages.get(language);
        if (configuration == null) {
            return null;
        }
        String[] keys = dottedPath.split("\\.");
        Object current = configuration;
        for (String key : keys) {
            if (current instanceof ConfigurationSection section) {
                current = section.get(key);
                continue;
            }
            return null;
        }
        if (current != null) {
            return current;
        }
        return configuration.getValues(false).get(dottedPath);
    }
}

package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LanguageLoader {

    private final EmakiAttributePlugin plugin;
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<>();
    private final YamlConfiguration bundledFallback;
    private String currentLanguage = "zh_CN";
    private String fallbackLanguage = "zh_CN";

    public LanguageLoader(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
        this.bundledFallback = loadBundledFallbackLanguage();
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
        if (!directory.exists() && !directory.mkdirs()) {
            MessageService messages = plugin.messageService();
            if (messages != null) {
                messages.warning("loader.lang_directory_create_failed", Map.of("path", directory.getPath()));
            } else {
                plugin.getLogger().warning("loader.lang_directory_create_failed");
            }
        }
        File fallbackFile = plugin.dataPath("lang", fallbackLanguage + ".yml").toFile();
        try {
            YamlFiles.syncVersionedResource(plugin, fallbackFile, "lang/" + fallbackLanguage + ".yml", "lang_version");
        } catch (IOException exception) {
            MessageService messages = plugin.messageService();
            if (messages != null) {
                messages.warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
            } else {
                plugin.getLogger().warning("loader.bundled_language_load_failed");
            }
        }
        if (!fallbackFile.exists()) {
            MessageService messages = plugin.messageService();
            if (messages != null) {
                messages.warning("loader.bundled_resource_missing", Map.of(
                    "type", "语言",
                    "path", fallbackFile.getPath(),
                    "resource", "lang/" + fallbackLanguage + ".yml"
                ));
            } else {
                plugin.getLogger().warning("Missing bundled resource lang/" + fallbackLanguage + ".yml");
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
                MessageService messages = plugin.messageService();
                if (messages != null) {
                    messages.warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
                } else {
                    plugin.getLogger().warning("loader.bundled_language_load_failed");
                }
            }
            if (!file.exists()) {
                MessageService messages = plugin.messageService();
                if (messages != null) {
                    messages.warning("loader.bundled_resource_missing", Map.of(
                        "type", "语言",
                        "path", file.getPath(),
                        "resource", "lang/" + langId + ".yml"
                    ));
                } else {
                    plugin.getLogger().warning("Missing bundled resource lang/" + langId + ".yml");
                }
            }
            languages.put(langId, YamlFiles.load(file));
        }
        return languages.size();
    }

    public boolean setLanguage(String language) {
        if (Texts.isBlank(language) || !languages.containsKey(language)) {
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

    private YamlConfiguration loadBundledFallbackLanguage() {
        try (InputStream inputStream = plugin.getResource("lang/zh_CN.yml")) {
            if (inputStream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            MessageService messages = plugin.messageService();
            if (messages != null) {
                messages.warning("loader.bundled_language_load_failed", Map.of("error", String.valueOf(exception.getMessage())));
            } else {
                plugin.getLogger().warning("loader.bundled_language_load_failed");
            }
            return null;
        }
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

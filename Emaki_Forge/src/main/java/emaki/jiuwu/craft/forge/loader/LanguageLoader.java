package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
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

    private final EmakiForgePlugin plugin;
    private final Map<String, YamlConfiguration> languages = new LinkedHashMap<>();
    private String currentLanguage = "zh_CN";
    private String fallbackLanguage = "zh_CN";
    private final YamlConfiguration bundledFallback;

    public LanguageLoader(EmakiForgePlugin plugin) {
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
            plugin.getLogger().warning("Failed to create lang directory: " + directory.getPath());
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return 0;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            String langId = file.getName().replace(".yml", "").replace(".yaml", "");
            languages.put(langId, YamlConfiguration.loadConfiguration(file));
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

    private YamlConfiguration loadBundledFallbackLanguage() {
        try (InputStream inputStream = plugin.getResource("defaults/lang/zh_CN.yml")) {
            if (inputStream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to load bundled fallback language: " + exception.getMessage());
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
        return current;
    }
}

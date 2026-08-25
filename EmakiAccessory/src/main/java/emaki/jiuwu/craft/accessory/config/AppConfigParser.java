package emaki.jiuwu.craft.accessory.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class AppConfigParser {

    private AppConfigParser() {
    }

    public static AppConfig parse(YamlSection section) {
        AppConfig defaults = AppConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig(
                section.getString("version", defaults.version()),
                section.getString("language", defaults.language()),
                section.getBoolean("release_default_data", defaults.releaseDefaultData()),
                section.getBoolean("drop_on_death", defaults.dropOnDeath()),
                section.getBoolean("unique", defaults.unique()),
                section.getInt("persistence.autosave_seconds", defaults.autosaveSeconds()),
                section.getInt("persistence.drain_timeout_seconds", defaults.drainTimeoutSeconds()),
                parseSlotSources(section.getSection("slot_sources"))
        );
    }

    private static AccessorySlotSourceConfig parseSlotSources(YamlSection section) {
        if (section == null) {
            return AccessorySlotSourceConfig.defaults();
        }
        YamlSection pdc = section.getSection("pdc");
        YamlSection lore = section.getSection("lore");
        boolean pdcEnabled = pdc != null && Boolean.TRUE.equals(pdc.getBoolean("enabled", Boolean.FALSE));
        String pdcKey = pdc == null ? "" : pdc.getString("key", "");
        boolean loreEnabled = lore != null && Boolean.TRUE.equals(lore.getBoolean("enabled", Boolean.FALSE));
        List<String> patterns = lore == null ? List.of() : lore.getStringList("patterns");
        String separator = lore == null
                ? AccessorySlotSourceConfig.DEFAULT_SEPARATOR
                : lore.getString("separator", AccessorySlotSourceConfig.DEFAULT_SEPARATOR);
        return AccessorySlotSourceConfig.of(
                pdcEnabled, pdcKey, loreEnabled, patterns, separator,
                parseAliases(lore == null ? null : lore.getSection("aliases")));
    }

    private static Map<String, String> parseAliases(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String target = Texts.normalizeId(section.getString(key, ""));
            if (Texts.isNotBlank(key) && Texts.isNotBlank(target)) {
                aliases.put(Texts.trim(key), target);
            }
        }
        return aliases;
    }
}

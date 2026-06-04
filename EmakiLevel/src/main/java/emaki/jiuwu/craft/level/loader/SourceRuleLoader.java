package emaki.jiuwu.craft.level.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;

public final class SourceRuleLoader {

    private final JavaPlugin plugin;
    private List<SourceRuleConfig> rules = List.of();

    public SourceRuleLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File directory = plugin.getDataFolder().toPath().resolve("sources").toFile();
        List<SourceRuleConfig> loaded = new ArrayList<>();
        File[] files = directory.listFiles((_, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlSection root = YamlFiles.load(file);
                if (!root.getBoolean("enabled", true)) {
                    continue;
                }
                YamlSection sources = root.getSection("sources");
                if (sources == null) {
                    continue;
                }
                for (String key : sources.getKeys(false)) {
                    Object raw = sources.get(key);
                    if (raw instanceof java.util.Map<?, ?> map) {
                        SourceRuleConfig rule = SourceRuleConfig.parse(Texts.normalizeId(key), map);
                        if (rule.enabled()) {
                            loaded.add(rule);
                        }
                    }
                }
            }
        }
        rules = List.copyOf(loaded);
    }

    public List<SourceRuleConfig> rules() {
        return rules;
    }

    public List<SourceRuleConfig> byTrigger(String trigger) {
        String normalized = Texts.normalizeId(trigger);
        return rules.stream()
                .filter(rule -> normalized.equals(rule.trigger()))
                .toList();
    }
}

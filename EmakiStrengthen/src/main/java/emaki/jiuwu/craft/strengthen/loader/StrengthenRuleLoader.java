package emaki.jiuwu.craft.strengthen.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRule;

public final class StrengthenRuleLoader extends YamlDirectoryLoader<StrengthenRule> {

    public StrengthenRuleLoader(EmakiStrengthenPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "rules";
    }

    @Override
    protected String typeName() {
        return "strengthen-rule";
    }

    @Override
    protected StrengthenRule parse(File file, YamlConfiguration configuration) {
        return StrengthenRule.fromConfig(configuration);
    }

    @Override
    protected String idOf(StrengthenRule value) {
        return value.id();
    }

    public List<StrengthenRule> ordered() {
        synchronized (stateLock) {
            List<StrengthenRule> ordered = new ArrayList<>();
            for (LoadedYamlEntry<StrengthenRule> entry : loadedEntries.values()) {
                if (entry != null && entry.value() != null) {
                    ordered.add(entry.value());
                }
            }
            return List.copyOf(ordered);
        }
    }
}

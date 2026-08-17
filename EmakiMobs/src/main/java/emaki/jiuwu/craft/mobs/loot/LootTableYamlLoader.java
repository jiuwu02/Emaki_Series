package emaki.jiuwu.craft.mobs.loot;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LootTableYamlLoader extends YamlDirectoryLoader<LootTableDefinition> {

    public LootTableYamlLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "loot_tables";
    }

    @Override
    protected String typeName() {
        return "LootTable";
    }

    @Override
    protected LootTableDefinition parse(File file, YamlSection config) {
        String mobId = config.getString("mob_id");
        if (mobId == null || mobId.isBlank()) {
            plugin.getLogger().warning("Loot table file '" + file.getName() + "' missing 'mob_id', skipping.");
            return null;
        }
        List<LootPoolDefinition> pools = new ArrayList<>();
        YamlSection poolsSection = config.getSection("pools");
        if (poolsSection != null) {
            for (String poolKey : poolsSection.getKeys(false)) {
                YamlSection poolSection = poolsSection.getSection(poolKey);
                if (poolSection != null) {
                    LootPoolDefinition pool = parsePool(poolSection);
                    if (pool != null) {
                        pools.add(pool);
                    }
                }
            }
        }
        return new LootTableDefinition(mobId, pools);
    }

    @Override
    protected String idOf(LootTableDefinition value) {
        return value.mobId();
    }

    private LootPoolDefinition parsePool(YamlSection section) {
        Object rolls = section.get("rolls");
        if (rolls == null) rolls = 1;
        List<LootEntryDefinition> entries = new ArrayList<>();
        YamlSection entriesSection = section.getSection("entries");
        if (entriesSection != null) {
            for (String entryKey : entriesSection.getKeys(false)) {
                YamlSection entrySection = entriesSection.getSection(entryKey);
                if (entrySection != null) {
                    LootEntryDefinition entry = parseEntry(entrySection);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
        }
        return new LootPoolDefinition(rolls, entries);
    }

    private LootEntryDefinition parseEntry(YamlSection section) {
        String item = section.getString("item");
        String emakiItem = section.getString("emaki_item");
        if ((item == null || item.isBlank()) && (emakiItem == null || emakiItem.isBlank())) {
            return null;
        }
        int weight = section.getInt("weight", 1);
        double chance = section.getDouble("chance", 1.0);
        List<LootFunctionDefinition> functions = new ArrayList<>();
        YamlSection functionsSection = section.getSection("functions");
        if (functionsSection != null) {
            for (String funcKey : functionsSection.getKeys(false)) {
                YamlSection funcSection = functionsSection.getSection(funcKey);
                if (funcSection != null) {
                    LootFunctionDefinition func = parseFunction(funcSection);
                    if (func != null) {
                        functions.add(func);
                    }
                }
            }
        }
        return new LootEntryDefinition(
                item == null || item.isBlank() ? null : item,
                emakiItem == null || emakiItem.isBlank() ? null : emakiItem,
                weight, chance, functions);
    }

    private LootFunctionDefinition parseFunction(YamlSection section) {
        String type = section.getString("type");
        if (type == null) {
            return null;
        }
        YamlSection countSection = section.getSection("count");
        LootFunctionDefinition.CountRange count = null;
        if (countSection != null) {
            int min = countSection.getInt("min", 1);
            int max = countSection.getInt("max", 1);
            count = new LootFunctionDefinition.CountRange(min, max);
        }
        return new LootFunctionDefinition(type, count);
    }
}

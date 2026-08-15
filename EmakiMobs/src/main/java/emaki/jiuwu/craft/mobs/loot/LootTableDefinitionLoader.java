package emaki.jiuwu.craft.mobs.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LootTableDefinitionLoader {

    private final Plugin plugin;

    public LootTableDefinitionLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, LootTableDefinition> loadAll() {
        Map<String, LootTableDefinition> result = new HashMap<>();
        File lootDir = new File(plugin.getDataFolder(), "loot_tables");
        if (!lootDir.isDirectory()) {
            return result;
        }
        File[] files = lootDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            LootTableDefinition def = parseFile(file);
            if (def != null) {
                result.put(def.mobId(), def);
            }
        }
        return result;
    }

    private LootTableDefinition parseFile(File file) {
        var config = YamlConfiguration.loadConfiguration(file);
        String mobId = config.getString("mob_id");
        if (mobId == null || mobId.isBlank()) {
            plugin.getLogger().warning("Loot table file '" + file.getName() + "' missing 'mob_id', skipping.");
            return null;
        }
        List<LootPoolDefinition> pools = new ArrayList<>();
        var poolsSection = config.getConfigurationSection("pools");
        if (poolsSection != null) {
            for (String poolKey : poolsSection.getKeys(false)) {
                var poolSection = poolsSection.getConfigurationSection(poolKey);
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

    private LootPoolDefinition parsePool(ConfigurationSection section) {
        Object rolls = section.get("rolls", 1);
        List<LootEntryDefinition> entries = new ArrayList<>();
        var entriesSection = section.getConfigurationSection("entries");
        if (entriesSection != null) {
            for (String entryKey : entriesSection.getKeys(false)) {
                var entrySection = entriesSection.getConfigurationSection(entryKey);
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

    private LootEntryDefinition parseEntry(ConfigurationSection section) {
        String item = section.getString("item");
        String emakiItem = section.getString("emaki_item");
        if ((item == null || item.isBlank()) && (emakiItem == null || emakiItem.isBlank())) {
            return null;
        }
        int weight = section.getInt("weight", 1);
        double chance = section.getDouble("chance", 1.0);
        List<LootFunctionDefinition> functions = new ArrayList<>();
        var functionsSection = section.getConfigurationSection("functions");
        if (functionsSection != null) {
            for (String funcKey : functionsSection.getKeys(false)) {
                var funcSection = functionsSection.getConfigurationSection(funcKey);
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

    private LootFunctionDefinition parseFunction(ConfigurationSection section) {
        String type = section.getString("type");
        if (type == null) {
            return null;
        }
        var countSection = section.getConfigurationSection("count");
        LootFunctionDefinition.CountRange count = null;
        if (countSection != null) {
            int min = countSection.getInt("min", 1);
            int max = countSection.getInt("max", 1);
            count = new LootFunctionDefinition.CountRange(min, max);
        }
        return new LootFunctionDefinition(type, count);
    }
}

package emaki.jiuwu.craft.mobs.loader;

import emaki.jiuwu.craft.mobs.spawner.AutonomousSpawnRule;
import emaki.jiuwu.craft.mobs.spawner.CountRange;
import emaki.jiuwu.craft.mobs.spawner.DistanceRange;
import emaki.jiuwu.craft.mobs.spawner.NaturalSpawnRule;
import emaki.jiuwu.craft.mobs.spawner.SpawnRule;
import emaki.jiuwu.craft.mobs.spawner.SpawnTrigger;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpawnRuleLoader {

    private final Plugin plugin;

    public SpawnRuleLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public List<SpawnRule> loadAll() {
        List<SpawnRule> result = new ArrayList<>();
        File spawnDir = new File(plugin.getDataFolder(), "spawn_rules");
        if (!spawnDir.isDirectory()) {
            return result;
        }
        File[] files = spawnDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            parseFile(file, result);
        }
        return result;
    }

    private void parseFile(File file, List<SpawnRule> result) {
        var config = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : config.getMapList("rules")) {
            SpawnRule rule = parseRule(map, file.getName());
            if (rule != null) {
                result.add(rule);
            }
        }
    }

    private SpawnRule parseRule(Map<?, ?> map, String fileName) {
        Object mobIdObj = map.get("mob_id");
        Object typeObj = map.get("type");
        if (!(mobIdObj instanceof String mobId) || !(typeObj instanceof String type)) {
            plugin.getLogger().warning(
                    "Spawn rule in '" + fileName + "' missing mob_id or type, skipping.");
            return null;
        }
        return switch (type) {
            case "natural"    -> parseNatural(mobId, map);
            case "autonomous" -> parseAutonomous(mobId, map, fileName);
            default -> {
                plugin.getLogger().warning(
                        "Unknown spawn type '" + type + "' in '" + fileName + "', skipping.");
                yield null;
            }
        };
    }

    private NaturalSpawnRule parseNatural(String mobId, Map<?, ?> map) {
        Set<String> worlds = new HashSet<>(getStringList(map, "worlds"));
        Set<Biome> biomes = parseBiomes(getStringList(map, "biomes"));
        List<?> yRange = getList(map, "y_range");
        int yMin = yRange.size() >= 1 ? toInt(yRange.get(0), -64) : -64;
        int yMax = yRange.size() >= 2 ? toInt(yRange.get(1), 320) : 320;
        int lightMax = toInt(map.get("light_level_max"), 15);
        double chance = toDouble(map.get("replacement_chance"), 1.0);
        int maxNearby = toInt(map.get("max_nearby"), 0);
        return new NaturalSpawnRule(
                mobId, Set.copyOf(worlds), biomes, yMin, yMax, lightMax, chance, maxNearby,
                parseCount(map));
    }

    private AutonomousSpawnRule parseAutonomous(String mobId, Map<?, ?> map, String fileName) {
        Object triggerObj = map.get("trigger");
        if (!(triggerObj instanceof String triggerStr)) {
            plugin.getLogger().warning(
                    "Autonomous rule for '" + mobId + "' in '" + fileName + "' missing trigger, skipping.");
            return null;
        }
        SpawnTrigger trigger = SpawnTrigger.fromString(triggerStr);
        if (trigger == null) {
            plugin.getLogger().warning(
                    "Unknown trigger '" + triggerStr + "' for '" + mobId + "' in '" + fileName + "', skipping.");
            return null;
        }
        long intervalTicks = toLong(map.get("interval_ticks"), 600L);
        int intervalDays = toInt(map.get("interval_days"), 1);
        boolean onDayStart = Boolean.TRUE.equals(map.get("on_day_start"));
        String cronExpr = map.get("cron") instanceof String s ? s : "";
        Set<String> worlds = new HashSet<>(getStringList(map, "worlds"));
        Set<Biome> biomes = parseBiomes(getStringList(map, "biomes"));
        List<Structure> structures = parseStructures(getStringList(map, "structures"), fileName);
        List<?> yRange = getList(map, "y_range");
        int yMin = yRange.size() >= 1 ? toInt(yRange.get(0), -64) : -64;
        int yMax = yRange.size() >= 2 ? toInt(yRange.get(1), 320) : 320;
        int lightMax = toInt(map.get("light_level_max"), 15);
        String timeOfDay = map.get("time_of_day") instanceof String s ? s : "any";
        boolean requireSurface = Boolean.TRUE.equals(map.get("require_surface"));
        DistanceRange distance = parseDistanceRange(map, "distance");
        int maxNearby = toInt(map.get("max_nearby"), 0);
        int maxGlobal = toInt(map.get("max_global"), 0);
        return new AutonomousSpawnRule(
                mobId, trigger, intervalTicks, intervalDays, onDayStart, cronExpr,
                Set.copyOf(worlds), biomes, List.copyOf(structures),
                yMin, yMax, lightMax, timeOfDay, requireSurface,
                distance, maxNearby, maxGlobal, parseCount(map));
    }

    private List<Structure> parseStructures(List<String> keys, String fileName) {
        List<Structure> result = new ArrayList<>();
        for (String key : keys) {
            NamespacedKey nsk = NamespacedKey.fromString(key);
            Structure s = nsk != null ? Registry.STRUCTURE.get(nsk) : null;
            if (s == null) {
                plugin.getLogger().warning(
                        "Unknown structure '" + key + "' in '" + fileName + "', skipping.");
            } else {
                result.add(s);
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private Set<Biome> parseBiomes(List<String> names) {
        Set<Biome> result = new HashSet<>();
        for (String name : names) {
            String key = name.contains(":") ? name.substring(name.lastIndexOf(':') + 1) : name;
            try {
                result.add(Biome.valueOf(key.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown biome '" + name + "', skipping.");
            }
        }
        return result;
    }

    private CountRange parseCount(Map<?, ?> map) {
        Object countObj = map.get("count");
        if (countObj instanceof Map<?, ?> countMap) {
            return new CountRange(toInt(countMap.get("min"), 1), toInt(countMap.get("max"), 1));
        }
        return new CountRange(1, 1);
    }

    private DistanceRange parseDistanceRange(Map<?, ?> map, String key) {
        Object obj = map.get(key);
        if (obj instanceof Map<?, ?> distMap) {
            return new DistanceRange(toInt(distMap.get("min"), 16), toInt(distMap.get("max"), 64));
        }
        return new DistanceRange(16, 64);
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    private List<?> getList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List<?> l ? l : List.of();
    }

    private int toInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private long toLong(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private double toDouble(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}

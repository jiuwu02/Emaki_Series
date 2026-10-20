package emaki.jiuwu.craft.dungeon.flow;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BlockInteractionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BlockStateTarget;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BossPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.BoundaryZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.CheckpointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.EffectZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.ExitPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.FxDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.GateDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.Head;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.HintDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.ItemInteractionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.KillTargetDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.MechanismDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.MobSpawnRef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.OptionCase;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.OptionDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RespawnPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RewardDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RoomDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.RoomTimerDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.SpawnPointDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.SpawnPointModuleDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TeleportZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TempItemDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerLimits;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.WaveDef;

public final class FlowLoader {

    private static final double COORD_LIMIT = 30_000_000.0D;

    public FlowDefinition load(File flowFile) {
        YamlSection root = YamlFiles.load(flowFile);
        if (root == null || root.isEmpty()) {
            throw new IllegalArgumentException("flow file missing or empty");
        }
        FlowDefinition definition = new FlowDefinition();
        parseSpawnPoint(root, definition);
        parseCheckpoints(root, definition);
        parseRespawnPoints(root, definition);
        parseExitPoints(root, definition);
        parseGates(root, definition);
        parseSpawnPoints(root, definition);
        parseTriggerZones(root, definition);
        parseBlockInteractions(root, definition);
        parseRewards(root, definition);
        parseBossPoints(root, definition);
        parseOptions(root, definition);
        parseHints(root, definition);
        parseTeleportZones(root, definition);
        parseBoundaryZones(root, definition);
        parseEffectZones(root, definition);
        parseMechanisms(root, definition);
        parseRoomTimers(root, definition);
        parseKillTargets(root, definition);
        parseItemInteractions(root, definition);
        parseFxPoints(root, definition);
        parseTempItems(root, definition);
        parseRooms(root, definition);
        if (!definition.validate()) {
            throw new IllegalArgumentException("duplicate module id in flow definition");
        }
        return definition;
    }

    private void parseSpawnPoint(YamlSection root, FlowDefinition definition) {
        YamlSection section = root.getSection("spawn_point");
        if (section == null || section.isEmpty()) {
            return;
        }
        definition.setSpawnPoint(new SpawnPointDef(head(section), point(section.getSection("location"))));
    }

    private void parseCheckpoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "checkpoints").entrySet()) {
            YamlSection section = entry.getValue();
            definition.checkpoints().put(entry.getKey(), new CheckpointDef(
                    head(section),
                    section.getString("record_id", entry.getKey()),
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.getInt("min_players", 1)
            ));
        }
    }

    private void parseRespawnPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "respawn_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.respawnPoints().put(entry.getKey(), new RespawnPointDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("checkpoint", "")
            ));
        }
    }

    private void parseExitPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "exit_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.exitPoints().put(entry.getKey(), new ExitPointDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getBoolean("need_confirm", true)
            ));
        }
    }

    private void parseGates(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "gates").entrySet()) {
            YamlSection section = entry.getValue();
            definition.gates().put(entry.getKey(), new GateDef(
                    head(section),
                    section.getString("gate_id", entry.getKey()),
                    regions(section.getMapList("door_regions")),
                    conditions(section.getList("conditions")),
                    section.getString("logic", "AND"),
                    section.getString("open_mode", "blocks"),
                    section.contains("redstone") ? point(section.getSection("redstone")) : null
            ));
        }
    }

    private void parseSpawnPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "spawn_points").entrySet()) {
            YamlSection section = entry.getValue();
            YamlSection trigger = section.getSection("trigger");
            List<WaveDef> waves = new ArrayList<>();
            List<?> rawWaves = section.getList("waves");
            if (rawWaves != null) {
                for (Object rawWave : rawWaves) {
                    if (rawWave instanceof Map<?, ?> waveMap) {
                        waves.add(parseWave(waveMap));
                    }
                }
            }
            definition.spawnPoints().put(entry.getKey(), new SpawnPointModuleDef(
                    head(section),
                    section.getString("spawn_id", entry.getKey()),
                    point(section.getSection("location")),
                    trigger == null ? 8 : trigger.getInt("range", 8),
                    trigger == null ? 1 : trigger.getInt("min_players", 1),
                    trigger == null || trigger.getBoolean("once", true),
                    waves
            ));
        }
    }

    private WaveDef parseWave(Map<?, ?> waveMap) {
        Object intervalRaw = waveMap.get("interval");
        int intervalTicks = parseTicks(String.valueOf(intervalRaw == null ? "2s" : intervalRaw));
        boolean dropLoot = !(waveMap.get("drop_loot") instanceof Boolean flag) || flag;
        List<MobSpawnRef> mobs = new ArrayList<>();
        Object mobsRaw = waveMap.get("mobs");
        if (mobsRaw instanceof List<?> mobList) {
            for (Object rawMob : mobList) {
            if (!(rawMob instanceof Map<?, ?> mobMap)) {
                continue;
            }
            Object rawId = mobMap.get("id");
            String mobId = rawId == null ? "" : String.valueOf(rawId);
                int count = mobMap.get("count") instanceof Number number ? number.intValue() : 1;
                int weight = mobMap.get("weight") instanceof Number number ? number.intValue() : 1;
                if (!mobId.isEmpty()) {
                    mobs.add(new MobSpawnRef(mobId, Math.max(1, count), Math.max(1, weight)));
                }
            }
        }
        return new WaveDef(Math.max(1, intervalTicks), dropLoot, mobs);
    }

    private void parseTriggerZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "trigger_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.triggerZones().put(entry.getKey(), new TriggerZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    section.getString("direction", "enter"),
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseBlockInteractions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "block_interactions").entrySet()) {
            YamlSection section = entry.getValue();
            definition.blockInteractions().put(entry.getKey(), new BlockInteractionDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("click", "right"),
                    conditions(section.getList("conditions")),
                    actions(section.getList("actions"))
            ));
        }
    }

    private void parseRewards(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "rewards").entrySet()) {
            YamlSection section = entry.getValue();
            definition.rewards().put(entry.getKey(), new RewardDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("loot_table", ""),
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseBossPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "boss_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.bossPoints().put(entry.getKey(), new BossPointDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("source", "vanilla"),
                    section.getString("boss_id", ""),
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseOptions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "options").entrySet()) {
            YamlSection section = entry.getValue();
            List<OptionCase> cases = new ArrayList<>();
            for (Map.Entry<String, YamlSection> caseEntry : sections(section, "cases").entrySet()) {
                YamlSection caseSection = caseEntry.getValue();
                cases.add(new OptionCase(
                        caseSection.getString("label", caseEntry.getKey()),
                        caseSection.getString("case", caseEntry.getKey()),
                        caseSection.getString("ending", ""),
                        regions(caseSection.getMapList("seal_regions"))
                ));
            }
            FlowPoint safePoint = section.contains("safe-point")
                    ? point(section.getSection("safe-point"))
                    : null;
            Map<String, String> endingMap = new LinkedHashMap<>();
            YamlSection endings = section.getSection("ending_map");
            if (endings != null && !endings.isEmpty()) {
                for (String key : endings.getKeys(false)) {
                    String value = endings.getString(key, "");
                    if (!value.isBlank()) {
                        endingMap.put(key, value);
                    }
                }
            }
            definition.options().put(entry.getKey(), new OptionDef(
                    head(section),
                    section.getString("option_id", entry.getKey()),
                    point(section.getSection("location")),
                    section.getString("select_mode", "single"),
                    cases,
                    section.getInt("time_limit_seconds", 0),
                    section.getInt("min_select", 1),
                    section.getInt("max_select", 1),
                    conditions(section.getList("conditions")),
                    safePoint,
                    endingMap
            ));
        }
    }

    private void parseHints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "hints").entrySet()) {
            YamlSection section = entry.getValue();
            definition.hints().put(entry.getKey(), new HintDef(
                    head(section),
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.getString("text", ""),
                    section.getString("channel", "chat"),
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseTeleportZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "teleport_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.teleportZones().put(entry.getKey(), new TeleportZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    point(section.getSection("target")),
                    conditions(section.getList("conditions")),
                    section.getBoolean("all_members", false)
            ));
        }
    }

    private void parseBoundaryZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "boundary_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.boundaryZones().put(entry.getKey(), new BoundaryZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    section.contains("pullback") ? point(section.getSection("pullback")) : null
            ));
        }
    }

    private void parseEffectZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "effect_zones").entrySet()) {
            YamlSection section = entry.getValue();
            List<String> effects = new ArrayList<>();
            List<?> rawEffects = section.getList("effects");
            if (rawEffects != null) {
                for (Object rawEffect : rawEffects) {
                    effects.add(String.valueOf(rawEffect));
                }
            }
            definition.effectZones().put(entry.getKey(), new EffectZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    effects,
                    section.getInt("interval_ticks", 40)
            ));
        }
    }

    private void parseMechanisms(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "mechanisms").entrySet()) {
            YamlSection section = entry.getValue();
            List<BlockStateTarget> targets = new ArrayList<>();
            for (Map.Entry<String, YamlSection> targetEntry : sections(section, "targets").entrySet()) {
                targets.add(new BlockStateTarget(
                        point(targetEntry.getValue().getSection("location")),
                        targetEntry.getValue().getString("block_data", "")
                ));
            }
            definition.mechanisms().put(entry.getKey(), new MechanismDef(
                    head(section),
                    targets,
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseRoomTimers(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "room_timers").entrySet()) {
            YamlSection section = entry.getValue();
            YamlSection onZero = section.getSection("on_zero");
            definition.roomTimers().put(entry.getKey(), new RoomTimerDef(
                    head(section),
                    region(section.getSection("region")),
                    section.getInt("seconds", 60),
                    onZero == null
                            ? new FlowActionDef("hint", Map.of("text", "time up"))
                            : new FlowActionDef(onZero.getString("type", "hint"), flatSection(onZero))
            ));
        }
    }

    private void parseKillTargets(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "kill_targets").entrySet()) {
            YamlSection section = entry.getValue();
            definition.killTargets().put(entry.getKey(), new KillTargetDef(
                    head(section),
                    section.getString("target", ""),
                    section.getInt("count", 1),
                    section.contains("scope") ? region(section.getSection("scope")) : null,
                    actions(section.getList("actions"))
            ));
        }
    }

    private void parseItemInteractions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "item_interactions").entrySet()) {
            YamlSection section = entry.getValue();
            definition.itemInteractions().put(entry.getKey(), new ItemInteractionDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("item", ""),
                    section.getInt("amount", 1),
                    section.getBoolean("consume", false),
                    conditions(section.getList("conditions")),
                    actions(section.getList("actions"))
            ));
        }
    }

    private void parseFxPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "fx_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.fxPoints().put(entry.getKey(), new FxDef(
                    head(section),
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.getString("sound", ""),
                    section.getString("particle", ""),
                    section.getDouble("radius", 8.0D),
                    conditions(section.getList("conditions"))
            ));
        }
    }

    private void parseTempItems(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : sections(root, "temp_items").entrySet()) {
            YamlSection section = entry.getValue();
            definition.tempItems().put(entry.getKey(), new TempItemDef(
                    head(section),
                    section.getString("item", ""),
                    section.getInt("amount", 1),
                    section.getString("timing", "entry"),
                    section.getString("hint", "")
            ));
        }
    }

    private void parseRooms(YamlSection root, FlowDefinition definition) {
        List<RoomDef> rooms = new ArrayList<>();
        for (Map.Entry<String, YamlSection> entry : sections(root, "rooms").entrySet()) {
            YamlSection section = entry.getValue();
            rooms.add(new RoomDef(
                    entry.getKey(),
                    section.getInt("order", rooms.size() + 1),
                    region(section.getSection("region")),
                    section.contains("arrival") ? point(section.getSection("arrival")) : null
            ));
        }
        rooms.sort((left, right) -> Integer.compare(left.order(), right.order()));
        definition.addRooms(rooms);
    }

    private Head head(YamlSection section) {
        YamlSection limits = section.getSection("limits");
        TriggerLimits triggerLimits = new TriggerLimits(
                limits == null ? 0 : limits.getInt("trigger", 0),
                limits == null ? 0 : limits.getInt("player", 0),
                limits == null ? 0 : limits.getInt("total", 0)
        );
        return new Head(section.getString("id", ""), section.getBoolean("enabled", true), triggerLimits);
    }

    private List<ConditionDef> conditions(List<?> raw) {
        List<ConditionDef> parsed = new ArrayList<>();
        if (raw == null) {
            return parsed;
        }
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map) {
                ConditionDef condition = parseCondition(map);
                if (condition != null) {
                    parsed.add(condition);
                }
            }
        }
        return parsed;
    }

    private ConditionDef parseCondition(Map<?, ?> map) {
        Object rawType = map.get("type");
        String type = rawType == null ? "" : String.valueOf(rawType);
        if (type.isEmpty()) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<ConditionDef> children = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if ("type".equals(key) || "logic".equals(key) || "when".equals(key)) {
                continue;
            }
            if ("children".equals(key) && entry.getValue() instanceof List<?> list) {
                for (Object child : list) {
                    if (child instanceof Map<?, ?> childMap) {
                        ConditionDef parsedChild = parseCondition(childMap);
                        if (parsedChild != null) {
                            children.add(parsedChild);
                        }
                    }
                }
                continue;
            }
            params.put(key, entry.getValue());
        }
        Object rawLogic = map.get("logic");
        return new ConditionDef(type, params,
                rawLogic == null ? "AND" : String.valueOf(rawLogic), children);
    }

    private List<FlowActionDef> actions(List<?> raw) {
        List<FlowActionDef> parsed = new ArrayList<>();
        if (raw == null) {
            return parsed;
        }
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map) {
                Object rawType = map.get("type");
                String type = rawType == null ? "" : String.valueOf(rawType);
                if (!type.isEmpty()) {
                    parsed.add(new FlowActionDef(type, flatParams(map)));
                }
            }
        }
        return parsed;
    }

    private Map<String, Object> flatSection(YamlSection section) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (!"type".equals(key)) {
                Object value = section.get(key);
                if (value != null) {
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    private Map<String, Object> flatParams(java.util.Map<?, ?> map) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!"type".equals(key)) {
                params.put(key, entry.getValue());
            }
        }
        return params;
    }

    private Map<String, YamlSection> sections(YamlSection parent, String path) {
        Map<String, YamlSection> result = new LinkedHashMap<>();
        YamlSection section = parent.getSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            YamlSection child = section.getSection(key);
            if (child != null) {
                result.put(key, child);
            }
        }
        return result;
    }

    private FlowPoint point(YamlSection section) {
        if (section == null) {
            throw new IllegalArgumentException("location missing");
        }
        double x = section.getDouble("x", 0.0D);
        double y = section.getDouble("y", 64.0D);
        double z = section.getDouble("z", 0.0D);
        checkBounds(x, y, z);
        return new FlowPoint(x, y, z,
                section.getDouble("yaw", 0.0D).floatValue(),
                section.getDouble("pitch", 0.0D).floatValue());
    }

    private FlowRegion region(YamlSection section) {
        if (section == null) {
            throw new IllegalArgumentException("region missing");
        }
        double minX = section.getDouble("min.x", 0.0D);
        double minY = section.getDouble("min.y", -64.0D);
        double minZ = section.getDouble("min.z", 0.0D);
        double maxX = section.getDouble("max.x", 0.0D);
        double maxY = section.getDouble("max.y", 320.0D);
        double maxZ = section.getDouble("max.z", 0.0D);
        checkBounds(minX, minY, minZ);
        checkBounds(maxX, maxY, maxZ);
        return new FlowRegion(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private List<FlowRegion> regions(List<Map<?, ?>> raw) {
        List<FlowRegion> parsed = new ArrayList<>();
        if (raw == null) {
            return parsed;
        }
        for (Map<?, ?> map : raw) {
            double minX = toDouble(map.get("min.x"), 0.0D);
            double minY = toDouble(map.get("min.y"), -64.0D);
            double minZ = toDouble(map.get("min.z"), 0.0D);
            double maxX = toDouble(map.get("max.x"), 0.0D);
            double maxY = toDouble(map.get("max.y"), 320.0D);
            double maxZ = toDouble(map.get("max.z"), 0.0D);
            checkBounds(minX, minY, minZ);
            checkBounds(maxX, maxY, maxZ);
            parsed.add(new FlowRegion(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return parsed;
    }

    private static void checkBounds(double x, double y, double z) {
        if (Math.abs(x) > COORD_LIMIT || Math.abs(z) > COORD_LIMIT || y < -64.0D || y > 320.0D) {
            throw new IllegalArgumentException("coordinate out of bounds: " + x + "," + y + "," + z);
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static int parseTicks(String raw) {
        if (raw == null || raw.isBlank()) {
            return 40;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("t")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            }
            if (normalized.endsWith("s")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1)) * 20;
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return 40;
        }
    }
}

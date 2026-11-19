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
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerSpec;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.TriggerZoneDef;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.WaveDef;

public final class FlowLoader {

    private static final double COORD_LIMIT = 30_000_000.0D;

    private static final Map<String, String> LEGACY_SECTIONS = buildLegacySections();

    public static Map<String, String> legacySections() {
        return LEGACY_SECTIONS;
    }

    public static String legacyBossSection() {
        return LEGACY_BOSS_SECTION;
    }

    private static Map<String, String> buildLegacySections() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("checkpoints", "check_points");
        map.put("hints", "tip_points");
        map.put("rewards", "reward_points");
        map.put("options", "option_points");
        map.put("block_interactions", "block_points");
        map.put("item_interactions", "item_points");
        map.put("gates", "gate_zones");
        map.put("mechanisms", "mechanism_zones");
        map.put("room_timers", "room_timer_zones");
        map.put("rooms", "room_zones");
        return map;
    }

    private static final String LEGACY_BOSS_SECTION = "boss_points";

    private final List<String> deprecations = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public FlowDefinition load(File flowFile) {
        deprecations.clear();
        warnings.clear();
        YamlSection root = YamlFiles.load(flowFile);
        if (root == null || root.isEmpty()) {
            throw new IllegalArgumentException("流程文件缺失或为空");
        }
        FlowDefinition definition = new FlowDefinition();
        parseSpawnPoint(root, definition);
        parseCheckpoints(root, definition);
        parseRespawnPoints(root, definition);
        parseExitPoints(root, definition);
        parseGates(root, definition);
        parseSpawnPoints(root, definition);
        parseLegacyBossPoints(root, definition);
        parseTriggerZones(root, definition);
        parseBlockInteractions(root, definition);
        parseRewards(root, definition);
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

    public List<String> deprecations() {
        return List.copyOf(deprecations);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void parseSpawnPoint(YamlSection root, FlowDefinition definition) {
        YamlSection section = root.getSection("spawn_point");
        if (section == null || section.isEmpty()) {
            return;
        }
        definition.setSpawnPoint(new SpawnPointDef(head(section), point(section.getSection("location")),
                conditions(section.getList("conditions")), triggerOf(section)));
    }

    private void parseCheckpoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "check_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.checkpoints().put(entry.getKey(), new CheckpointDef(
                    head(section),
                    section.getString("record_id", entry.getKey()),
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.getInt("min_players", 1),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseRespawnPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "respawn_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.respawnPoints().put(entry.getKey(), new RespawnPointDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("checkpoint", ""),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseExitPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "exit_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.exitPoints().put(entry.getKey(), new ExitPointDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getBoolean("need_confirm", true),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseGates(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "gate_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.gates().put(entry.getKey(), new GateDef(
                    head(section),
                    section.getString("gate_id", entry.getKey()),
                    regions(section.getMapList("door_regions")),
                    conditions(section.getList("conditions")),
                    section.getString("logic", "AND"),
                    section.getString("open_mode", "blocks"),
                    section.contains("redstone") ? point(section.getSection("redstone")) : null,
                    triggerOf(section)
            ));
        }
    }

    private void parseSpawnPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "spawn_points").entrySet()) {
            YamlSection section = entry.getValue();
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
                    waves,
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseLegacyBossPoints(YamlSection root, FlowDefinition definition) {
        Map<String, YamlSection> legacy = sections(root, LEGACY_BOSS_SECTION);
        if (legacy.isEmpty()) {
            return;
        }
        deprecations.add(LEGACY_BOSS_SECTION + "→spawn_points");
        for (Map.Entry<String, YamlSection> entry : legacy.entrySet()) {
            YamlSection section = entry.getValue();
            String bossId = section.getString("boss_id", "");
            if (bossId.isBlank()) {
                warnings.add(LEGACY_BOSS_SECTION + "." + entry.getKey() + " 缺少 boss_id, 已跳过");
                continue;
            }
            String source = legacyBossSource(section.getString("source", "vanilla"));
            MobSpawnRef mob = new MobSpawnRef(bossId, 1, 1, source, true);
            WaveDef wave = new WaveDef(1, true, List.of(mob));
            SpawnPointModuleDef normalized = new SpawnPointModuleDef(
                    head(section),
                    entry.getKey(),
                    point(section.getSection("location")),
                    List.of(wave),
                    conditions(section.getList("conditions")),
                    TriggerSpec.defaults());
            if (definition.spawnPoints().putIfAbsent(entry.getKey(), normalized) != null) {
                throw new IllegalArgumentException("duplicate module id in flow definition: " + entry.getKey());
            }
        }
    }

    public static String canonicalBossSource(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "vanilla" -> "vanilla";
            case "mythic" -> "mythic";
            case "emakimobs", "emaki-mobs" -> "emaki-mobs";
            default -> "auto";
        };
    }

    private String legacyBossSource(String raw) {
        String mapped = canonicalBossSource(raw);
        if ("auto".equals(mapped)) {
            deprecations.add(LEGACY_BOSS_SECTION + ".source=" + raw + "→auto");
        }
        return mapped;
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
                MobSpawnRef ref = parseMob(mobMap);
                if (ref != null) {
                    mobs.add(ref);
                }
            }
        }
        return new WaveDef(Math.max(1, intervalTicks), dropLoot, mobs);
    }

    private MobSpawnRef parseMob(Map<?, ?> mobMap) {
        Object rawId = mobMap.get("id");
        String mobId = rawId == null ? "" : String.valueOf(rawId);
        int count = mobMap.get("count") instanceof Number number ? number.intValue() : 1;
        int weight = mobMap.get("weight") instanceof Number number ? number.intValue() : 1;
        Object rawSource = mobMap.get("source");
        String source = mobSource(rawSource == null ? "auto" : String.valueOf(rawSource));
        boolean boss = mobMap.get("boss") instanceof Boolean flag && flag;
        if (mobId.isEmpty()) {
            return null;
        }
        return new MobSpawnRef(mobId, Math.max(1, count), Math.max(1, weight), source, boss);
    }

    private String mobSource(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "auto", "vanilla", "mythic", "emaki-mobs" -> value;
            case "emakimobs" -> "emaki-mobs";
            default -> {
                deprecations.add("source=" + raw + "→auto");
                yield "auto";
            }
        };
    }

    private void parseTriggerZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "trigger_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.triggerZones().put(entry.getKey(), new TriggerZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    section.getString("direction", "enter"),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseBlockInteractions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "block_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.blockInteractions().put(entry.getKey(), new BlockInteractionDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("click", "right"),
                    conditions(section.getList("conditions")),
                    actions(section.getList("actions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseRewards(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "reward_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.rewards().put(entry.getKey(), new RewardDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("loot_table", ""),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseOptions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "option_points").entrySet()) {
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
                    endingMap,
                    triggerOf(section)
            ));
        }
    }

    private void parseHints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "tip_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.hints().put(entry.getKey(), new HintDef(
                    head(section),
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.getString("text", ""),
                    tipType(section),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private String tipType(YamlSection section) {
        boolean hasType = section.contains("type");
        boolean hasChannel = section.contains("channel");
        if (hasChannel) {
            deprecations.add("channel→type");
        }
        String raw = hasType
                ? section.getString("type", "message")
                : hasChannel ? section.getString("channel", "chat") : "message";
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "message", "bossbar", "actionbar", "title" -> value;
            case "chat" -> {
                deprecations.add("chat→message");
                yield "message";
            }
            default -> {
                deprecations.add("type=" + raw + "→message");
                yield "message";
            }
        };
    }

    private void parseTeleportZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "teleport_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.teleportZones().put(entry.getKey(), new TeleportZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    point(section.getSection("target")),
                    conditions(section.getList("conditions")),
                    section.getBoolean("all_members", false),
                    triggerOf(section)
            ));
        }
    }

    private void parseBoundaryZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "boundary_zones").entrySet()) {
            YamlSection section = entry.getValue();
            definition.boundaryZones().put(entry.getKey(), new BoundaryZoneDef(
                    head(section),
                    region(section.getSection("region")),
                    section.contains("pullback") ? point(section.getSection("pullback")) : null,
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseEffectZones(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "effect_zones").entrySet()) {
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
                    section.getInt("interval_ticks", 40),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseMechanisms(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "mechanism_zones").entrySet()) {
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
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseRoomTimers(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "room_timer_zones").entrySet()) {
            YamlSection section = entry.getValue();
            YamlSection onZero = section.getSection("on_zero");
            definition.roomTimers().put(entry.getKey(), new RoomTimerDef(
                    head(section),
                    region(section.getSection("region")),
                    section.getInt("seconds", 60),
                    onZero == null
                            ? new FlowActionDef("hint", Map.of("text", "time up"))
                            : new FlowActionDef(onZero.getString("type", "hint"), flatSection(onZero)),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseKillTargets(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "kill_targets").entrySet()) {
            YamlSection section = entry.getValue();
            definition.killTargets().put(entry.getKey(), new KillTargetDef(
                    head(section),
                    section.getString("target", ""),
                    section.getInt("count", 1),
                    section.contains("scope") ? region(section.getSection("scope")) : null,
                    actions(section.getList("actions")),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseItemInteractions(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "item_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.itemInteractions().put(entry.getKey(), new ItemInteractionDef(
                    head(section),
                    point(section.getSection("location")),
                    section.getString("item", ""),
                    section.getInt("amount", 1),
                    section.getBoolean("consume", false),
                    conditions(section.getList("conditions")),
                    actions(section.getList("actions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseFxPoints(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "fx_points").entrySet()) {
            YamlSection section = entry.getValue();
            definition.fxPoints().put(entry.getKey(), new FxDef(
                    head(section),
                    section.contains("location") ? point(section.getSection("location")) : null,
                    section.contains("region") ? region(section.getSection("region")) : null,
                    section.getString("sound", ""),
                    section.getString("particle", ""),
                    section.getDouble("radius", 8.0D),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseTempItems(YamlSection root, FlowDefinition definition) {
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "temp_items").entrySet()) {
            YamlSection section = entry.getValue();
            definition.tempItems().put(entry.getKey(), new TempItemDef(
                    head(section),
                    section.getString("item", ""),
                    section.getInt("amount", 1),
                    section.getString("timing", "entry"),
                    section.getString("hint", ""),
                    conditions(section.getList("conditions")),
                    triggerOf(section)
            ));
        }
    }

    private void parseRooms(YamlSection root, FlowDefinition definition) {
        List<RoomDef> rooms = new ArrayList<>();
        for (Map.Entry<String, YamlSection> entry : moduleSections(root, "room_zones").entrySet()) {
            YamlSection section = entry.getValue();
            rooms.add(new RoomDef(
                    entry.getKey(),
                    section.getInt("order", rooms.size() + 1),
                    region(section.getSection("region")),
                    section.contains("arrival") ? point(section.getSection("arrival")) : null,
                    conditions(section.getList("conditions")),
                    triggerOf(section)
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

    private TriggerSpec triggerOf(YamlSection section) {
        YamlSection trigger = section.getSection("trigger");
        TriggerSpec defaults = TriggerSpec.defaults();
        if (trigger == null) {
            return defaults;
        }
        String actor = normalizeActor(trigger.getString("actor", defaults.actor()));
        String event = trigger.getString("event", defaults.event());
        int range = trigger.getInt("range", defaults.range());
        int minPlayers = trigger.getInt("min_players", defaults.minPlayers());
        boolean once = trigger.getBoolean("once", defaults.once());
        validateActorEvent(actor, event);
        return new TriggerSpec(actor, event, range, minPlayers, once);
    }

    private String normalizeActor(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "player", "team", "entity" -> value;
            default -> {
                deprecations.add("trigger.actor=" + raw + "→player");
                yield "player";
            }
        };
    }

    private void validateActorEvent(String actor, String event) {
        String value = event == null ? "" : event.trim().toLowerCase(Locale.ROOT);
        boolean deathLike = value.contains("death") || value.contains("kill") || value.contains("die");
        boolean enterLike = value.contains("enter") || value.contains("click") || value.contains("interact")
                || value.contains("activate") || value.contains("open");
        if (deathLike && !"entity".equals(actor)) {
            warnings.add("trigger.event=" + event + " 与 trigger.actor=" + actor + " 不匹配, 死亡类事件主语应为 entity, 已保留配置");
        } else if (enterLike && "entity".equals(actor)) {
            warnings.add("trigger.event=" + event + " 与 trigger.actor=" + actor + " 不匹配, 进入/点击类事件主语应为 player 或 team, 已保留配置");
        }
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

    private Map<String, Object> flatParams(Map<?, ?> map) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!"type".equals(key)) {
                params.put(key, entry.getValue());
            }
        }
        return params;
    }

    private Map<String, YamlSection> moduleSections(YamlSection root, String section) {
        Map<String, YamlSection> merged = new LinkedHashMap<>();
        for (Map.Entry<String, YamlSection> entry : sections(root, section).entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }
        String legacy = legacyNameOf(section);
        if (legacy == null) {
            return merged;
        }
        Map<String, YamlSection> old = sections(root, legacy);
        if (old.isEmpty()) {
            return merged;
        }
        deprecations.add(legacy + "→" + section);
        for (Map.Entry<String, YamlSection> entry : old.entrySet()) {
            if (merged.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                warnings.add(legacy + "." + entry.getKey() + " 与 " + section + "." + entry.getKey() + " 重名, 已保留新键");
            }
        }
        return merged;
    }

    private String legacyNameOf(String section) {
        for (Map.Entry<String, String> entry : LEGACY_SECTIONS.entrySet()) {
            if (entry.getValue().equals(section)) {
                return entry.getKey();
            }
        }
        return null;
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
            throw new IllegalArgumentException("位置缺失");
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
            throw new IllegalArgumentException("区域缺失");
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

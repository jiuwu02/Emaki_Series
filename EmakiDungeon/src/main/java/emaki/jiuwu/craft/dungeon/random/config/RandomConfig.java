package emaki.jiuwu.craft.dungeon.random.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class RandomConfig {

    public record RoomCount(int base, int min, int max, int growthPerPlayer) {
    }

    public record MandatoryRule(String tag, int minCount, int maxCount, String placementRule, int percentile) {
    }

    public record RoomType(String typeId, Integer weight, int minCount, int maxCount) {
    }

    public record Corridor(String linkMode, String templateRef, int width, int height,
            int lengthMin, int lengthMax, int maxTurns, int maxHeightDiff, String overlapPolicy,
            String floorBlock, int tileGap) {
    }

    public record Orientation(boolean rotatable, String mirrorAxis) {
    }

    /**
     * 拓扑形状策略。
     *
     * @param style           branch(枝杈状随机树) | chain(蛇形/环状链) | mixed(按 seed 比例混合)
     * @param chainChance     mixed 时产出 chain 的概率百分比 0-100
     * @param maxLoopsPercent 环路数上限占房量的百分比; 0 关闭环路, 回到纯树
     */
    public record Topology(String style, int chainChance, int maxLoopsPercent) {

        public boolean constraintDriven() {
            return "constraint".equals(style);
        }
    }

    public record Spatial(String mode, int floorCount, int floorSpacing, int floorMaxSlope,
            boolean templateConnections, boolean proceduralConnections, int loopChance,
            int verticalChance, int horizontalMargin, int verticalMargin, int maxSearchStates) {
    }

    public record TreasureMap(boolean enabled, String granularity, int pixelsPerRoom,
            int updateIntervalTicks, boolean showUnexploredOutline, boolean floorMarkers,
            Map<String, String> targetIcons) {
    }

    public record SeedPolicy(String source, long fixedValue) {
    }

    public record WorldPolicy(String type, int prewarmRadiusChunks, int simulationDistance) {
    }

    public record EditorPolicy(long maxTemplateBytes, boolean allowEntityCapture, String overwritePolicy) {
    }

    public record Limits(int blocksPerTick, int computeBudgetMs, int buildBudgetTicks,
            long maxTemplateBlocks, int maxLayoutBacktracks, int concurrentInstances) {
    }

    public record Loaded(RandomConfig config, List<String> errors) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public final RoomCount roomCount;
    public final Map<String, MandatoryRule> mandatory;
    public final Map<String, RoomType> roomTypes;
    public final Corridor corridor;
    public final Orientation orientation;
    public final Topology topology;
    public final Spatial spatial;
    public final TreasureMap treasureMap;
    public final SeedPolicy seed;
    public final WorldPolicy world;
    public final EditorPolicy editor;
    public final Limits limits;
    public final String canonical;
    public final String configHash;

    private RandomConfig(RoomCount roomCount, Map<String, MandatoryRule> mandatory,
            Map<String, RoomType> roomTypes, Corridor corridor, Orientation orientation,
            Topology topology, Spatial spatial, TreasureMap treasureMap, SeedPolicy seed, WorldPolicy world,
            EditorPolicy editor, Limits limits, String canonical) {
        this.roomCount = roomCount;
        this.mandatory = mandatory;
        this.roomTypes = roomTypes;
        this.corridor = corridor;
        this.orientation = orientation;
        this.topology = topology;
        this.spatial = spatial;
        this.treasureMap = treasureMap;
        this.seed = seed;
        this.world = world;
        this.editor = editor;
        this.limits = limits;
        this.canonical = canonical;
        this.configHash = emaki.jiuwu.craft.dungeon.random.gen.Seeds.configHash(canonical);
    }

    public static final String[] ICON_KEYS = {"star", "skull", "chest", "flag", "dot", "gate", "stairs", "ring"};

    /** 副本世界 config.yml 中承载随机生成参数的段名。 */
    public static final String SECTION = "random";

    /**
     * 从**副本世界** config.yml 解析随机生成配置。
     *
     * <p>该段随副本下放, 每个副本独立配置, 不再从插件顶层 config.yml 读取。
     */
    public static Loaded parse(YamlSection root) {
        List<String> errors = new ArrayList<>();
        if (root == null || root.isEmpty()) {
            errors.add("root: 配置为空");
            return new Loaded(null, errors);
        }
        YamlSection section = root.getSection(SECTION);
        if (section == null || section.isEmpty()) {
            errors.add(SECTION + ": 配置段缺失");
            return new Loaded(null, errors);
        }
        RoomCount roomCount = parseRoomCount(section, errors);
        Map<String, MandatoryRule> mandatory = parseMandatory(section, errors);
        Map<String, RoomType> roomTypes = parseRoomTypes(section, errors);
        Corridor corridor = parseCorridor(section, errors);
        Orientation orientation = parseOrientation(section, errors);
        Topology topology = parseTopology(section, errors);
        Spatial spatial = parseSpatial(section, errors);
        TreasureMap treasureMap = parseTreasureMap(section, errors);
        SeedPolicy seed = parseSeed(section, errors);
        WorldPolicy world = parseWorld(section, errors);
        EditorPolicy editor = parseEditor(section, errors);
        Limits limits = parseLimits(section, errors);
        if (roomCount != null && treasureMap != null && mandatory != null) {
            int mandatoryTotal = 0;
            for (MandatoryRule rule : mandatory.values()) {
                mandatoryTotal += Math.max(1, rule.minCount());
            }
            if (roomCount.min() < mandatoryTotal + 3) {
                errors.add("room-count.min: 必须不小于全部必选规则 min-count 之和加 3, 当前 " + roomCount.min());
            }
            if (treasureMap.enabled() && "room".equals(treasureMap.granularity()) && roomCount.max() > 64) {
                errors.add("room-count.max: 藏宝图启用且 granularity=room 时必须不大于 64, 当前 " + roomCount.max());
            }
        }
        if (!errors.isEmpty()) {
            return new Loaded(null, errors);
        }
        return new Loaded(new RandomConfig(roomCount, mandatory, roomTypes, corridor, orientation,
                topology, spatial, treasureMap, seed, world, editor, limits, canonicalSection(section)), errors);
    }

    private static RoomCount parseRoomCount(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("room-count");
        if (node == null) {
            errors.add("room-count: 缺失");
            return null;
        }
        int base = node.getInt("base", 12);
        int min = node.getInt("min", 8);
        int max = node.getInt("max", 40);
        int growth = node.getInt("growth-per-player", 2);
        if (base <= 0 || min <= 0 || max <= 0 || min > max || base < min || base > max) {
            errors.add("room-count: base/min/max 区间非法");
            return null;
        }
        return new RoomCount(base, min, max, growth);
    }

    private static Map<String, MandatoryRule> parseMandatory(YamlSection section, List<String> errors) {
        Map<String, MandatoryRule> rules = new LinkedHashMap<>();
        YamlSection node = section.getSection("mandatory");
        if (node == null) {
            return rules;
        }
        for (String tag : node.getKeys(false)) {
            YamlSection rule = node.getSection(tag);
            if (rule == null) {
                continue;
            }
            int minCount = rule.getInt("min-count", 1);
            int maxCount = rule.getInt("max-count", 0);
            String placement = rule.getString("placement-rule", "root").toLowerCase(Locale.ROOT);
            int percentile = rule.getInt("percentile", 50);
            if (minCount < 1) {
                errors.add("mandatory." + tag + ".min-count: 必须至少为 1");
            }
            if (maxCount != 0 && maxCount < minCount) {
                errors.add("mandatory." + tag + ".max-count: 不得小于 min-count");
            }
            if (!"root".equals(placement) && !"deepest".equals(placement) && !"branch-percentile".equals(placement)) {
                errors.add("mandatory." + tag + ".placement-rule: 非法值 " + placement);
            }
            if (percentile < 50 || percentile > 70) {
                errors.add("mandatory." + tag + ".percentile: 必须落在 50-70");
            }
            rules.put(tag, new MandatoryRule(tag, minCount, maxCount, placement, percentile));
        }
        return rules;
    }

    private static Map<String, RoomType> parseRoomTypes(YamlSection section, List<String> errors) {
        Map<String, RoomType> types = new LinkedHashMap<>();
        YamlSection node = section.getSection("room-types");
        if (node == null) {
            errors.add("room-types: 缺失或为空");
            return types;
        }
        for (String typeId : node.getKeys(false)) {
            YamlSection entry = node.getSection(typeId);
            if (entry == null) {
                continue;
            }
            Integer weight = entry.contains("weight") ? entry.getInt("weight", 1) : null;
            int minCount = entry.getInt("min-count", 0);
            int maxCount = entry.getInt("max-count", 0);
            if (weight != null && weight < 0) {
                errors.add("room-types." + typeId + ".weight: 不得为负");
            }
            if (minCount < 0 || (maxCount != 0 && maxCount < minCount)) {
                errors.add("room-types." + typeId + ".min-count/max-count: 区间非法");
            }
            types.put(typeId, new RoomType(typeId, weight, minCount, maxCount));
        }
        return types;
    }

    private static Topology parseTopology(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("topology");
        String style = node == null ? "branch" : node.getString("style", "branch").toLowerCase(Locale.ROOT);
        if (!"constraint".equals(style) && !"branch".equals(style)
                && !"chain".equals(style) && !"mixed".equals(style)) {
            errors.add("topology.style: 非法值 " + style);
        }
        int chainChance = node == null ? 50 : node.getInt("chain-chance", 50);
        if (chainChance < 0 || chainChance > 100) {
            errors.add("topology.chain-chance: 必须落在 0-100, 当前 " + chainChance);
        }
        int maxLoopsPercent = node == null ? 15 : node.getInt("max-loops-percent", 15);
        if (maxLoopsPercent < 0 || maxLoopsPercent > 100) {
            errors.add("topology.max-loops-percent: 必须落在 0-100, 当前 " + maxLoopsPercent);
        }
        return new Topology(style, chainChance, maxLoopsPercent);
    }

    private static Spatial parseSpatial(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("spatial");
        String mode = node == null ? "floors" : node.getString("mode", "floors").toLowerCase(Locale.ROOT);
        if (!"floors".equals(mode) && !"free".equals(mode)) {
            errors.add("spatial.mode: 合法值 floors/free");
        }
        int floorCount = node == null ? 3 : node.getInt("floor-count", 3);
        int floorSpacing = node == null ? 12 : node.getInt("floor-spacing", 12);
        int floorMaxSlope = node == null ? 2 : node.getInt("floor-max-slope", 2);
        int loopChance = node == null ? 20 : node.getInt("loop-chance", 20);
        int verticalChance = node == null ? 30 : node.getInt("vertical-chance", 30);
        int horizontalMargin = node == null ? 1 : node.getInt("horizontal-margin", 1);
        int verticalMargin = node == null ? 1 : node.getInt("vertical-margin", 1);
        int maxSearchStates = node == null ? 50000 : node.getInt("max-search-states", 50000);
        if (floorCount < 1 || floorCount > 16) {
            errors.add("spatial.floor-count: 必须落在 1-16");
        }
        if (floorSpacing < 3 || floorSpacing > 128) {
            errors.add("spatial.floor-spacing: 必须落在 3-128");
        }
        if (floorMaxSlope < 0 || floorMaxSlope > floorSpacing - 1) {
            errors.add("spatial.floor-max-slope: 必须落在 0..floor-spacing-1");
        }
        if (loopChance < 0 || loopChance > 100 || verticalChance < 0 || verticalChance > 100) {
            errors.add("spatial.loop-chance/vertical-chance: 必须落在 0-100");
        }
        if (horizontalMargin < 0 || horizontalMargin > 32
                || verticalMargin < 0 || verticalMargin > floorSpacing - 1) {
            errors.add("spatial.horizontal-margin/vertical-margin: 超出合法范围");
        }
        if (maxSearchStates < 1 || maxSearchStates > 1000000) {
            errors.add("spatial.max-search-states: 必须落在 1-1000000");
        }
        return new Spatial(mode, floorCount, floorSpacing, floorMaxSlope,
                node == null || node.getBoolean("template-connections", true),
                node == null || node.getBoolean("procedural-connections", true),
                loopChance, verticalChance, horizontalMargin, verticalMargin, maxSearchStates);
    }

    private static Corridor parseCorridor(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("corridor");
        String linkMode = node == null ? "default" : node.getString("link-mode", "default").toLowerCase(Locale.ROOT);
        if (!"force".equals(linkMode) && !"default".equals(linkMode) && !"off".equals(linkMode)) {
            errors.add("corridor.link-mode: 非法值 " + linkMode);
        }
        int width = node == null ? 1 : node.getInt("width", 1);
        int height = node == null ? 3 : node.getInt("height", 3);
        int lengthMin = node == null ? 2 : node.getInt("length-min", 2);
        int lengthMax = node == null ? 8 : node.getInt("length-max", 8);
        int maxTurns = node == null ? 1 : node.getInt("max-turns", 1);
        int maxHeightDiff = node == null ? 8 : node.getInt("max-height-diff", 8);
        String overlapPolicy = node == null ? "reject" : node.getString("overlap-policy", "reject").toLowerCase(Locale.ROOT);
        if (width != 1 || height != 3) {
            errors.add("corridor.width/height: 本版仅支持 1x3 截面");
        }
        if (lengthMin < 2 || lengthMax < lengthMin) {
            errors.add("corridor.length-min/length-max: 区间非法");
        }
        if (maxTurns != 0 && maxTurns != 1) {
            errors.add("corridor.max-turns: 仅支持 0 或 1");
        }
        if (maxHeightDiff < 0 || maxHeightDiff > 8) {
            errors.add("corridor.max-height-diff: 必须落在 0-8");
        }
        if (!"reject".equals(overlapPolicy)) {
            errors.add("corridor.overlap-policy: 唯一合法值 reject");
        }
        int slope = node == null ? 1 : node.getInt("slope", 1);
        if (slope != 1) {
            errors.add("corridor.slope: 固定 1:1, 仅接受 1");
        }
        String floorBlock = node == null ? "stone" : node.getString("floor-block", "stone");
        int tileGap = node == null ? 0 : node.getInt("tile-gap", 0);
        if (tileGap < 0 || tileGap > 8) {
            errors.add("corridor.tile-gap: 必须落在 0-8");
        }
        return new Corridor(linkMode, node == null ? "" : node.getString("template-ref", ""),
                width, height, lengthMin, lengthMax, maxTurns, maxHeightDiff, overlapPolicy,
                floorBlock, tileGap);
    }

    private static Orientation parseOrientation(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("orientation");
        boolean rotatable = node == null || node.getBoolean("rotatable", true);
        if (node != null && node.contains("rotations")) {
            errors.add("orientation.rotations: 已下线, 请改用 orientation.rotatable");
        }
        String mirrorAxis = node == null ? "none" : node.getString("mirror-axis", "none").toLowerCase(Locale.ROOT);
        if (!"none".equals(mirrorAxis) && !"x".equals(mirrorAxis)) {
            errors.add("orientation.mirror-axis: 仅支持 none 或 x");
        }
        return new Orientation(rotatable, mirrorAxis);
    }

    private static TreasureMap parseTreasureMap(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("treasure-map");
        boolean enabled = node == null || node.getBoolean("enabled", true);
        String granularity = node == null ? "room" : node.getString("granularity", "room").toLowerCase(Locale.ROOT);
        if (!"room".equals(granularity)) {
            errors.add("treasure-map.granularity: 唯一合法值 room");
        }
        int pixelsPerRoom = node == null ? 2 : node.getInt("pixels-per-room", 2);
        if (pixelsPerRoom != 2) {
            errors.add("treasure-map.pixels-per-room: 固定值 2");
        }
        int interval = node == null ? 5 : node.getInt("update-interval-ticks", 5);
        if (interval < 1) {
            errors.add("treasure-map.update-interval-ticks: 必须至少为 1");
        }
        boolean outline = node == null || node.getBoolean("show-unexplored-outline", true);
        boolean floorMarkers = node == null || node.getBoolean("floor-markers", true);
        Map<String, String> icons = new LinkedHashMap<>();
        if (node != null) {
            YamlSection iconNode = node.getSection("target-icons");
            if (iconNode != null) {
                for (String tag : iconNode.getKeys(false)) {
                    String iconKey = iconNode.getString(tag, "").toLowerCase(Locale.ROOT);
                    if (!java.util.Arrays.asList(ICON_KEYS).contains(iconKey)) {
                        errors.add("treasure-map.target-icons." + tag + ": 未知图标键 " + iconKey);
                    }
                    icons.put(tag, iconKey);
                }
            }
        }
        return new TreasureMap(enabled, granularity, pixelsPerRoom, interval, outline, floorMarkers, Map.copyOf(icons));
    }

    private static SeedPolicy parseSeed(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("seed");
        String source = node == null ? "auto" : node.getString("source", "auto").toLowerCase(Locale.ROOT);
        if (!"auto".equals(source) && !"fixed".equals(source) && !"replay".equals(source)) {
            errors.add("seed.source: 非法值 " + source);
        }
        Object fixedRaw = node == null ? null : node.get("fixed-value");
        long fixedValue = fixedRaw instanceof Number number ? number.longValue() : 0L;
        if (node != null && node.contains("persist-config-fingerprint")
                && !node.getBoolean("persist-config-fingerprint", true)) {
            errors.add("seed.persist-config-fingerprint: 固定 true, 不可关闭");
        }
        return new SeedPolicy(source, fixedValue);
    }

    private static WorldPolicy parseWorld(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("world");
        String type = node == null ? "air" : node.getString("type", "air").toLowerCase(Locale.ROOT);
        if (!"air".equals(type) && !"normal".equals(type) && !"nether".equals(type) && !"end".equals(type)) {
            errors.add("world.type: 合法值 air/normal/nether/end");
        }
        int prewarm = node == null ? 2 : node.getInt("prewarm-radius-chunks", 2);
        int simulation = node == null ? 6 : node.getInt("simulation-distance", 6);
        return new WorldPolicy(type, Math.max(0, prewarm), Math.max(1, simulation));
    }

    private static EditorPolicy parseEditor(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("editor");
        long maxBytes = node == null ? 4194304L : node.getDouble("max-template-bytes", 4194304.0D).longValue();
        boolean allowEntity = node != null && node.getBoolean("allow-entity-capture", false);
        String overwrite = node == null ? "reject" : node.getString("overwrite-policy", "reject").toLowerCase(Locale.ROOT);
        if (!"reject".equals(overwrite) && !"confirm".equals(overwrite)) {
            errors.add("editor.overwrite-policy: 非法值 " + overwrite);
        }
        return new EditorPolicy(maxBytes, allowEntity, overwrite);
    }

    private static Limits parseLimits(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("limits");
        int blocksPerTick = node == null ? 8192 : node.getInt("blocks-per-tick", 8192);
        int computeBudget = node == null ? 50 : node.getInt("compute-budget-ms", 50);
        int buildTicks = node == null ? 300 : node.getInt("build-budget-ticks", 300);
        long maxTemplateBlocks = node == null ? 2000000L : node.getDouble("max-template-blocks", 2000000.0D).longValue();
        int maxBacktracks = node == null ? 1000 : node.getInt("max-layout-backtracks", 1000);
        int concurrent = node == null ? 4 : node.getInt("concurrent-instances", 4);
        if (blocksPerTick <= 0 || blocksPerTick > 8192) {
            errors.add("limits.blocks-per-tick: 必须落在 1-8192");
        }
        if (computeBudget <= 0) {
            errors.add("limits.compute-budget-ms: 必须为正");
        }
        if (buildTicks <= 0) {
            errors.add("limits.build-budget-ticks: 必须为正");
        }
        if (maxTemplateBlocks <= 0) {
            errors.add("limits.max-template-blocks: 必须为正");
        }
        if (maxBacktracks <= 0) {
            errors.add("limits.max-layout-backtracks: 必须为正");
        }
        if (concurrent <= 0 || concurrent > 20) {
            errors.add("limits.concurrent-instances: 必须落在 1-20");
        }
        return new Limits(blocksPerTick, computeBudget, buildTicks, maxTemplateBlocks, maxBacktracks, concurrent);
    }

    public int roomCountFor(int players) {
        int p = Math.max(1, players);
        int value = roomCount.base() + (int) Math.round((p - 1) * (double) roomCount.growthPerPlayer());
        return Math.max(roomCount.min(), Math.min(roomCount.max(), value));
    }

    private static String canonicalSection(YamlSection section) {
        StringBuilder builder = new StringBuilder("random-config/v2\n");
        for (String key : new java.util.TreeSet<>(section.getKeys(true))) {
            Object value = section.get(key);
            if (!(value instanceof YamlSection)) {
                builder.append(key).append('=');
                emaki.jiuwu.craft.dungeon.random.gen.GenerationFingerprint.write(builder, value);
                builder.append(';');
            }
        }
        return builder.toString();
    }
}

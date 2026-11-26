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
            String floorBlock) {
    }

    public record Orientation(List<Integer> rotations, String mirrorAxis, String selection) {
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
    public final TreasureMap treasureMap;
    public final SeedPolicy seed;
    public final WorldPolicy world;
    public final EditorPolicy editor;
    public final Limits limits;
    public final String canonical;
    public final String configHash;

    private RandomConfig(RoomCount roomCount, Map<String, MandatoryRule> mandatory,
            Map<String, RoomType> roomTypes, Corridor corridor, Orientation orientation,
            TreasureMap treasureMap, SeedPolicy seed, WorldPolicy world, EditorPolicy editor,
            Limits limits) {
        this.roomCount = roomCount;
        this.mandatory = mandatory;
        this.roomTypes = roomTypes;
        this.corridor = corridor;
        this.orientation = orientation;
        this.treasureMap = treasureMap;
        this.seed = seed;
        this.world = world;
        this.editor = editor;
        this.limits = limits;
        this.canonical = buildCanonical();
        this.configHash = emaki.jiuwu.craft.dungeon.random.gen.Seeds.configHash(canonical);
    }

    public static final String[] ICON_KEYS = {"star", "skull", "chest", "flag", "dot", "gate", "stairs", "ring"};

    public static Loaded parse(YamlSection root) {
        List<String> errors = new ArrayList<>();
        if (root == null || root.isEmpty()) {
            errors.add("root: 配置为空");
            return new Loaded(null, errors);
        }
        YamlSection section = root.getSection("random-dungeon");
        if (section == null || section.isEmpty()) {
            errors.add("random-dungeon: 配置段缺失");
            return new Loaded(null, errors);
        }
        RoomCount roomCount = parseRoomCount(section, errors);
        Map<String, MandatoryRule> mandatory = parseMandatory(section, errors);
        Map<String, RoomType> roomTypes = parseRoomTypes(section, errors);
        Corridor corridor = parseCorridor(section, errors);
        Orientation orientation = parseOrientation(section, errors);
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
                treasureMap, seed, world, editor, limits), errors);
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
        return new Corridor(linkMode, node == null ? "" : node.getString("template-ref", ""),
                width, height, lengthMin, lengthMax, maxTurns, maxHeightDiff, overlapPolicy,
                floorBlock);
    }

    private static Orientation parseOrientation(YamlSection section, List<String> errors) {
        YamlSection node = section.getSection("orientation");
        List<Integer> rotations = new ArrayList<>();
        if (node != null) {
            Object raw = node.get("rotations");
            if (raw instanceof List<?> list) {
                for (Object entry : list) {
                    int value = entry instanceof Number number ? number.intValue() : -1;
                    if (value != 0 && value != 90 && value != 180 && value != 270) {
                        errors.add("orientation.rotations: 非法旋转 " + value);
                    }
                    rotations.add(value);
                }
            }
        }
        if (rotations.isEmpty()) {
            rotations = new ArrayList<>(List.of(0, 90, 180, 270));
        }
        String mirrorAxis = node == null ? "none" : node.getString("mirror-axis", "none").toLowerCase(Locale.ROOT);
        if (!"none".equals(mirrorAxis) && !"x".equals(mirrorAxis)) {
            errors.add("orientation.mirror-axis: 仅支持 none 或 x");
        }
        String selection = node == null ? "uniform" : node.getString("selection", "uniform").toLowerCase(Locale.ROOT);
        if (!"uniform".equals(selection)) {
            errors.add("orientation.selection: 唯一合法值 uniform");
        }
        return new Orientation(List.copyOf(rotations), mirrorAxis, selection);
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

    private String buildCanonical() {
        StringBuilder builder = new StringBuilder();
        builder.append("room-count=").append(roomCount.base()).append(',')
                .append(roomCount.min()).append(',').append(roomCount.max()).append(',')
                .append(roomCount.growthPerPlayer()).append(';');
        for (MandatoryRule rule : mandatory.values()) {
            builder.append("mandatory.").append(rule.tag()).append('=')
                    .append(rule.minCount()).append(',').append(rule.maxCount()).append(',')
                    .append(rule.placementRule()).append(',').append(rule.percentile()).append(';');
        }
        for (RoomType type : roomTypes.values()) {
            builder.append("room-types.").append(type.typeId()).append('=')
                    .append(type.weight()).append(',').append(type.minCount()).append(',')
                    .append(type.maxCount()).append(';');
        }
        builder.append("corridor=").append(corridor.linkMode()).append(',').append(corridor.templateRef()).append(',')
                .append(corridor.width()).append('x').append(corridor.height()).append(',')
                .append(corridor.lengthMin()).append('-').append(corridor.lengthMax()).append(',')
                .append(corridor.maxTurns()).append(',').append(corridor.maxHeightDiff()).append(';');
        builder.append("orientation=").append(orientation.rotations()).append(',')
                .append(orientation.mirrorAxis()).append(',').append(orientation.selection()).append(';');
        builder.append("treasure-map=").append(treasureMap.enabled()).append(',')
                .append(treasureMap.granularity()).append(',').append(treasureMap.pixelsPerRoom()).append(',')
                .append(treasureMap.updateIntervalTicks()).append(',').append(treasureMap.showUnexploredOutline()).append(',')
                .append(treasureMap.floorMarkers()).append(',').append(treasureMap.targetIcons()).append(';');
        builder.append("seed=").append(seed.source()).append(',').append(seed.fixedValue()).append(';');
        builder.append("world=").append(world.type()).append(',').append(world.prewarmRadiusChunks()).append(',')
                .append(world.simulationDistance()).append(';');
        builder.append("limits=").append(limits.blocksPerTick()).append(',').append(limits.computeBudgetMs()).append(',')
                .append(limits.buildBudgetTicks()).append(',').append(limits.maxTemplateBlocks()).append(',')
                .append(limits.maxLayoutBacktracks()).append(',').append(limits.concurrentInstances());
        return builder.toString();
    }
}

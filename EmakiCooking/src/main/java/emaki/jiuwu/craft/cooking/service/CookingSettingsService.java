package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingSettingsService {

    private static final Pattern RANGE_PATTERN = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");
    private static final DisplayAdjustmentProfile DEFAULT_ITEM_DISPLAY_ADJUSTMENT = new DisplayAdjustmentProfile(
            new Vector3(0.5D, 1.02D, 0.5D),
            new RotationProfile(AxisRotation.fixed(90D), AxisRotation.fixed(0D), AxisRotation.fixed(0D)),
            new Vector3(0.5D, 0.5D, 0.5D)
    );
    private static final DisplayAdjustmentProfile DEFAULT_BLOCK_DISPLAY_ADJUSTMENT = new DisplayAdjustmentProfile(
            new Vector3(0.5D, 1.125D, 0.5D),
            new RotationProfile(AxisRotation.fixed(0D), AxisRotation.fixed(90D), AxisRotation.fixed(0D)),
            new Vector3(0.25D, 0.25D, 0.25D)
    );

    private final JavaPlugin plugin;
    private volatile YamlSection configuration = new MapYamlSection();
    private volatile YamlSection steamerGuiConfiguration = new MapYamlSection();
    private volatile Map<String, ItemDisplayAdjustmentOverride> itemAdjustments = Map.of();

    public CookingSettingsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        configuration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        steamerGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("steamer.yml").toFile());
        itemAdjustments = loadItemAdjustments();
    }

    public ItemSource stationBlockSource(StationType stationType) {
        return ItemSourceUtil.parse(configuration.getString(stationPath(stationType) + ".block_source", ""));
    }

    public boolean requireSneaking(StationType stationType) {
        return configuration.getBoolean(stationPath(stationType) + ".require_sneaking", true);
    }

    public DisplayAdjustmentProfile displayAdjustment(StationType stationType, ItemSource source, boolean blockDisplay) {
        DisplayAdjustmentKind kind = blockDisplay ? DisplayAdjustmentKind.BLOCK : DisplayAdjustmentKind.ITEM;
        DisplayAdjustmentProfile fallback = resolveDefaultDisplayAdjustment(stationType, kind);
        if (source == null) {
            return fallback;
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            return fallback;
        }
        ItemDisplayAdjustmentOverride override = itemAdjustments.get(Texts.normalizeId(shorthand));
        return override == null ? fallback : override.resolve(stationType, fallback);
    }

    public boolean choppingDropResult() {
        return configuration.getBoolean("stations.chopping_board.drop_result", true);
    }

    public boolean choppingSpaceRestriction() {
        return configuration.getBoolean("stations.chopping_board.space_restriction", false);
    }

    public long choppingInteractionDelayMs() {
        return Math.max(0L, configuration.getInt("stations.chopping_board.interaction_delay_ms", 1000));
    }

    public List<ItemSource> choppingToolSources() {
        return parseSources(configuration.getStringList("stations.chopping_board.tool_sources"));
    }

    public boolean choppingCutDamageEnabled() {
        return configuration.getBoolean("stations.chopping_board.cut_damage.enabled", true);
    }

    public int choppingCutDamageChance() {
        return Math.max(0, configuration.getInt("stations.chopping_board.cut_damage.chance", 10));
    }

    public int choppingCutDamageValue() {
        return Math.max(0, configuration.getInt("stations.chopping_board.cut_damage.value", 2));
    }

    public boolean grinderDropResult() {
        return configuration.getBoolean("stations.grinder.drop_result", true);
    }

    public int grinderCheckDelayTicks() {
        return Math.max(1, configuration.getInt("stations.grinder.check_delay_ticks", 20));
    }

    public boolean wokDropResult() {
        return configuration.getBoolean("stations.wok.drop_result", true);
    }

    public boolean wokNeedBowl() {
        return configuration.getBoolean("stations.wok.need_bowl", true);
    }

    public long wokStirDelayMs() {
        return Math.max(0L, configuration.getInt("stations.wok.stir_delay_ms", 5000));
    }

    public long wokTimeoutMs() {
        return Math.max(0L, configuration.getInt("stations.wok.timeout_ms", 30000));
    }

    public List<ItemSource> wokSpatulaSources() {
        return parseSources(configuration.getStringList("stations.wok.spatula_sources"));
    }

    public List<HeatLevelRule> wokHeatLevels() {
        List<HeatLevelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.wok.heat_levels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource source = ItemSourceUtil.parse(normalized.get("source"));
            if (source == null) {
                continue;
            }
            Integer level = configurationValueToInt(normalized.get("level"), 0);
            result.add(new HeatLevelRule(source, level == null ? 0 : Math.max(0, level)));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public boolean wokScaldDamageEnabled() {
        return configuration.getBoolean("stations.wok.scald_damage.enabled", true);
    }

    public int wokScaldDamageValue() {
        return Math.max(0, configuration.getInt("stations.wok.scald_damage.value", 2));
    }

    public boolean wokFailureEnabled() {
        return configuration.getBoolean("stations.wok.failure.enabled", true);
    }

    public int wokFailureChance() {
        return Math.max(0, configuration.getInt("stations.wok.failure.chance", 5));
    }

    public String wokFailureOutputSource() {
        return configuration.getString("stations.wok.failure.output_source", "");
    }

    public String wokInvalidResultSource() {
        return configuration.getString("stations.wok.invalid_result_source", "");
    }

    public boolean steamerDropResult() {
        return configuration.getBoolean("stations.steamer.drop_result", true);
    }

    public String steamerInventoryTitle() {
        return steamerGuiConfiguration.getString("title", "<dark_gray>蒸锅");
    }

    public int steamerInventoryRows() {
        int rows = steamerGuiConfiguration.getInt("rows", 1);
        return Math.max(1, Math.min(6, rows));
    }

    public List<Integer> steamerIngredientSlots() {
        int inventorySize = steamerInventoryRows() * 9;
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        YamlSection slotsSection = steamerGuiConfiguration.getSection("slots");
        if (slotsSection != null && !slotsSection.isEmpty()) {
            for (String key : slotsSection.getKeys(false)) {
                YamlSection slotSection = slotsSection.getSection(key);
                if (slotSection == null || slotSection.isEmpty()) {
                    addSlotIndexes(slots, slotsSection.get(key), inventorySize);
                    continue;
                }
                String type = Texts.lower(slotSection.getString("type", ""));
                if (Texts.isNotBlank(type) && !"ingredient".equals(type)) {
                    continue;
                }
                addSlotIndexes(slots, slotSection.get("slots"), inventorySize);
            }
        }
        if (slots.isEmpty()) {
            for (int slot = 0; slot < Math.min(5, inventorySize); slot++) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    public List<ItemSource> steamerHeatSources() {
        return parseSources(configuration.getStringList("stations.steamer.heat_sources"));
    }

    public boolean steamerIgniteHeatSource() {
        return configuration.getBoolean("stations.steamer.ignite_heat_source", true);
    }

    public List<SteamerFuelRule> steamerFuels() {
        List<SteamerFuelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.steamer.fuels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource source = ItemSourceUtil.parse(normalized.get("source"));
            if (source == null) {
                continue;
            }
            Integer duration = configurationValueToInt(normalized.get("duration_seconds"), 0);
            result.add(new SteamerFuelRule(source, duration == null ? 0 : Math.max(0, duration)));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public List<SteamerMoistureRule> steamerMoistureSources() {
        List<SteamerMoistureRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.steamer.moisture_sources")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource input = ItemSourceUtil.parse(normalized.get("input_source"));
            if (input == null) {
                continue;
            }
            ItemSource output = ItemSourceUtil.parse(normalized.get("output_source"));
            Integer moisture = configurationValueToInt(normalized.get("moisture"), 0);
            result.add(new SteamerMoistureRule(input, output, moisture == null ? 0 : Math.max(0, moisture)));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public boolean steamerResetProgressWhenSteamEmpty() {
        return configuration.getBoolean("stations.steamer.reset_progress_when_steam_empty", true);
    }

    public int steamerSteamProductionEfficiency() {
        return Math.max(0, configuration.getInt("stations.steamer.steam_production_efficiency", 10));
    }

    public int steamerSteamConversionEfficiency() {
        return Math.max(0, configuration.getInt("stations.steamer.steam_conversion_efficiency", 1));
    }

    public int steamerSteamConsumptionEfficiency() {
        return Math.max(0, configuration.getInt("stations.steamer.steam_consumption_efficiency", 1));
    }

    private List<ItemSource> parseSources(List<String> tokens) {
        List<ItemSource> result = new ArrayList<>();
        if (tokens == null) {
            return result;
        }
        for (String token : tokens) {
            ItemSource source = ItemSourceUtil.parse(token);
            if (source != null) {
                result.add(source);
            }
        }
        return List.copyOf(result);
    }

    private String stationPath(StationType stationType) {
        return "stations." + stationType.folderName();
    }

    private Integer configurationValueToInt(Object raw, Integer fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception _) {
            return fallback;
        }
    }

    private void addSlotIndexes(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
        if (sink == null || raw == null || inventorySize <= 0) {
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                addSlotIndex(sink, entry, inventorySize);
            }
            return;
        }
        addSlotIndex(sink, raw, inventorySize);
    }

    private void addSlotIndex(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
        Integer slot = configurationValueToInt(raw, null);
        if (slot != null && slot >= 0 && slot < inventorySize) {
            sink.add(slot);
        }
    }

    private DisplayAdjustmentProfile resolveDefaultDisplayAdjustment(StationType stationType, DisplayAdjustmentKind kind) {
        DisplayAdjustmentProfile resolved = kind == DisplayAdjustmentKind.BLOCK
                ? DEFAULT_BLOCK_DISPLAY_ADJUSTMENT
                : DEFAULT_ITEM_DISPLAY_ADJUSTMENT;
        DisplayAdjustmentSpec globalDefaults = parseDisplayAdjustmentSpec(configuration.getSection("display_adjustments.defaults." + kind.path()));
        if (globalDefaults != null) {
            resolved = globalDefaults.resolve(resolved);
        }
        DisplayAdjustmentSpec stationDefaults = parseDisplayAdjustmentSpec(
                configuration.getSection("display_adjustments.station_defaults." + stationType.folderName() + "." + kind.path()));
        return stationDefaults == null ? resolved : stationDefaults.resolve(resolved);
    }

    private Map<String, ItemDisplayAdjustmentOverride> loadItemAdjustments() {
        Path directory = plugin.getDataFolder().toPath().resolve("item_adjustments");
        try {
            YamlFiles.ensureDirectory(directory);
        } catch (IOException _) {
            return Map.of();
        }
        if (!Files.exists(directory)) {
            return Map.of();
        }
        Map<String, ItemDisplayAdjustmentOverride> loaded = new LinkedHashMap<>();
        try (var stream = Files.walk(directory)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isYamlFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase()))
                    .toList();
            for (Path file : files) {
                ItemDisplayAdjustmentOverride adjustment = parseItemDisplayAdjustment(file, YamlFiles.load(file.toFile()));
                if (adjustment == null) {
                    continue;
                }
                loaded.put(Texts.normalizeId(adjustment.source()), adjustment);
            }
        } catch (IOException _) {
            return Map.of();
        }
        return loaded.isEmpty() ? Map.of() : Map.copyOf(loaded);
    }

    private boolean isYamlFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private ItemDisplayAdjustmentOverride parseItemDisplayAdjustment(Path file, YamlSection section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        ItemSource source = ItemSourceUtil.parse(section.getString("source", ""));
        String shorthand = source == null ? "" : ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            return null;
        }
        DisplayAdjustmentSpec shared = parseDisplayAdjustmentSpec(section.getSection("adjustment"));
        if (shared == null) {
            shared = parseDisplayAdjustmentSpec(section);
        }
        Map<StationType, DisplayAdjustmentSpec> stationAdjustments = new EnumMap<>(StationType.class);
        YamlSection stationsSection = section.getSection("stations");
        if (stationsSection != null && !stationsSection.isEmpty()) {
            for (String key : stationsSection.getKeys(false)) {
                StationType stationType = resolveStationType(key);
                if (stationType == null) {
                    continue;
                }
                DisplayAdjustmentSpec stationSpec = parseDisplayAdjustmentSpec(stationsSection.getSection(key));
                if (stationSpec != null) {
                    stationAdjustments.put(stationType, stationSpec);
                }
            }
        }
        if (shared == null && stationAdjustments.isEmpty()) {
            return null;
        }
        return new ItemDisplayAdjustmentOverride(
                shorthand,
                shared,
                stationAdjustments.isEmpty() ? Map.of() : Map.copyOf(stationAdjustments)
        );
    }

    private StationType resolveStationType(String value) {
        if (Texts.isBlank(value)) {
            return null;
        }
        String normalized = Texts.normalizeId(value);
        for (StationType stationType : StationType.values()) {
            if (normalized.equals(Texts.normalizeId(stationType.folderName()))
                    || normalized.equals(Texts.normalizeId(stationType.name()))) {
                return stationType;
            }
        }
        return null;
    }

    private DisplayAdjustmentSpec parseDisplayAdjustmentSpec(YamlSection section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        PartialVector offset = parsePartialVector(section, "offset", false);
        RotationOverride rotation = parseRotationOverride(section.getSection("rotation"));
        PartialVector scale = parsePartialVector(section, "scale", true);
        if (offset == null && rotation == null && scale == null) {
            return null;
        }
        return new DisplayAdjustmentSpec(offset, rotation, scale);
    }

    private PartialVector parsePartialVector(YamlSection section, String path, boolean allowScalar) {
        if (section == null || Texts.isBlank(path) || !section.contains(path)) {
            return null;
        }
        Object raw = section.get(path);
        if (allowScalar && raw instanceof Number scalar) {
            double value = scalar.doubleValue();
            return new PartialVector(value, value, value);
        }
        YamlSection nested = section.getSection(path);
        if (nested == null || nested.isEmpty()) {
            return null;
        }
        Double x = nested.getDouble("x", null);
        Double y = nested.getDouble("y", null);
        Double z = nested.getDouble("z", null);
        if (x == null && y == null && z == null) {
            return null;
        }
        return new PartialVector(x, y, z);
    }

    private RotationOverride parseRotationOverride(YamlSection section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        AxisRotation x = parseAxisRotation(section.get("x"));
        AxisRotation y = parseAxisRotation(section.get("y"));
        AxisRotation z = parseAxisRotation(section.get("z"));
        if (x == null && y == null && z == null) {
            return null;
        }
        return new RotationOverride(x, y, z);
    }

    private AxisRotation parseAxisRotation(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return AxisRotation.fixed(number.doubleValue());
        }
        String text = Texts.toStringSafe(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = RANGE_PATTERN.matcher(text);
        if (matcher.matches()) {
            double min = parseDouble(matcher.group(1), 0D);
            double max = parseDouble(matcher.group(2), min);
            return new AxisRotation(min, max);
        }
        return AxisRotation.fixed(parseDouble(text, 0D));
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(Texts.toStringSafe(raw).trim());
        } catch (Exception _) {
            return fallback;
        }
    }

    public record HeatLevelRule(ItemSource source, int level) {
    }

    public record SteamerFuelRule(ItemSource source, int durationSeconds) {
    }

    public record SteamerMoistureRule(ItemSource inputSource, ItemSource outputSource, int moisture) {
    }

    private enum DisplayAdjustmentKind {
        ITEM("item"),
        BLOCK("block");

        private final String path;

        DisplayAdjustmentKind(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    public record DisplayAdjustmentProfile(Vector3 offset, RotationProfile rotation, Vector3 scale) {

        public DisplayAdjustmentProfile {
            offset = offset == null ? new Vector3(0.5D, 1.0D, 0.5D) : offset;
            rotation = rotation == null
                    ? new RotationProfile(AxisRotation.fixed(0D), AxisRotation.fixed(0D), AxisRotation.fixed(0D))
                    : rotation;
            scale = scale == null ? new Vector3(0.5D, 0.5D, 0.5D) : scale;
        }

        public Location applyOffset(Location base) {
            if (base == null) {
                return null;
            }
            return new Location(
                    base.getWorld(),
                    base.getX() + offset.x(),
                    base.getY() + offset.y(),
                    base.getZ() + offset.z()
            );
        }

        public Transformation transformation() {
            Quaternionf rotationQuaternion = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotation.x().resolve()),
                    (float) Math.toRadians(rotation.y().resolve()),
                    (float) Math.toRadians(rotation.z().resolve())
            );
            return new Transformation(
                    new Vector3f(),
                    rotationQuaternion,
                    scale.toVector3f(),
                    new Quaternionf()
            );
        }
    }

    public record Vector3(double x, double y, double z) {

        private Vector3f toVector3f() {
            return new Vector3f((float) x, (float) y, (float) z);
        }
    }

    public record RotationProfile(AxisRotation x, AxisRotation y, AxisRotation z) {

        public RotationProfile {
            x = x == null ? AxisRotation.fixed(0D) : x;
            y = y == null ? AxisRotation.fixed(0D) : y;
            z = z == null ? AxisRotation.fixed(0D) : z;
        }
    }

    public record AxisRotation(double min, double max) {

        public AxisRotation {
            if (min > max) {
                double swapped = min;
                min = max;
                max = swapped;
            }
        }

        public static AxisRotation fixed(double value) {
            return new AxisRotation(value, value);
        }

        public double resolve() {
            if (Double.compare(min, max) == 0) {
                return min;
            }
            return ThreadLocalRandom.current().nextDouble(min, max);
        }
    }

    private record PartialVector(Double x, Double y, Double z) {

        private Vector3 resolve(Vector3 fallback) {
            return new Vector3(
                    x == null ? fallback.x() : x,
                    y == null ? fallback.y() : y,
                    z == null ? fallback.z() : z
            );
        }
    }

    private record RotationOverride(AxisRotation x, AxisRotation y, AxisRotation z) {

        private RotationProfile resolve(RotationProfile fallback) {
            return new RotationProfile(
                    x == null ? fallback.x() : x,
                    y == null ? fallback.y() : y,
                    z == null ? fallback.z() : z
            );
        }
    }

    private record DisplayAdjustmentSpec(PartialVector offset, RotationOverride rotation, PartialVector scale) {

        private DisplayAdjustmentProfile resolve(DisplayAdjustmentProfile fallback) {
            return new DisplayAdjustmentProfile(
                    offset == null ? fallback.offset() : offset.resolve(fallback.offset()),
                    rotation == null ? fallback.rotation() : rotation.resolve(fallback.rotation()),
                    scale == null ? fallback.scale() : scale.resolve(fallback.scale())
            );
        }
    }

    private record ItemDisplayAdjustmentOverride(String source,
            DisplayAdjustmentSpec shared,
            Map<StationType, DisplayAdjustmentSpec> stations) {

        private DisplayAdjustmentProfile resolve(StationType stationType, DisplayAdjustmentProfile fallback) {
            DisplayAdjustmentSpec stationSpec = stations == null ? null : stations.get(stationType);
            if (stationSpec != null) {
                return stationSpec.resolve(fallback);
            }
            return shared == null ? fallback : shared.resolve(fallback);
        }
    }
}

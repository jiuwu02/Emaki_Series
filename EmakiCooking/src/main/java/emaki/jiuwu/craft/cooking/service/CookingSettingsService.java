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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.NutritionComboThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionCompare;
import emaki.jiuwu.craft.cooking.model.NutritionFoodSource;
import emaki.jiuwu.craft.cooking.model.NutritionSingleThreshold;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationInteractionType;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingSettingsService {

    public static final String INTERACTION_PLACE_INPUT = "place_input";
    public static final String INTERACTION_PROCESS = "process";
    public static final String INTERACTION_RETURN_INPUT = "return_input";
    public static final String INTERACTION_ADD_INGREDIENT = "add_ingredient";
    public static final String INTERACTION_STIR = "stir";
    public static final String INTERACTION_SERVE = "serve";
    public static final String INTERACTION_RETURN_INGREDIENT = "return_ingredient";
    public static final String INTERACTION_INSPECT = "inspect";
    public static final String INTERACTION_START = "start";
    public static final String INTERACTION_OPEN = "open";
    public static final String INTERACTION_FUEL = "fuel";
    public static final String INTERACTION_MOISTURE = "moisture";

    private static final Pattern RANGE_PATTERN = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*-\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    static final String[] LIT_SOURCE_KEYS = {"lit_item_sources"};
    static final String[] UNLIT_SOURCE_KEYS = {"unlit_item_sources"};
    static final DisplayAdjustmentProfile DEFAULT_ITEM_DISPLAY_ADJUSTMENT = new DisplayAdjustmentProfile(
            new Vector3(0.5D, 1.02D, 0.5D),
            new RotationProfile(AxisRotation.fixed(90D), AxisRotation.fixed(0D), AxisRotation.fixed(0D)),
            new Vector3(0.5D, 0.5D, 0.5D)
    );
    static final DisplayAdjustmentProfile DEFAULT_BLOCK_DISPLAY_ADJUSTMENT = new DisplayAdjustmentProfile(
            new Vector3(0.5D, 1.125D, 0.5D),
            new RotationProfile(AxisRotation.fixed(0D), AxisRotation.fixed(90D), AxisRotation.fixed(0D)),
            new Vector3(0.25D, 0.25D, 0.25D)
    );

    private final JavaPlugin plugin;
    volatile YamlSection configuration = new MapYamlSection();
    private volatile YamlSection steamerGuiConfiguration = new MapYamlSection();
    private volatile YamlSection ovenGuiConfiguration = new MapYamlSection();
    private volatile YamlSection juicerGuiConfiguration = new MapYamlSection();
    private volatile YamlSection fermentationBarrelGuiConfiguration = new MapYamlSection();
    volatile Map<String, ItemDisplayAdjustmentOverride> itemAdjustments = Map.of();
    volatile Set<String> globalDisabledWorlds = Set.of();
    volatile Map<StationType, Set<String>> stationDisabledWorlds = Map.of();
    volatile Map<StationType, List<ItemSourceRef>> stationBlockSourceCache = Map.of();

    final ChoppingBoardSettings choppingBoardSettings;
    final GrinderSettings grinderSettings;
    final WokSettings wokSettings;
    final SteamerSettings steamerSettings;
    final OvenSettings ovenSettings;
    final JuicerSettings juicerSettings;
    final FermentationBarrelSettings fermentationBarrelSettings;

    public CookingSettingsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.choppingBoardSettings = new ChoppingBoardSettings(() -> configuration);
        this.grinderSettings = new GrinderSettings(() -> configuration);
        this.wokSettings = new WokSettings(() -> configuration);
        this.steamerSettings = new SteamerSettings(() -> configuration, () -> steamerGuiConfiguration);
        this.ovenSettings = new OvenSettings(() -> configuration, () -> ovenGuiConfiguration);
        this.juicerSettings = new JuicerSettings(() -> configuration, () -> juicerGuiConfiguration);
        this.fermentationBarrelSettings =
                new FermentationBarrelSettings(() -> configuration, () -> fermentationBarrelGuiConfiguration);
    }

    public void reload() {
        configuration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        steamerGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("steamer.yml").toFile());
        ovenGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("oven.yml").toFile());
        juicerGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("juicer.yml").toFile());
        fermentationBarrelGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("fermentation_barrel.yml").toFile());
        reportDeprecatedMatcherKeys();
        itemAdjustments = loadItemAdjustments();
        globalDisabledWorlds = disabledWorldSet(configuration.get("station.disabled_worlds"));
        stationDisabledWorlds = loadStationDisabledWorlds();
        stationBlockSourceCache = loadStationBlockSources();
    }

    private void reportDeprecatedMatcherKeys() {
        reportDeprecatedMatcherKeys("stations.chopping_board", "tool", "tool_item_sources", "tool_matcher");
        reportDeprecatedMatcherKeys("stations.wok", "spatula", "spatula_item_sources", "spatula_matcher");
        reportDeprecatedMatcherKeys("stations.juicer", "container", "container_item_sources", "container_matcher");
        int index = 0;
        for (Map<?, ?> raw : configuration.getMapList("stations.steamer.moisture_rules")) {
            Map<String, Object> rule = MapYamlSection.normalizeMap(raw);
            if (rule.containsKey("input_item_sources") || rule.containsKey("input_matcher")) {
                boolean nested = rule.get("input") instanceof Map<?, ?>;
                plugin.getLogger().warning("[config] stations.steamer.moisture_rules[" + index
                        + "] 处的旧版烹饪匹配键已弃用; 请使用 input.item_sources 与 input.matcher, "
                        + deprecatedKeyHint(nested, "嵌套输入配置优先"));
            }
            index++;
        }
    }

    private static String deprecatedKeyHint(boolean nested, String nestedHint) { return CookingSettingsServiceDelegate.deprecatedKeyHint(nested, nestedHint); }

    private void reportDeprecatedMatcherKeys(String ownerPath, String nestedKey, String legacySourcesKey, String legacyMatcherKey) {
        YamlSection owner = configuration.getSection(ownerPath);
        if (owner == null || (!owner.contains(legacySourcesKey) && !owner.contains(legacyMatcherKey))) {
            return;
        }
        YamlSection nested = owner.getSection(nestedKey);
        plugin.getLogger().warning("[config] " + ownerPath + "." + legacySourcesKey + "/" + legacyMatcherKey
                + " 处的旧版烹饪匹配键已弃用; 请使用 " + ownerPath + "." + nestedKey + ".item_sources 与 .matcher, "
                + deprecatedKeyHint(nested != null && !nested.isEmpty(), "嵌套配置优先"));
    }

    public List<ItemSourceRef> stationBlockSources(StationType stationType) { return new CookingSettingsServiceDelegate(this).stationBlockSources(stationType); }

    public ItemSourceRef stationBlockSource(StationType stationType) { return new CookingSettingsServiceDelegate(this).stationBlockSource(stationType); }

    public boolean isInteractionDisabled(StationType stationType, String worldName) { return new CookingSettingsServiceDelegate(this).isInteractionDisabled(stationType, worldName); }

    public Set<String> globalDisabledWorlds() { return new CookingSettingsServiceDelegate(this).globalDisabledWorlds(); }

    public Set<String> disabledWorlds(StationType stationType) { return new CookingSettingsServiceDelegate(this).disabledWorlds(stationType); }

    public boolean onlyRecipeItems(StationType stationType) {
        String stationPath = stationPath(stationType) + ".only_recipe_items";
        if (configuration.contains(stationPath)) {
            return configuration.getBoolean(stationPath, true);
        }
        return configuration.getBoolean("input_rules.only_recipe_items", true);
    }

    public String displayEntitiesBackend() { return new CookingSettingsServiceDelegate(this).displayEntitiesBackend(); }

    public double displayEntitiesViewDistanceBlocks() { return new CookingSettingsServiceDelegate(this).displayEntitiesViewDistanceBlocks(); }

    public int displayEntitiesRefreshIntervalTicks() { return new CookingSettingsServiceDelegate(this).displayEntitiesRefreshIntervalTicks(); }

    public double wokDisplayLayoutRadius() { return new CookingSettingsServiceDelegate(this).wokDisplayLayoutRadius(); }

    public boolean textDisplayEnabled(StationType stationType) { return new CookingSettingsServiceDelegate(this).textDisplayEnabled(stationType); }

    public TextDisplayProfile textDisplayProfile(StationType stationType) {
        Vector3 defaultOffset = readVector3(
                configuration.getSection("display_entities.text.defaults.offset"),
                new Vector3(0.5D, 1.6D, 0.5D));
        Vector3 defaultScale = readVector3(
                configuration.getSection("display_entities.text.defaults.scale"),
                new Vector3(1.0D, 1.0D, 1.0D));
        String billboard = normalizeBillboard(configuration.getString("display_entities.text.billboard", "center"));
        int lineWidth = Math.max(1, configuration.getInt("display_entities.text.line_width", 200));
        int backgroundArgb = configuration.getInt("display_entities.text.background", 0);
        boolean shadow = configuration.getBoolean("display_entities.text.shadow", true);
        boolean seeThrough = configuration.getBoolean("display_entities.text.see_through", false);

        Vector3 offset = defaultOffset;
        Vector3 scale = defaultScale;
        if (stationType != null) {
            String base = "display_entities.text.stations." + stationType.folderName();
            offset = readVector3(configuration.getSection(base + ".offset"), defaultOffset);
            scale = readVector3(configuration.getSection(base + ".scale"), defaultScale);
            String stationBillboard = configuration.getString(base + ".billboard", "");
            if (Texts.isNotBlank(stationBillboard)) {
                billboard = normalizeBillboard(stationBillboard);
            }
        }
        return new TextDisplayProfile(offset, scale, billboard, lineWidth, backgroundArgb, shadow, seeThrough);
    }

    Vector3 readVector3(YamlSection section, Vector3 fallback) { return new CookingSettingsServiceDelegate(this).readVector3(section, fallback); }

    private String normalizeBillboard(String value) { return new CookingSettingsServiceDelegate(this).normalizeBillboard(value); }

    public boolean matchesInteraction(StationType stationType,
            String operation,
            StationInteraction interaction) { return new CookingSettingsServiceDelegate(this).matchesInteraction(stationType, operation, interaction); }

    public DisplayAdjustmentProfile displayAdjustment(StationType stationType, ItemSourceRef source, boolean blockDisplay) {
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

    public boolean choppingDropResult() { return new CookingSettingsServiceDelegate(this).choppingDropResult(); }

    public boolean choppingSpaceRestriction() { return new CookingSettingsServiceDelegate(this).choppingSpaceRestriction(); }

    public long choppingInteractionDelayMs() { return new CookingSettingsServiceDelegate(this).choppingInteractionDelayMs(); }

    public ItemRequirement choppingToolRequirement() { return new CookingSettingsServiceDelegate(this).choppingToolRequirement(); }

    public boolean choppingCutDamageEnabled() { return new CookingSettingsServiceDelegate(this).choppingCutDamageEnabled(); }

    public int choppingCutDamageChance() { return new CookingSettingsServiceDelegate(this).choppingCutDamageChance(); }

    public int choppingCutDamageValue() { return new CookingSettingsServiceDelegate(this).choppingCutDamageValue(); }

    public boolean grinderDropResult() { return new CookingSettingsServiceDelegate(this).grinderDropResult(); }

    public int grinderCheckDelayTicks() { return new CookingSettingsServiceDelegate(this).grinderCheckDelayTicks(); }

    public boolean wokDropResult() { return new CookingSettingsServiceDelegate(this).wokDropResult(); }

    public boolean wokNeedBowl() { return new CookingSettingsServiceDelegate(this).wokNeedBowl(); }

    public long wokStirDelayMs() { return new CookingSettingsServiceDelegate(this).wokStirDelayMs(); }

    public long wokTimeoutMs() { return new CookingSettingsServiceDelegate(this).wokTimeoutMs(); }

    public ItemRequirement wokSpatulaRequirement() { return new CookingSettingsServiceDelegate(this).wokSpatulaRequirement(); }

    public List<HeatLevelRule> wokHeatLevels() { return new CookingSettingsServiceDelegate(this).wokHeatLevels(); }

    public boolean wokIgniteHeatSource() { return new CookingSettingsServiceDelegate(this).wokIgniteHeatSource(); }

    public boolean wokScaldDamageEnabled() { return new CookingSettingsServiceDelegate(this).wokScaldDamageEnabled(); }

    public int wokScaldDamageValue() { return new CookingSettingsServiceDelegate(this).wokScaldDamageValue(); }

    public boolean wokStirAnimationEnabled() { return new CookingSettingsServiceDelegate(this).wokStirAnimationEnabled(); }

    public int wokStirAnimationDurationTicks() { return new CookingSettingsServiceDelegate(this).wokStirAnimationDurationTicks(); }

    public double wokStirAnimationHeight() { return new CookingSettingsServiceDelegate(this).wokStirAnimationHeight(); }

    public String wokStirAnimationAxis() { return new CookingSettingsServiceDelegate(this).wokStirAnimationAxis(); }

    public double wokStirAnimationRotation() { return new CookingSettingsServiceDelegate(this).wokStirAnimationRotation(); }

    public boolean wokFailureEnabled() { return new CookingSettingsServiceDelegate(this).wokFailureEnabled(); }

    public int wokFailureChance() { return new CookingSettingsServiceDelegate(this).wokFailureChance(); }

    public String wokFailureOutputSource() { return new CookingSettingsServiceDelegate(this).wokFailureOutputSource(); }

    public String wokInvalidResultSource() { return new CookingSettingsServiceDelegate(this).wokInvalidResultSource(); }

    public boolean steamerDropResult() { return new CookingSettingsServiceDelegate(this).steamerDropResult(); }

    public String steamerInventoryTitle() { return new CookingSettingsServiceDelegate(this).steamerInventoryTitle(); }

    public int steamerInventoryRows() { return new CookingSettingsServiceDelegate(this).steamerInventoryRows(); }

    public List<Integer> steamerIngredientSlots() { return new CookingSettingsServiceDelegate(this).steamerIngredientSlots(); }

    public List<ItemSourceRef> steamerHeatSources() { return new CookingSettingsServiceDelegate(this).steamerHeatSources(); }

    public List<HeatSourceIgnitionRule> steamerHeatSourceIgnitionRules() { return new CookingSettingsServiceDelegate(this).steamerHeatSourceIgnitionRules(); }

    public boolean steamerIgniteHeatSource() { return new CookingSettingsServiceDelegate(this).steamerIgniteHeatSource(); }

    public List<SteamerFuelRule> steamerFuels() { return new CookingSettingsServiceDelegate(this).steamerFuels(); }

    public List<SteamerMoistureRule> steamerMoistureSources() { return new CookingSettingsServiceDelegate(this).steamerMoistureSources(); }

    public boolean steamerResetProgressWhenSteamEmpty() { return new CookingSettingsServiceDelegate(this).steamerResetProgressWhenSteamEmpty(); }

    public int steamerSteamProductionEfficiency() { return new CookingSettingsServiceDelegate(this).steamerSteamProductionEfficiency(); }

    public int steamerSteamConversionEfficiency() { return new CookingSettingsServiceDelegate(this).steamerSteamConversionEfficiency(); }

    public int steamerSteamConsumptionEfficiency() { return new CookingSettingsServiceDelegate(this).steamerSteamConsumptionEfficiency(); }

    public boolean ovenDropResult() { return new CookingSettingsServiceDelegate(this).ovenDropResult(); }

    public String ovenInventoryTitle() { return new CookingSettingsServiceDelegate(this).ovenInventoryTitle(); }

    public int ovenInventoryRows() { return new CookingSettingsServiceDelegate(this).ovenInventoryRows(); }

    public List<Integer> ovenIngredientSlots() { return new CookingSettingsServiceDelegate(this).ovenIngredientSlots(); }

    public List<OvenFuelRule> ovenFuels() { return new CookingSettingsServiceDelegate(this).ovenFuels(); }

    public int ovenHeatMin() { return new CookingSettingsServiceDelegate(this).ovenHeatMin(); }

    public int ovenHeatMax() { return new CookingSettingsServiceDelegate(this).ovenHeatMax(); }

    public int ovenHeatDecayPerSecond() { return new CookingSettingsServiceDelegate(this).ovenHeatDecayPerSecond(); }

    public boolean juicerDropResult() { return new CookingSettingsServiceDelegate(this).juicerDropResult(); }

    public boolean juicerRequireContainer() { return new CookingSettingsServiceDelegate(this).juicerRequireContainer(); }

    public ItemRequirement juicerContainerRequirement() { return new CookingSettingsServiceDelegate(this).juicerContainerRequirement(); }

    public int juicerMaxFluidMl() { return new CookingSettingsServiceDelegate(this).juicerMaxFluidMl(); }

    public int juicerDefaultServingMl() { return new CookingSettingsServiceDelegate(this).juicerDefaultServingMl(); }

    public String juicerInventoryTitle() { return new CookingSettingsServiceDelegate(this).juicerInventoryTitle(); }

    public int juicerInventoryRows() { return new CookingSettingsServiceDelegate(this).juicerInventoryRows(); }

    public List<Integer> juicerIngredientSlots() { return new CookingSettingsServiceDelegate(this).juicerIngredientSlots(); }

    public boolean fermentationBarrelDropResult() { return new CookingSettingsServiceDelegate(this).fermentationBarrelDropResult(); }

    public boolean fermentationBarrelPauseWhenOpen() { return new CookingSettingsServiceDelegate(this).fermentationBarrelPauseWhenOpen(); }

    public String fermentationBarrelInventoryTitle() { return new CookingSettingsServiceDelegate(this).fermentationBarrelInventoryTitle(); }

    public int fermentationBarrelInventoryRows() { return new CookingSettingsServiceDelegate(this).fermentationBarrelInventoryRows(); }

    public List<Integer> fermentationBarrelIngredientSlots() { return new CookingSettingsServiceDelegate(this).fermentationBarrelIngredientSlots(); }

    static List<Integer> ingredientSlots(YamlSection guiConfiguration, int inventorySize, int fallbackCount) { return CookingSettingsServiceDelegate.ingredientSlots(guiConfiguration, inventorySize, fallbackCount); }

    static List<ItemSourceRef> parseSources(Object raw) { return CookingSettingsServiceDelegate.parseSources(raw); }

    static ItemSourceRef parseLitSource(Map<String, Object> normalized) { return CookingSettingsServiceDelegate.parseLitSource(normalized); }

    static ItemSourceRef parseUnlitSource(Map<String, Object> normalized) { return CookingSettingsServiceDelegate.parseUnlitSource(normalized); }

    static Object firstPresent(Map<String, Object> values, String... keys) { return CookingSettingsServiceDelegate.firstPresent(values, keys); }

    String stationPath(StationType stationType) { return new CookingSettingsServiceDelegate(this).stationPath(stationType); }

    StationInteractionType defaultInteraction(StationType stationType, String operation) { return new CookingSettingsServiceDelegate(this).defaultInteraction(stationType, operation); }

    static Integer configurationValueToInt(Object raw, Integer fallback) { return CookingSettingsServiceDelegate.configurationValueToInt(raw, fallback); }

    static void addSlotIndexes(LinkedHashSet<Integer> sink, Object raw, int inventorySize) { CookingSettingsServiceDelegate.addSlotIndexes(sink, raw, inventorySize); }

    static void addSlotIndex(LinkedHashSet<Integer> sink, Object raw, int inventorySize) { CookingSettingsServiceDelegate.addSlotIndex(sink, raw, inventorySize); }

    DisplayAdjustmentProfile resolveDefaultDisplayAdjustment(StationType stationType, DisplayAdjustmentKind kind) {
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

    private Map<StationType, List<ItemSourceRef>> loadStationBlockSources() { return new CookingSettingsServiceDelegate(this).loadStationBlockSources(); }

    private Map<StationType, Set<String>> loadStationDisabledWorlds() { return new CookingSettingsServiceDelegate(this).loadStationDisabledWorlds(); }

    Set<String> disabledWorldSet(Object raw) { return new CookingSettingsServiceDelegate(this).disabledWorldSet(raw); }

    String normalizeWorldName(String worldName) { return new CookingSettingsServiceDelegate(this).normalizeWorldName(worldName); }

    Map<String, ItemDisplayAdjustmentOverride> loadItemAdjustments() {
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
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
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

    private boolean isYamlFile(Path path) { return new CookingSettingsServiceDelegate(this).isYamlFile(path); }

    ItemDisplayAdjustmentOverride parseItemDisplayAdjustment(Path file, YamlSection section) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(section.get("item_sources"));
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

    private StationType resolveStationType(String value) { return new CookingSettingsServiceDelegate(this).resolveStationType(value); }

    DisplayAdjustmentSpec parseDisplayAdjustmentSpec(YamlSection section) {
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

    PartialVector parsePartialVector(YamlSection section, String path, boolean allowScalar) { return new CookingSettingsServiceDelegate(this).parsePartialVector(section, path, allowScalar); }

    RotationOverride parseRotationOverride(YamlSection section) {
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

    AxisRotation parseAxisRotation(Object raw) {
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
        var matcher = RANGE_PATTERN.matcher(text);
        if (matcher.matches()) {
            double min = parseDouble(matcher.group(1), 0D);
            double max = parseDouble(matcher.group(2), min);
            return new AxisRotation(min, max);
        }
        return AxisRotation.fixed(parseDouble(text, 0D));
    }

    double parseDouble(String raw, double fallback) { return new CookingSettingsServiceDelegate(this).parseDouble(raw, fallback); }

    public record HeatLevelRule(ItemSourceRef source, ItemSourceRef litSource, ItemSourceRef unlitSource, int level) {
    }

    public record HeatSourceIgnitionRule(ItemSourceRef source, ItemSourceRef litSource, ItemSourceRef unlitSource) {
    }

    public record SteamerFuelRule(ItemSourceRef source, int durationSeconds, Matcher matcher) {

        public SteamerFuelRule(ItemSourceRef source, int durationSeconds) {
            this(source, durationSeconds, null);
        }

        public ItemRequirement requirement() {
            return requirementOf(source, matcher);
        }
    }

    public record SteamerMoistureRule(ItemSourceRef inputSource, ItemSourceRef outputSource, int moisture, Matcher matcher) {

        public SteamerMoistureRule(ItemSourceRef inputSource, ItemSourceRef outputSource, int moisture) {
            this(inputSource, outputSource, moisture, null);
        }

        public ItemRequirement inputRequirement() {
            return requirementOf(inputSource, matcher);
        }
    }

    public record OvenFuelRule(ItemSourceRef source, int durationSeconds, int heat, Matcher matcher) {

        public OvenFuelRule(ItemSourceRef source, int durationSeconds, int heat) {
            this(source, durationSeconds, heat, null);
        }

        public ItemRequirement requirement() {
            return requirementOf(source, matcher);
        }
    }

    static ItemRequirement requirementOf(ItemSourceRef source, Matcher matcher) { return CookingSettingsServiceDelegate.requirementOf(source, matcher); }

    enum DisplayAdjustmentKind {
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

    public record TextDisplayProfile(Vector3 offset,
            Vector3 scale,
            String billboard,
            int lineWidth,
            int backgroundArgb,
            boolean shadow,
            boolean seeThrough) {

        public TextDisplayProfile {
            offset = offset == null ? new Vector3(0.5D, 1.6D, 0.5D) : offset;
            scale = scale == null ? new Vector3(1.0D, 1.0D, 1.0D) : scale;
            billboard = billboard == null || billboard.isBlank() ? "center" : billboard;
            lineWidth = Math.max(1, lineWidth);
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

    record PartialVector(Double x, Double y, Double z) {

        Vector3 resolve(Vector3 fallback) {
            return new Vector3(
                    x == null ? fallback.x() : x,
                    y == null ? fallback.y() : y,
                    z == null ? fallback.z() : z
            );
        }
    }

    record RotationOverride(AxisRotation x, AxisRotation y, AxisRotation z) {

        RotationProfile resolve(RotationProfile fallback) {
            return new RotationProfile(
                    x == null ? fallback.x() : x,
                    y == null ? fallback.y() : y,
                    z == null ? fallback.z() : z
            );
        }
    }

    record DisplayAdjustmentSpec(PartialVector offset, RotationOverride rotation, PartialVector scale) {

        DisplayAdjustmentProfile resolve(DisplayAdjustmentProfile fallback) {
            return new DisplayAdjustmentProfile(
                    offset == null ? fallback.offset() : offset.resolve(fallback.offset()),
                    rotation == null ? fallback.rotation() : rotation.resolve(fallback.rotation()),
                    scale == null ? fallback.scale() : scale.resolve(fallback.scale())
            );
        }
    }

    record ItemDisplayAdjustmentOverride(String source,
            DisplayAdjustmentSpec shared,
            Map<StationType, DisplayAdjustmentSpec> stations) {

        DisplayAdjustmentProfile resolve(StationType stationType, DisplayAdjustmentProfile fallback) {
            DisplayAdjustmentSpec stationSpec = stations == null ? null : stations.get(stationType);
            if (stationSpec != null) {
                return stationSpec.resolve(fallback);
            }
            return shared == null ? fallback : shared.resolve(fallback);
        }
    }

    public List<String> getStationActions(StationType stationType, String operation) { return new CookingSettingsServiceDelegate(this).getStationActions(stationType, operation); }

    public boolean nutritionEnabled() { return new CookingSettingsServiceDelegate(this).nutritionEnabled(); }

    public int nutritionSaveIntervalSeconds() { return new CookingSettingsServiceDelegate2(this).nutritionSaveIntervalSeconds(); }

    public List<NutritionFoodSource> nutritionFoodSources() { return new CookingSettingsServiceDelegate2(this).nutritionFoodSources(); }

    public List<NutritionSingleThreshold> nutritionSingleThresholds() {
        List<NutritionSingleThreshold> result = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : configuration.getMapList("nutrition.thresholds.single")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            String id = Texts.toStringSafe(firstPresent(normalized, "id"));
            if (Texts.isBlank(id)) {
                id = "single_" + index;
            }
            List<String> types = normalizeIds(stringList(normalized.get("types")));
            double value = configurationValueToDouble(normalized.get("value"), 0D);
            NutritionCompare compare = NutritionCompare.parse(Texts.toStringSafe(normalized.get("compare")));
            List<String> onMeet = stringList(normalized.get("actions"));
            List<String> onRecover = stringList(normalized.get("on_recover"));
            if (onMeet.isEmpty() && onRecover.isEmpty()) {
                index++;
                continue;
            }
            result.add(new NutritionSingleThreshold(id, types, value, compare, onMeet, onRecover));
            index++;
        }
        return List.copyOf(result);
    }

    public List<NutritionComboThreshold> nutritionComboThresholds() {
        List<NutritionComboThreshold> result = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> entry : configuration.getMapList("nutrition.thresholds.combo")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            String id = Texts.toStringSafe(firstPresent(normalized, "id"));
            if (Texts.isBlank(id)) {
                id = "combo_" + index;
            }
            List<String> types = normalizeIds(stringList(normalized.get("types")));
            double value = configurationValueToDouble(normalized.get("value"), 0D);
            NutritionCompare compare = NutritionCompare.parse(Texts.toStringSafe(normalized.get("compare")));
            Integer requiredCount = configurationValueToInt(normalized.get("required_count"), 5);
            List<String> onMeet = stringList(normalized.get("actions"));
            List<String> onRecover = stringList(normalized.get("on_recover"));
            if (onMeet.isEmpty() && onRecover.isEmpty()) {
                index++;
                continue;
            }
            result.add(new NutritionComboThreshold(id, types, value, compare,
                    requiredCount == null ? 5 : requiredCount, onMeet, onRecover));
            index++;
        }
        return List.copyOf(result);
    }

    Map<String, Double> parseNutritionAmounts(Object raw) { return new CookingSettingsServiceDelegate2(this).parseNutritionAmounts(raw); }

    List<String> stringList(Object raw) { return new CookingSettingsServiceDelegate2(this).stringList(raw); }

    private List<String> normalizeIds(List<String> raw) { return new CookingSettingsServiceDelegate2(this).normalizeIds(raw); }

    Double configurationValueToDouble(Object raw, double fallback) { return new CookingSettingsServiceDelegate2(this).configurationValueToDouble(raw, fallback); }
}

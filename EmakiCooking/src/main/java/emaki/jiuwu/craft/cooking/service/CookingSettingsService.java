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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
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

    private static final String[] LIT_SOURCE_KEYS = {"lit_item_sources"};
    private static final String[] UNLIT_SOURCE_KEYS = {"unlit_item_sources"};
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
    private volatile YamlSection ovenGuiConfiguration = new MapYamlSection();
    private volatile YamlSection juicerGuiConfiguration = new MapYamlSection();
    private volatile YamlSection fermentationBarrelGuiConfiguration = new MapYamlSection();
    private volatile Map<String, ItemDisplayAdjustmentOverride> itemAdjustments = Map.of();
    private volatile Set<String> globalDisabledWorlds = Set.of();
    private volatile Map<StationType, Set<String>> stationDisabledWorlds = Map.of();

    private final ChoppingBoardSettings choppingBoardSettings;
    private final GrinderSettings grinderSettings;
    private final WokSettings wokSettings;
    private final SteamerSettings steamerSettings;
    private final OvenSettings ovenSettings;
    private final JuicerSettings juicerSettings;
    private final FermentationBarrelSettings fermentationBarrelSettings;

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
        itemAdjustments = loadItemAdjustments();
        globalDisabledWorlds = disabledWorldSet(configuration.get("station.disabled_worlds"));
        stationDisabledWorlds = loadStationDisabledWorlds();
    }

    public List<ItemSourceRef> stationBlockSources(StationType stationType) {
        return parseSources(configuration.get(stationPath(stationType) + ".block_item_sources"));
    }

    public ItemSourceRef stationBlockSource(StationType stationType) {
        List<ItemSourceRef> sources = stationBlockSources(stationType);
        return sources.isEmpty() ? null : sources.getFirst();
    }

    public boolean isInteractionDisabled(StationType stationType, String worldName) {
        String world = normalizeWorldName(worldName);
        if (world.isBlank()) {
            return false;
        }
        if (globalDisabledWorlds.contains(world)) {
            return true;
        }
        Set<String> stationWorlds = stationType == null ? null : stationDisabledWorlds.get(stationType);
        return stationWorlds != null && stationWorlds.contains(world);
    }

    public Set<String> globalDisabledWorlds() {
        return globalDisabledWorlds;
    }

    public Set<String> disabledWorlds(StationType stationType) {
        Set<String> worlds = stationType == null ? null : stationDisabledWorlds.get(stationType);
        return worlds == null ? Set.of() : worlds;
    }

    public boolean onlyRecipeItems(StationType stationType) {
        String stationPath = stationPath(stationType) + ".only_recipe_items";
        if (configuration.contains(stationPath)) {
            return configuration.getBoolean(stationPath, true);
        }
        return configuration.getBoolean("input_rules.only_recipe_items", true);
    }

    public String displayEntitiesBackend() {
        String backend = Texts.normalizeId(configuration.getString("display_entities.backend", "auto"));
        return switch (backend) {
            case "packet_events", "bukkit" -> backend;
            default -> "auto";
        };
    }

    public double displayEntitiesViewDistanceBlocks() {
        return Math.max(1D, configuration.getDouble("display_entities.view_distance_blocks", 48D));
    }

    public int displayEntitiesRefreshIntervalTicks() {
        return Math.max(1, configuration.getInt("display_entities.refresh_interval_ticks", 20));
    }

    public double wokDisplayLayoutRadius() {
        return wokSettings.displayLayoutRadius();
    }


    public boolean textDisplayEnabled(StationType stationType) {
        if (stationType == null) {
            return false;
        }
        if (!configuration.getBoolean("display_entities.text.enabled", true)) {
            return false;
        }
        String stationPath = "display_entities.text.stations." + stationType.folderName() + ".enabled";
        return configuration.getBoolean(stationPath, true);
    }

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

    private Vector3 readVector3(YamlSection section, Vector3 fallback) {
        if (section == null || section.isEmpty()) {
            return fallback;
        }
        Double x = section.getDouble("x", null);
        Double y = section.getDouble("y", null);
        Double z = section.getDouble("z", null);
        if (x == null && y == null && z == null) {
            return fallback;
        }
        return new Vector3(
                x == null ? fallback.x() : x,
                y == null ? fallback.y() : y,
                z == null ? fallback.z() : z
        );
    }

    private String normalizeBillboard(String value) {
        return switch (Texts.lower(Texts.toStringSafe(value).trim())) {
            case "fixed" -> "fixed";
            case "vertical" -> "vertical";
            case "horizontal" -> "horizontal";
            default -> "center";
        };
    }

    public boolean matchesInteraction(StationType stationType,
            String operation,
            StationInteraction interaction) {
        if (stationType == null || Texts.isBlank(operation) || interaction == null) {
            return false;
        }
        StationInteractionType configured = null;
        YamlSection interactions = configuration.getSection(stationPath(stationType) + ".interactions");
        if (interactions != null && !interactions.isEmpty()) {
            configured = StationInteractionType.parse(interactions.getString(operation, ""));
        }
        if (configured == null) {
            configured = defaultInteraction(stationType, operation);
        }
        return interaction.matches(configured);
    }

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

    public boolean choppingDropResult() {
        return choppingBoardSettings.dropResult();
    }

    public boolean choppingSpaceRestriction() {
        return choppingBoardSettings.spaceRestriction();
    }

    public long choppingInteractionDelayMs() {
        return choppingBoardSettings.interactionDelayMs();
    }

    public List<ItemSourceRef> choppingToolSources() {
        return choppingBoardSettings.toolSources();
    }

    public boolean choppingCutDamageEnabled() {
        return choppingBoardSettings.cutDamageEnabled();
    }

    public int choppingCutDamageChance() {
        return choppingBoardSettings.cutDamageChance();
    }

    public int choppingCutDamageValue() {
        return choppingBoardSettings.cutDamageValue();
    }

    public boolean grinderDropResult() {
        return grinderSettings.dropResult();
    }

    public int grinderCheckDelayTicks() {
        return grinderSettings.checkDelayTicks();
    }

    public boolean wokDropResult() {
        return wokSettings.dropResult();
    }

    public boolean wokNeedBowl() {
        return wokSettings.needBowl();
    }

    public long wokStirDelayMs() {
        return wokSettings.stirDelayMs();
    }

    public long wokTimeoutMs() {
        return wokSettings.timeoutMs();
    }

    public List<ItemSourceRef> wokSpatulaSources() {
        return wokSettings.spatulaSources();
    }

    public List<HeatLevelRule> wokHeatLevels() {
        return wokSettings.heatLevels();
    }

    public boolean wokIgniteHeatSource() {
        return wokSettings.igniteHeatSource();
    }

    public boolean wokScaldDamageEnabled() {
        return wokSettings.scaldDamageEnabled();
    }

    public int wokScaldDamageValue() {
        return wokSettings.scaldDamageValue();
    }

    public boolean wokStirAnimationEnabled() {
        return wokSettings.stirAnimationEnabled();
    }

    public int wokStirAnimationDurationTicks() {
        return wokSettings.stirAnimationDurationTicks();
    }

    public double wokStirAnimationHeight() {
        return wokSettings.stirAnimationHeight();
    }

    public String wokStirAnimationAxis() {
        return wokSettings.stirAnimationAxis();
    }

    public double wokStirAnimationRotation() {
        return wokSettings.stirAnimationRotation();
    }

    public boolean wokFailureEnabled() {
        return wokSettings.failureEnabled();
    }

    public int wokFailureChance() {
        return wokSettings.failureChance();
    }

    public String wokFailureOutputSource() {
        return wokSettings.failureOutputSource();
    }

    public String wokInvalidResultSource() {
        return wokSettings.invalidResultSource();
    }

    public boolean steamerDropResult() {
        return steamerSettings.dropResult();
    }

    public String steamerInventoryTitle() {
        return steamerSettings.inventoryTitle();
    }

    public int steamerInventoryRows() {
        return steamerSettings.inventoryRows();
    }

    public List<Integer> steamerIngredientSlots() {
        return steamerSettings.ingredientSlots();
    }

    public List<ItemSourceRef> steamerHeatSources() {
        return steamerSettings.heatSources();
    }

    public List<HeatSourceIgnitionRule> steamerHeatSourceIgnitionRules() {
        return steamerSettings.heatSourceIgnitionRules();
    }

    public boolean steamerIgniteHeatSource() {
        return steamerSettings.igniteHeatSource();
    }

    public List<SteamerFuelRule> steamerFuels() {
        return steamerSettings.fuels();
    }

    public List<SteamerMoistureRule> steamerMoistureSources() {
        return steamerSettings.moistureSources();
    }

    public boolean steamerResetProgressWhenSteamEmpty() {
        return steamerSettings.resetProgressWhenSteamEmpty();
    }

    public int steamerSteamProductionEfficiency() {
        return steamerSettings.steamProductionEfficiency();
    }

    public int steamerSteamConversionEfficiency() {
        return steamerSettings.steamConversionEfficiency();
    }

    public int steamerSteamConsumptionEfficiency() {
        return steamerSettings.steamConsumptionEfficiency();
    }

    public boolean ovenDropResult() {
        return ovenSettings.dropResult();
    }

    public String ovenInventoryTitle() {
        return ovenSettings.inventoryTitle();
    }

    public int ovenInventoryRows() {
        return ovenSettings.inventoryRows();
    }

    public List<Integer> ovenIngredientSlots() {
        return ovenSettings.ingredientSlots();
    }

    public List<OvenFuelRule> ovenFuels() {
        return ovenSettings.fuels();
    }

    public int ovenHeatMin() {
        return ovenSettings.heatMin();
    }

    public int ovenHeatMax() {
        return ovenSettings.heatMax();
    }

    public int ovenHeatDecayPerSecond() {
        return ovenSettings.heatDecayPerSecond();
    }

    public boolean juicerDropResult() {
        return juicerSettings.dropResult();
    }

    public boolean juicerRequireContainer() {
        return juicerSettings.requireContainer();
    }

    public List<ItemSourceRef> juicerContainerSources() {
        return juicerSettings.containerSources();
    }

    public int juicerMaxFluidMl() {
        return juicerSettings.maxFluidMl();
    }

    public int juicerDefaultServingMl() {
        return juicerSettings.defaultServingMl();
    }

    public String juicerInventoryTitle() {
        return juicerSettings.inventoryTitle();
    }

    public int juicerInventoryRows() {
        return juicerSettings.inventoryRows();
    }

    public List<Integer> juicerIngredientSlots() {
        return juicerSettings.ingredientSlots();
    }

    public boolean fermentationBarrelDropResult() {
        return fermentationBarrelSettings.dropResult();
    }

    public boolean fermentationBarrelPauseWhenOpen() {
        return fermentationBarrelSettings.pauseWhenOpen();
    }

    public String fermentationBarrelInventoryTitle() {
        return fermentationBarrelSettings.inventoryTitle();
    }

    public int fermentationBarrelInventoryRows() {
        return fermentationBarrelSettings.inventoryRows();
    }

    public List<Integer> fermentationBarrelIngredientSlots() {
        return fermentationBarrelSettings.ingredientSlots();
    }

    static List<Integer> ingredientSlots(YamlSection guiConfiguration, int inventorySize, int fallbackCount) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        YamlSection slotsSection = guiConfiguration.getSection("slots");
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
            for (int slot = 0; slot < Math.min(fallbackCount, inventorySize); slot++) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    static List<ItemSourceRef> parseSources(Object raw) {
        List<ItemSourceRef> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            ItemSourceRef source = ItemSourceUtil.parse(token);
            if (source != null) {
                result.add(source);
            }
        }
        return List.copyOf(result);
    }

    static ItemSourceRef parseLitSource(Map<String, Object> normalized) {
        return ItemSourceUtil.parse(firstPresent(normalized, LIT_SOURCE_KEYS));
    }

    static ItemSourceRef parseUnlitSource(Map<String, Object> normalized) {
        return ItemSourceUtil.parse(firstPresent(normalized, UNLIT_SOURCE_KEYS));
    }

    static Object firstPresent(Map<String, Object> values, String... keys) {
        if (values == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private String stationPath(StationType stationType) {
        return "stations." + stationType.folderName();
    }

    private StationInteractionType defaultInteraction(StationType stationType, String operation) {
        return switch (stationType) {
            case CHOPPING_BOARD -> switch (operation) {
                case INTERACTION_PLACE_INPUT, INTERACTION_PROCESS -> StationInteractionType.SHIFT_LEFT_CLICK;
                case INTERACTION_RETURN_INPUT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case WOK -> switch (operation) {
                case INTERACTION_ADD_INGREDIENT, INTERACTION_STIR, INTERACTION_SERVE, INTERACTION_RETURN_INGREDIENT -> StationInteractionType.SHIFT_LEFT_CLICK;
                case INTERACTION_INSPECT -> StationInteractionType.SHIFT_RIGHT_CLICK;
                default -> null;
            };
            case GRINDER -> INTERACTION_START.equals(operation) ? StationInteractionType.SHIFT_LEFT_CLICK : null;
            case STEAMER -> switch (operation) {
                case INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case INTERACTION_FUEL, INTERACTION_MOISTURE -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case OVEN -> switch (operation) {
                case INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case INTERACTION_FUEL, INTERACTION_INSPECT -> StationInteractionType.SHIFT_LEFT_CLICK;
                default -> null;
            };
            case JUICER -> switch (operation) {
                case INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case INTERACTION_PROCESS, INTERACTION_SERVE -> StationInteractionType.SHIFT_LEFT_CLICK;
                case INTERACTION_INSPECT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case FERMENTATION_BARREL -> switch (operation) {
                case INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case INTERACTION_START, INTERACTION_SERVE -> StationInteractionType.SHIFT_LEFT_CLICK;
                case INTERACTION_INSPECT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
        };
    }

    static Integer configurationValueToInt(Object raw, Integer fallback) {
        return Numbers.tryParseInt(raw, fallback);
    }

    private static void addSlotIndexes(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
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

    private static void addSlotIndex(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
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

    private Map<StationType, Set<String>> loadStationDisabledWorlds() {
        EnumMap<StationType, Set<String>> result = new EnumMap<>(StationType.class);
        for (StationType stationType : StationType.values()) {
            Set<String> worlds = disabledWorldSet(configuration.get(stationPath(stationType) + ".disabled_worlds"));
            if (!worlds.isEmpty()) {
                result.put(stationType, worlds);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private Set<String> disabledWorldSet(Object raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            String world = normalizeWorldName(Texts.toStringSafe(token));
            if (!world.isBlank()) {
                result.add(world);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    private String normalizeWorldName(String worldName) {
        return Texts.toStringSafe(worldName).trim().toLowerCase(Locale.ROOT);
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

    private boolean isYamlFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private ItemDisplayAdjustmentOverride parseItemDisplayAdjustment(Path file, YamlSection section) {
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
        return Numbers.tryParseDouble(raw, fallback);
    }

    public record HeatLevelRule(ItemSourceRef source, ItemSourceRef litSource, ItemSourceRef unlitSource, int level) {
    }

    public record HeatSourceIgnitionRule(ItemSourceRef source, ItemSourceRef litSource, ItemSourceRef unlitSource) {
    }

    public record SteamerFuelRule(ItemSourceRef source, int durationSeconds) {
    }

    public record SteamerMoistureRule(ItemSourceRef inputSource, ItemSourceRef outputSource, int moisture) {
    }

    public record OvenFuelRule(ItemSourceRef source, int durationSeconds, int heat) {
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


    public List<String> getStationActions(StationType stationType, String operation) {
        if (stationType == null || Texts.isBlank(operation)) {
            return List.of();
        }
        String path = "stations." + stationType.folderName() + ".actions." + operation;
        List<String> actions = configuration.getStringList(path);
        return actions == null ? List.of() : actions;
    }



    public boolean nutritionEnabled() {
        return configuration.getBoolean("nutrition.enabled", true);
    }

    public int nutritionSaveIntervalSeconds() {
        return Math.max(0, configuration.getInt("nutrition.save_interval_seconds", 300));
    }




    public List<NutritionFoodSource> nutritionFoodSources() {
        List<NutritionFoodSource> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("nutrition.food_sources")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            List<ItemSourceRef> sources = parseSources(normalized.get("item_sources"));
            if (sources.isEmpty()) {
                continue;
            }
            Map<String, Double> nutrition = parseNutritionAmounts(normalized.get("nutrition"));
            List<String> actions = stringList(normalized.get("actions"));
            if (nutrition.isEmpty() && actions.isEmpty()) {
                continue;
            }
            result.add(new NutritionFoodSource(sources, nutrition, actions));
        }
        return List.copyOf(result);
    }




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

    private Map<String, Double> parseNutritionAmounts(Object raw) {
        Map<String, Object> normalized = MapYamlSection.normalizeMap(raw instanceof Map<?, ?> map ? map : Map.of());
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            String typeId = Texts.normalizeId(entry.getKey());
            if (Texts.isBlank(typeId)) {
                continue;
            }
            Double amount = configurationValueToDouble(entry.getValue(), Double.NaN);
            if (amount != null && !amount.isNaN()) {
                result.put(typeId, amount);
            }
        }
        return result;
    }

    private List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            String value = Texts.toStringSafe(token);
            if (Texts.isNotBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<String> normalizeIds(List<String> raw) {
        List<String> result = new ArrayList<>();
        for (String token : raw) {
            String id = Texts.normalizeId(token);
            if (Texts.isNotBlank(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private Double configurationValueToDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && Texts.isNotBlank(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}

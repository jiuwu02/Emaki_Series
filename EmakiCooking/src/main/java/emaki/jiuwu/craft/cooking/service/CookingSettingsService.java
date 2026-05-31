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

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
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

    // 配置中"点燃状态"和"熄灭状态"的兼容 key 列表，在 wokHeatLevels 和 parseHeatSourceIgnitionRules 中复用
    private static final String[] LIT_SOURCE_KEYS = {"lit_item_sources", "lit_source", "ignited_item_sources", "ignited_source", "on_item_sources", "on_source"};
    private static final String[] UNLIT_SOURCE_KEYS = {"unlit_item_sources", "unlit_source", "extinguished_item_sources", "extinguished_source", "off_item_sources", "off_source"};
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

    public CookingSettingsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        configuration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        steamerGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("steamer.yml").toFile());
        ovenGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("oven.yml").toFile());
        juicerGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("juicer.yml").toFile());
        fermentationBarrelGuiConfiguration = YamlFiles.load(plugin.getDataFolder().toPath().resolve("gui").resolve("fermentation_barrel.yml").toFile());
        itemAdjustments = loadItemAdjustments();
    }

    public ItemSource stationBlockSource(StationType stationType) {
        return ItemSourceUtil.parse(configuration.get(stationPath(stationType) + ".block_item_sources"));
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
        return Math.max(0D, configuration.getDouble("display_entities.wok.layout_radius", 0.26D));
    }

    // ========== 文本展示实体 ==========

    /**
     * 指定工位是否启用文本展示实体（全局开关 AND 工位级开关）。
     */
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

    /**
     * 解析指定工位的文本展示渲染参数（offset/scale 优先取工位级覆盖，回退全局默认）。
     */
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
        return parseSources(configuration.get("stations.chopping_board.tool_item_sources"));
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
        return parseSources(configuration.get("stations.wok.spatula_item_sources"));
    }

    public List<HeatLevelRule> wokHeatLevels() {
        List<HeatLevelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.wok.heat_levels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource source = ItemSourceUtil.parse(normalized.get("item_sources"));
            if (source == null) {
                continue;
            }
            ItemSource litSource = ItemSourceUtil.parse(firstPresent(
                    normalized,
                    LIT_SOURCE_KEYS
            ));
            ItemSource unlitSource = ItemSourceUtil.parse(firstPresent(
                    normalized,
                    UNLIT_SOURCE_KEYS
            ));
            Integer level = configurationValueToInt(normalized.get("level"), 0);
            result.add(new HeatLevelRule(source, litSource, unlitSource == null ? source : unlitSource, level == null ? 0 : Math.max(0, level)));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public boolean wokIgniteHeatSource() {
        return configuration.getBoolean("stations.wok.ignite_heat_source", true);
    }

    public boolean wokScaldDamageEnabled() {
        return configuration.getBoolean("stations.wok.scald_damage.enabled", true);
    }

    public int wokScaldDamageValue() {
        return Math.max(0, configuration.getInt("stations.wok.scald_damage.value", 2));
    }

    public boolean wokStirAnimationEnabled() {
        return configuration.getBoolean("stations.wok.stir_animation.enabled", true);
    }

    public int wokStirAnimationDurationTicks() {
        return Math.max(2, configuration.getInt("stations.wok.stir_animation.duration_ticks", 10));
    }

    public double wokStirAnimationHeight() {
        return Math.max(0.0D, configuration.getDouble("stations.wok.stir_animation.height", 0.4D));
    }

    public String wokStirAnimationAxis() {
        String axis = Texts.lower(configuration.getString("stations.wok.stir_animation.rotation_axis", "x"));
        return switch (axis) {
            case "x", "y", "z" -> axis;
            default -> "x";
        };
    }

    public double wokStirAnimationRotation() {
        return configuration.getDouble("stations.wok.stir_animation.rotation_degrees", 360.0D);
    }

    public boolean wokFailureEnabled() {
        return configuration.getBoolean("stations.wok.failure.enabled", true);
    }

    public int wokFailureChance() {
        return Math.max(0, configuration.getInt("stations.wok.failure.chance", 5));
    }

    public String wokFailureOutputSource() {
        return firstSourceShorthand(configuration.get("stations.wok.failure.item_sources"));
    }

    public String wokInvalidResultSource() {
        return firstSourceShorthand(configuration.get("stations.wok.invalid_result_item_sources"));
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
        return ingredientSlots(steamerGuiConfiguration, steamerInventoryRows() * 9, 5);
    }

    public List<ItemSource> steamerHeatSources() {
        return parseSources(configuration.get("stations.steamer.heat_item_sources"));
    }

    public List<HeatSourceIgnitionRule> steamerHeatSourceIgnitionRules() {
        return parseHeatSourceIgnitionRules(configuration.get("stations.steamer.heat_item_sources"));
    }

    public boolean steamerIgniteHeatSource() {
        return configuration.getBoolean("stations.steamer.ignite_heat_source", true);
    }

    public List<SteamerFuelRule> steamerFuels() {
        List<SteamerFuelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.steamer.fuels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource source = ItemSourceUtil.parse(normalized.get("item_sources"));
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
        for (Map<?, ?> entry : configuration.getMapList("stations.steamer.moisture_rules")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource input = ItemSourceUtil.parse(normalized.get("input_item_sources"));
            if (input == null) {
                continue;
            }
            ItemSource output = ItemSourceUtil.parse(normalized.get("item_sources"));
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

    public boolean ovenDropResult() {
        return configuration.getBoolean("stations.oven.drop_result", true);
    }

    public String ovenInventoryTitle() {
        return ovenGuiConfiguration.getString("title", "<dark_gray>烤炉");
    }

    public int ovenInventoryRows() {
        int rows = ovenGuiConfiguration.getInt("rows", 1);
        return Math.max(1, Math.min(6, rows));
    }

    public List<Integer> ovenIngredientSlots() {
        return ingredientSlots(ovenGuiConfiguration, ovenInventoryRows() * 9, 5);
    }

    public List<OvenFuelRule> ovenFuels() {
        List<OvenFuelRule> result = new ArrayList<>();
        for (Map<?, ?> entry : configuration.getMapList("stations.oven.fuels")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            ItemSource source = ItemSourceUtil.parse(normalized.get("item_sources"));
            if (source == null) {
                continue;
            }
            Integer duration = configurationValueToInt(normalized.get("duration_seconds"), 0);
            Integer heat = configurationValueToInt(normalized.get("heat"), 0);
            result.add(new OvenFuelRule(
                    source,
                    duration == null ? 0 : Math.max(0, duration),
                    heat == null ? 0 : Math.max(0, heat)
            ));
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public int ovenHeatMin() {
        return Math.max(0, configuration.getInt("stations.oven.heat.min", 20));
    }

    public int ovenHeatMax() {
        return Math.max(ovenHeatMin(), configuration.getInt("stations.oven.heat.max", 80));
    }

    public int ovenHeatDecayPerSecond() {
        return Math.max(0, configuration.getInt("stations.oven.heat.decay_per_second", 5));
    }

    public boolean juicerDropResult() {
        return configuration.getBoolean("stations.juicer.drop_result", true);
    }

    public boolean juicerRequireContainer() {
        return configuration.getBoolean("stations.juicer.require_container", true);
    }

    public List<ItemSource> juicerContainerSources() {
        return parseSources(configuration.get("stations.juicer.container_item_sources"));
    }

    public int juicerMaxFluidMl() {
        return Math.max(1, configuration.getInt("stations.juicer.max_fluid_ml", 1000));
    }

    public int juicerDefaultServingMl() {
        return Math.max(1, configuration.getInt("stations.juicer.default_serving_ml", 250));
    }

    public String juicerInventoryTitle() {
        return juicerGuiConfiguration.getString("title", "<dark_gray>榨汁机");
    }

    public int juicerInventoryRows() {
        return Math.max(1, Math.min(6, juicerGuiConfiguration.getInt("rows", 1)));
    }

    public List<Integer> juicerIngredientSlots() {
        return ingredientSlots(juicerGuiConfiguration, juicerInventoryRows() * 9, 5);
    }

    public boolean fermentationBarrelDropResult() {
        return configuration.getBoolean("stations.fermentation_barrel.drop_result", true);
    }

    public boolean fermentationBarrelPauseWhenOpen() {
        return configuration.getBoolean("stations.fermentation_barrel.pause_when_open", true);
    }

    public String fermentationBarrelInventoryTitle() {
        return fermentationBarrelGuiConfiguration.getString("title", "<dark_gray>发酵桶");
    }

    public int fermentationBarrelInventoryRows() {
        return Math.max(1, Math.min(6, fermentationBarrelGuiConfiguration.getInt("rows", 3)));
    }

    public List<Integer> fermentationBarrelIngredientSlots() {
        return ingredientSlots(fermentationBarrelGuiConfiguration, fermentationBarrelInventoryRows() * 9, 7);
    }

    private List<Integer> ingredientSlots(YamlSection guiConfiguration, int inventorySize, int fallbackCount) {
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

    private List<ItemSource> parseSources(Object raw) {
        List<ItemSource> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            ItemSource source = ItemSourceUtil.parse(token);
            if (source != null) {
                result.add(source);
            }
        }
        return List.copyOf(result);
    }

    private List<HeatSourceIgnitionRule> parseHeatSourceIgnitionRules(Object raw) {
        List<HeatSourceIgnitionRule> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            if (token instanceof Map<?, ?> map) {
                Map<String, Object> normalized = MapYamlSection.normalizeMap(map);
                ItemSource source = ItemSourceUtil.parse(firstPresent(
                        normalized,
                        "item_sources",
                        "source",
                        "item"
                ));
                if (source == null) {
                    continue;
                }
                ItemSource litSource = ItemSourceUtil.parse(firstPresent(
                        normalized,
                        LIT_SOURCE_KEYS
                ));
                ItemSource unlitSource = ItemSourceUtil.parse(firstPresent(
                        normalized,
                        UNLIT_SOURCE_KEYS
                ));
                result.add(new HeatSourceIgnitionRule(source, litSource, unlitSource == null ? source : unlitSource));
                continue;
            }
            ItemSource source = ItemSourceUtil.parse(token);
            if (source != null) {
                result.add(new HeatSourceIgnitionRule(source, null, source));
            }
        }
        return List.copyOf(result);
    }

    private Object firstPresent(Map<String, Object> values, String... keys) {
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

    private String firstSourceShorthand(Object raw) {
        ItemSource source = ItemSourceUtil.parse(raw);
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : shorthand;
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
                case INTERACTION_INSPECT -> StationInteractionType.LEFT_CLICK;
                default -> null;
            };
            case FERMENTATION_BARREL -> switch (operation) {
                case INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case INTERACTION_START, INTERACTION_SERVE -> StationInteractionType.SHIFT_LEFT_CLICK;
                case INTERACTION_INSPECT -> StationInteractionType.LEFT_CLICK;
                default -> null;
            };
        };
    }

    private Integer configurationValueToInt(Object raw, Integer fallback) {
        return Numbers.tryParseInt(raw, fallback);
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
        ItemSource source = ItemSourceUtil.parse(section.get("item_sources"));
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

    public record HeatLevelRule(ItemSource source, ItemSource litSource, ItemSource unlitSource, int level) {
    }

    public record HeatSourceIgnitionRule(ItemSource source, ItemSource litSource, ItemSource unlitSource) {
    }

    public record SteamerFuelRule(ItemSource source, int durationSeconds) {
    }

    public record SteamerMoistureRule(ItemSource inputSource, ItemSource outputSource, int moisture) {
    }

    public record OvenFuelRule(ItemSource source, int durationSeconds, int heat) {
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

    // ========== 工位操作动作 ==========

    /**
     * 获取指定工位操作的动作列表。
     * 配置路径: {@code stations.<type>.actions.<operation>}
     *
     * @param stationType 工位类型
     * @param operation   操作名（如 "stir", "cut", "complete" 等）
     * @return 动作行列表，如果未配置则返回空列表
     */
    public List<String> getStationActions(StationType stationType, String operation) {
        if (stationType == null || Texts.isBlank(operation)) {
            return List.of();
        }
        String path = "stations." + stationType.folderName() + ".actions." + operation;
        List<String> actions = configuration.getStringList(path);
        return actions == null ? List.of() : actions;
    }
}

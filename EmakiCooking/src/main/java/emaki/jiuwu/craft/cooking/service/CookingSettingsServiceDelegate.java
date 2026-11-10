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

final class CookingSettingsServiceDelegate {

    private final CookingSettingsService ownerRef;

    CookingSettingsServiceDelegate(CookingSettingsService ownerRef) {
        this.ownerRef = ownerRef;
    }

    static String deprecatedKeyHint(boolean nested, String nestedHint) {
        return nested ? nestedHint : "旧键仅作兼容读取";
    }

    List<ItemSourceRef> stationBlockSources(StationType stationType) {
        if (stationType == null) {
            return List.of();
        }
        List<ItemSourceRef> sources = ownerRef.stationBlockSourceCache.get(stationType);
        return sources == null ? List.of() : sources;
    }

    ItemSourceRef stationBlockSource(StationType stationType) {
        List<ItemSourceRef> sources = ownerRef.stationBlockSources(stationType);
        return sources.isEmpty() ? null : sources.getFirst();
    }

    boolean isInteractionDisabled(StationType stationType, String worldName) {
        String world = ownerRef.normalizeWorldName(worldName);
        if (world.isBlank()) {
            return false;
        }
        if (ownerRef.globalDisabledWorlds.contains(world)) {
            return true;
        }
        Set<String> stationWorlds = stationType == null ? null : ownerRef.stationDisabledWorlds.get(stationType);
        return stationWorlds != null && stationWorlds.contains(world);
    }

    Set<String> globalDisabledWorlds() {
        return ownerRef.globalDisabledWorlds;
    }

    Set<String> disabledWorlds(StationType stationType) {
        Set<String> worlds = stationType == null ? null : ownerRef.stationDisabledWorlds.get(stationType);
        return worlds == null ? Set.of() : worlds;
    }

    String displayEntitiesBackend() {
        String backend = Texts.normalizeId(ownerRef.configuration.getString("display_entities.backend", "auto"));
        return switch (backend) {
            case "packet_events", "bukkit" -> backend;
            default -> "auto";
        };
    }

    double displayEntitiesViewDistanceBlocks() {
        return Math.max(1D, ownerRef.configuration.getDouble("display_entities.view_distance_blocks", 48D));
    }

    int displayEntitiesRefreshIntervalTicks() {
        return Math.max(1, ownerRef.configuration.getInt("display_entities.refresh_interval_ticks", 20));
    }

    double wokDisplayLayoutRadius() {
        return ownerRef.wokSettings.displayLayoutRadius();
    }

    boolean textDisplayEnabled(StationType stationType) {
        if (stationType == null) {
            return false;
        }
        if (!ownerRef.configuration.getBoolean("display_entities.text.enabled", true)) {
            return false;
        }
        String stationPath = "display_entities.text.stations." + stationType.folderName() + ".enabled";
        return ownerRef.configuration.getBoolean(stationPath, true);
    }

    CookingSettingsService.Vector3 readVector3(YamlSection section, CookingSettingsService.Vector3 fallback) {
        if (section == null || section.isEmpty()) {
            return fallback;
        }
        Double x = section.getDouble("x", null);
        Double y = section.getDouble("y", null);
        Double z = section.getDouble("z", null);
        if (x == null && y == null && z == null) {
            return fallback;
        }
        return new CookingSettingsService.Vector3(
                x == null ? fallback.x() : x,
                y == null ? fallback.y() : y,
                z == null ? fallback.z() : z
        );
    }

    String normalizeBillboard(String value) {
        return switch (Texts.lower(Texts.toStringSafe(value).trim())) {
            case "fixed" -> "fixed";
            case "vertical" -> "vertical";
            case "horizontal" -> "horizontal";
            default -> "center";
        };
    }

    boolean matchesInteraction(StationType stationType, String operation, StationInteraction interaction) {
        if (stationType == null || Texts.isBlank(operation) || interaction == null) {
            return false;
        }
        StationInteractionType configured = null;
        YamlSection interactions = ownerRef.configuration.getSection(ownerRef.stationPath(stationType) + ".interactions");
        if (interactions != null && !interactions.isEmpty()) {
            configured = StationInteractionType.parse(interactions.getString(operation, ""));
        }
        if (configured == null) {
            configured = ownerRef.defaultInteraction(stationType, operation);
        }
        return interaction.matches(configured);
    }

    boolean choppingDropResult() {
        return ownerRef.choppingBoardSettings.dropResult();
    }

    boolean choppingSpaceRestriction() {
        return ownerRef.choppingBoardSettings.spaceRestriction();
    }

    long choppingInteractionDelayMs() {
        return ownerRef.choppingBoardSettings.interactionDelayMs();
    }

    ItemRequirement choppingToolRequirement() {
        return ownerRef.choppingBoardSettings.toolRequirement();
    }

    boolean choppingCutDamageEnabled() {
        return ownerRef.choppingBoardSettings.cutDamageEnabled();
    }

    int choppingCutDamageChance() {
        return ownerRef.choppingBoardSettings.cutDamageChance();
    }

    int choppingCutDamageValue() {
        return ownerRef.choppingBoardSettings.cutDamageValue();
    }

    boolean grinderDropResult() {
        return ownerRef.grinderSettings.dropResult();
    }

    int grinderCheckDelayTicks() {
        return ownerRef.grinderSettings.checkDelayTicks();
    }

    boolean wokDropResult() {
        return ownerRef.wokSettings.dropResult();
    }

    boolean wokNeedBowl() {
        return ownerRef.wokSettings.needBowl();
    }

    long wokStirDelayMs() {
        return ownerRef.wokSettings.stirDelayMs();
    }

    long wokTimeoutMs() {
        return ownerRef.wokSettings.timeoutMs();
    }

    ItemRequirement wokSpatulaRequirement() {
        return ownerRef.wokSettings.spatulaRequirement();
    }

    List<CookingSettingsService.HeatLevelRule> wokHeatLevels() {
        return ownerRef.wokSettings.heatLevels();
    }

    boolean wokIgniteHeatSource() {
        return ownerRef.wokSettings.igniteHeatSource();
    }

    boolean wokScaldDamageEnabled() {
        return ownerRef.wokSettings.scaldDamageEnabled();
    }

    int wokScaldDamageValue() {
        return ownerRef.wokSettings.scaldDamageValue();
    }

    boolean wokStirAnimationEnabled() {
        return ownerRef.wokSettings.stirAnimationEnabled();
    }

    int wokStirAnimationDurationTicks() {
        return ownerRef.wokSettings.stirAnimationDurationTicks();
    }

    double wokStirAnimationHeight() {
        return ownerRef.wokSettings.stirAnimationHeight();
    }

    String wokStirAnimationAxis() {
        return ownerRef.wokSettings.stirAnimationAxis();
    }

    double wokStirAnimationRotation() {
        return ownerRef.wokSettings.stirAnimationRotation();
    }

    boolean wokFailureEnabled() {
        return ownerRef.wokSettings.failureEnabled();
    }

    int wokFailureChance() {
        return ownerRef.wokSettings.failureChance();
    }

    String wokFailureOutputSource() {
        return ownerRef.wokSettings.failureOutputSource();
    }

    String wokInvalidResultSource() {
        return ownerRef.wokSettings.invalidResultSource();
    }

    boolean steamerDropResult() {
        return ownerRef.steamerSettings.dropResult();
    }

    String steamerInventoryTitle() {
        return ownerRef.steamerSettings.inventoryTitle();
    }

    int steamerInventoryRows() {
        return ownerRef.steamerSettings.inventoryRows();
    }

    List<Integer> steamerIngredientSlots() {
        return ownerRef.steamerSettings.ingredientSlots();
    }

    List<ItemSourceRef> steamerHeatSources() {
        return ownerRef.steamerSettings.heatSources();
    }

    List<CookingSettingsService.HeatSourceIgnitionRule> steamerHeatSourceIgnitionRules() {
        return ownerRef.steamerSettings.heatSourceIgnitionRules();
    }

    boolean steamerIgniteHeatSource() {
        return ownerRef.steamerSettings.igniteHeatSource();
    }

    List<CookingSettingsService.SteamerFuelRule> steamerFuels() {
        return ownerRef.steamerSettings.fuels();
    }

    List<CookingSettingsService.SteamerMoistureRule> steamerMoistureSources() {
        return ownerRef.steamerSettings.moistureSources();
    }

    boolean steamerResetProgressWhenSteamEmpty() {
        return ownerRef.steamerSettings.resetProgressWhenSteamEmpty();
    }

    int steamerSteamProductionEfficiency() {
        return ownerRef.steamerSettings.steamProductionEfficiency();
    }

    int steamerSteamConversionEfficiency() {
        return ownerRef.steamerSettings.steamConversionEfficiency();
    }

    int steamerSteamConsumptionEfficiency() {
        return ownerRef.steamerSettings.steamConsumptionEfficiency();
    }

    boolean ovenDropResult() {
        return ownerRef.ovenSettings.dropResult();
    }

    String ovenInventoryTitle() {
        return ownerRef.ovenSettings.inventoryTitle();
    }

    int ovenInventoryRows() {
        return ownerRef.ovenSettings.inventoryRows();
    }

    List<Integer> ovenIngredientSlots() {
        return ownerRef.ovenSettings.ingredientSlots();
    }

    List<CookingSettingsService.OvenFuelRule> ovenFuels() {
        return ownerRef.ovenSettings.fuels();
    }

    int ovenHeatMin() {
        return ownerRef.ovenSettings.heatMin();
    }

    int ovenHeatMax() {
        return ownerRef.ovenSettings.heatMax();
    }

    int ovenHeatDecayPerSecond() {
        return ownerRef.ovenSettings.heatDecayPerSecond();
    }

    boolean juicerDropResult() {
        return ownerRef.juicerSettings.dropResult();
    }

    boolean juicerRequireContainer() {
        return ownerRef.juicerSettings.requireContainer();
    }

    ItemRequirement juicerContainerRequirement() {
        return ownerRef.juicerSettings.containerRequirement();
    }

    int juicerMaxFluidMl() {
        return ownerRef.juicerSettings.maxFluidMl();
    }

    int juicerDefaultServingMl() {
        return ownerRef.juicerSettings.defaultServingMl();
    }

    String juicerInventoryTitle() {
        return ownerRef.juicerSettings.inventoryTitle();
    }

    int juicerInventoryRows() {
        return ownerRef.juicerSettings.inventoryRows();
    }

    List<Integer> juicerIngredientSlots() {
        return ownerRef.juicerSettings.ingredientSlots();
    }

    boolean fermentationBarrelDropResult() {
        return ownerRef.fermentationBarrelSettings.dropResult();
    }

    boolean fermentationBarrelPauseWhenOpen() {
        return ownerRef.fermentationBarrelSettings.pauseWhenOpen();
    }

    String fermentationBarrelInventoryTitle() {
        return ownerRef.fermentationBarrelSettings.inventoryTitle();
    }

    int fermentationBarrelInventoryRows() {
        return ownerRef.fermentationBarrelSettings.inventoryRows();
    }

    List<Integer> fermentationBarrelIngredientSlots() {
        return ownerRef.fermentationBarrelSettings.ingredientSlots();
    }

    static List<Integer> ingredientSlots(YamlSection guiConfiguration, int inventorySize, int fallbackCount) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        YamlSection slotsSection = guiConfiguration.getSection("slots");
        if (slotsSection != null && !slotsSection.isEmpty()) {
            for (String key : slotsSection.getKeys(false)) {
                YamlSection slotSection = slotsSection.getSection(key);
                if (slotSection == null || slotSection.isEmpty()) {
                    CookingSettingsService.addSlotIndexes(slots, slotsSection.get(key), inventorySize);
                    continue;
                }
                String type = Texts.lower(slotSection.getString("type", ""));
                if (Texts.isNotBlank(type) && !"ingredient".equals(type)) {
                    continue;
                }
                CookingSettingsService.addSlotIndexes(slots, slotSection.get("slots"), inventorySize);
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
        return ItemSourceUtil.parse(CookingSettingsService.firstPresent(normalized, CookingSettingsService.LIT_SOURCE_KEYS));
    }

    static ItemSourceRef parseUnlitSource(Map<String, Object> normalized) {
        return ItemSourceUtil.parse(CookingSettingsService.firstPresent(normalized, CookingSettingsService.UNLIT_SOURCE_KEYS));
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

    String stationPath(StationType stationType) {
        return "stations." + stationType.folderName();
    }

    StationInteractionType defaultInteraction(StationType stationType, String operation) {
        return switch (stationType) {
            case CHOPPING_BOARD -> switch (operation) {
                case CookingSettingsService.INTERACTION_PLACE_INPUT, CookingSettingsService.INTERACTION_PROCESS -> StationInteractionType.SHIFT_LEFT_CLICK;
                case CookingSettingsService.INTERACTION_RETURN_INPUT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case WOK -> switch (operation) {
                case CookingSettingsService.INTERACTION_ADD_INGREDIENT, CookingSettingsService.INTERACTION_STIR, CookingSettingsService.INTERACTION_SERVE, CookingSettingsService.INTERACTION_RETURN_INGREDIENT -> StationInteractionType.SHIFT_LEFT_CLICK;
                case CookingSettingsService.INTERACTION_INSPECT -> StationInteractionType.SHIFT_RIGHT_CLICK;
                default -> null;
            };
            case GRINDER -> CookingSettingsService.INTERACTION_START.equals(operation) ? StationInteractionType.SHIFT_LEFT_CLICK : null;
            case STEAMER -> switch (operation) {
                case CookingSettingsService.INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case CookingSettingsService.INTERACTION_FUEL, CookingSettingsService.INTERACTION_MOISTURE -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case OVEN -> switch (operation) {
                case CookingSettingsService.INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case CookingSettingsService.INTERACTION_FUEL, CookingSettingsService.INTERACTION_INSPECT -> StationInteractionType.SHIFT_LEFT_CLICK;
                default -> null;
            };
            case JUICER -> switch (operation) {
                case CookingSettingsService.INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case CookingSettingsService.INTERACTION_PROCESS, CookingSettingsService.INTERACTION_SERVE -> StationInteractionType.SHIFT_LEFT_CLICK;
                case CookingSettingsService.INTERACTION_INSPECT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
            case FERMENTATION_BARREL -> switch (operation) {
                case CookingSettingsService.INTERACTION_OPEN -> StationInteractionType.SHIFT_RIGHT_CLICK;
                case CookingSettingsService.INTERACTION_START, CookingSettingsService.INTERACTION_SERVE -> StationInteractionType.SHIFT_LEFT_CLICK;
                case CookingSettingsService.INTERACTION_INSPECT -> StationInteractionType.RIGHT_CLICK;
                default -> null;
            };
        };
    }

    static Integer configurationValueToInt(Object raw, Integer fallback) {
        return Numbers.tryParseInt(raw, fallback);
    }

    static void addSlotIndexes(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
        if (sink == null || raw == null || inventorySize <= 0) {
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                CookingSettingsService.addSlotIndex(sink, entry, inventorySize);
            }
            return;
        }
        CookingSettingsService.addSlotIndex(sink, raw, inventorySize);
    }

    static void addSlotIndex(LinkedHashSet<Integer> sink, Object raw, int inventorySize) {
        Integer slot = CookingSettingsService.configurationValueToInt(raw, null);
        if (slot != null && slot >= 0 && slot < inventorySize) {
            sink.add(slot);
        }
    }

    Map<StationType, List<ItemSourceRef>> loadStationBlockSources() {
        EnumMap<StationType, List<ItemSourceRef>> result = new EnumMap<>(StationType.class);
        for (StationType stationType : StationType.values()) {
            result.put(stationType, parseSources(ownerRef.configuration.get(ownerRef.stationPath(stationType) + ".block_item_sources")));
        }
        return Map.copyOf(result);
    }

    Map<StationType, Set<String>> loadStationDisabledWorlds() {
        EnumMap<StationType, Set<String>> result = new EnumMap<>(StationType.class);
        for (StationType stationType : StationType.values()) {
            Set<String> worlds = ownerRef.disabledWorldSet(ownerRef.configuration.get(ownerRef.stationPath(stationType) + ".disabled_worlds"));
            if (!worlds.isEmpty()) {
                result.put(stationType, worlds);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    Set<String> disabledWorldSet(Object raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            String world = ownerRef.normalizeWorldName(Texts.toStringSafe(token));
            if (!world.isBlank()) {
                result.add(world);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    String normalizeWorldName(String worldName) {
        return Texts.toStringSafe(worldName).trim().toLowerCase(Locale.ROOT);
    }

    boolean isYamlFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    StationType resolveStationType(String value) {
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

    CookingSettingsService.PartialVector parsePartialVector(YamlSection section, String path, boolean allowScalar) {
        if (section == null || Texts.isBlank(path) || !section.contains(path)) {
            return null;
        }
        Object raw = section.get(path);
        if (allowScalar && raw instanceof Number scalar) {
            double value = scalar.doubleValue();
            return new CookingSettingsService.PartialVector(value, value, value);
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
        return new CookingSettingsService.PartialVector(x, y, z);
    }

    double parseDouble(String raw, double fallback) {
        return Numbers.tryParseDouble(raw, fallback);
    }

    static ItemRequirement requirementOf(ItemSourceRef source, Matcher matcher) {
        List<ItemSourceRef> sources = source == null ? List.of() : List.of(source);
        return new ItemRequirement(sources, matcher, ItemRequirement.sourceIdentity(sources));
    }

    List<String> getStationActions(StationType stationType, String operation) {
        if (stationType == null || Texts.isBlank(operation)) {
            return List.of();
        }
        String path = "stations." + stationType.folderName() + ".actions." + operation;
        List<String> actions = ownerRef.configuration.getStringList(path);
        return actions == null ? List.of() : actions;
    }

    boolean nutritionEnabled() {
        return ownerRef.configuration.getBoolean("nutrition.enabled", true);
    }

}

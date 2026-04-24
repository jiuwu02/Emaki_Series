package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridge;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class LegacyImportService {

    private static final String SCHEMA_VERSION = "2.0.0";
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WORLD_SEGMENT_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]+");

    private final EmakiCookingPlugin plugin;
    private final MessageService messageService;
    private final ItemSourceService itemSourceService;
    @SuppressWarnings("unused")
    private final CraftEngineBlockBridge craftEngineBlockBridge;

    public LegacyImportService(EmakiCookingPlugin plugin,
            MessageService messageService,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.itemSourceService = itemSourceService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
    }

    public LegacyImportResult dryRun() {
        return execute(false);
    }

    public LegacyImportResult apply() {
        return execute(true);
    }

    private LegacyImportResult execute(boolean apply) {
        ImportReport report = new ImportReport(apply);
        LegacyInputs inputs = loadInputs(report);
        ConversionBundle bundle = buildConversionBundle(inputs, report);
        Path backupPath = null;
        if (apply) {
            backupPath = backupCurrentRuntime(report);
            applyYamlUpdates("config.yml", bundle.configUpdates(), report);
            applyYamlUpdates("gui/steamer.yml", bundle.guiUpdates(), report);
            writeConvertedFiles(bundle.recipeFiles(), report);
            writeConvertedFiles(bundle.stationFiles(), report);
            writeConvertedFiles(bundle.adjustmentFiles(), report);
        }
        Path reportPath = writeReport(bundle, report, backupPath);
        return new LegacyImportResult(
                apply,
                reportPath == null ? "" : reportPath.toString(),
                backupPath == null ? null : backupPath.toString(),
                report.convertedRecipeCount(),
                report.convertedStationCount(),
                report.skippedCount(),
                report.conflictCount(),
                report.unknownSourceCount(),
                report.actionContextIssueCount()
        );
    }

    private LegacyInputs loadInputs(ImportReport report) {
        Path oldRoot = plugin.dataPath(plugin.appConfig().oldDirectory());
        report.oldDirectory = oldRoot.toString();
        return new LegacyInputs(
                oldRoot,
                loadYaml(oldRoot.resolve("Config.yml")),
                loadYaml(oldRoot.resolve("Recipe").resolve("ChoppingBoard.yml")),
                loadYaml(oldRoot.resolve("Recipe").resolve("Wok.yml")),
                loadYaml(oldRoot.resolve("Recipe").resolve("Grinder.yml")),
                loadYaml(oldRoot.resolve("Recipe").resolve("Steamer.yml")),
                loadYaml(oldRoot.resolve("Data.yml"))
        );
    }

    private ConversionBundle buildConversionBundle(LegacyInputs inputs, ImportReport report) {
        List<ConvertedFile> recipeFiles = new ArrayList<>();
        List<ConvertedFile> stationFiles = new ArrayList<>();
        List<ConvertedFile> adjustmentFiles = new ArrayList<>();
        Map<String, Object> configUpdates = buildConfigUpdates(inputs.config(), report);
        Map<String, Object> guiUpdates = buildGuiUpdates(inputs.config(), report);
        DisplayAdjustmentConversion displayAdjustments = buildDisplayAdjustmentConversion(inputs.config(), report);
        configUpdates.putAll(displayAdjustments.configUpdates());
        adjustmentFiles.addAll(displayAdjustments.files());

        convertChoppingBoardRecipes(inputs.choppingBoardRecipes(), report, recipeFiles);
        convertWokRecipes(inputs.wokRecipes(), inputs.config(), report, recipeFiles);
        convertGrinderRecipes(inputs.grinderRecipes(), report, recipeFiles);
        convertSteamerRecipes(inputs.steamerRecipes(), report, recipeFiles);
        convertStationStates(inputs.data(), report, stationFiles);

        if (recipeFiles.isEmpty() && stationFiles.isEmpty() && adjustmentFiles.isEmpty() && configUpdates.isEmpty() && guiUpdates.isEmpty()) {
            report.skipped.add("未在 old/ 目录中找到可导入的旧版数据。");
        }

        return new ConversionBundle(configUpdates, guiUpdates, recipeFiles, stationFiles, adjustmentFiles);
    }

    private Map<String, Object> buildConfigUpdates(YamlSection oldConfig, ImportReport report) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (oldConfig == null || oldConfig.isEmpty()) {
            return updates;
        }

        putUpdate(updates, report, "stations.chopping_board.block_source",
                convertLegacyBlockSource(
                        oldConfig.getBoolean("Setting.ChoppingBoard.Custom", false),
                        oldConfig.getString("Setting.ChoppingBoard.Material"),
                        "old/Config.yml:Setting.ChoppingBoard.Material",
                        report));
        putUpdate(updates, report, "stations.chopping_board.require_sneaking",
                oldConfig.getBoolean("Setting.ChoppingBoard.StealthInteraction", true));
        putUpdate(updates, report, "stations.chopping_board.drop_result",
                oldConfig.getBoolean("Setting.ChoppingBoard.Drop", true));
        putUpdate(updates, report, "stations.chopping_board.space_restriction",
                oldConfig.getBoolean("Setting.ChoppingBoard.SpaceRestriction", false));
        putUpdate(updates, report, "stations.chopping_board.interaction_delay_ms",
                Math.max(0, secondsToMillis(oldConfig.getDouble("Setting.ChoppingBoard.Delay", 1D))));
        putUpdate(updates, report, "stations.chopping_board.tool_sources",
                convertLegacySourceList(oldConfig.getStringList("Setting.ChoppingBoard.KitchenKnife.Material"), report,
                        "old/Config.yml:Setting.ChoppingBoard.KitchenKnife.Material"));
        putUpdate(updates, report, "stations.chopping_board.cut_damage.enabled",
                oldConfig.getBoolean("Setting.ChoppingBoard.Damage.Enable", true));
        putUpdate(updates, report, "stations.chopping_board.cut_damage.chance",
                oldConfig.getInt("Setting.ChoppingBoard.Damage.Chance", 10));
        putUpdate(updates, report, "stations.chopping_board.cut_damage.value",
                oldConfig.getInt("Setting.ChoppingBoard.Damage.Value", 2));

        putUpdate(updates, report, "stations.wok.block_source",
                convertLegacyBlockSource(
                        oldConfig.getBoolean("Setting.Wok.Custom", false),
                        oldConfig.getString("Setting.Wok.Material"),
                        "old/Config.yml:Setting.Wok.Material",
                        report));
        putUpdate(updates, report, "stations.wok.require_sneaking",
                oldConfig.getBoolean("Setting.Wok.StealthInteraction", true));
        putUpdate(updates, report, "stations.wok.drop_result",
                oldConfig.getBoolean("Setting.Wok.Drop", true));
        putUpdate(updates, report, "stations.wok.need_bowl",
                oldConfig.getBoolean("Setting.Wok.NeedBowl", true));
        putUpdate(updates, report, "stations.wok.stir_delay_ms",
                Math.max(0, secondsToMillis(oldConfig.getDouble("Setting.Wok.Delay", 5D))));
        putUpdate(updates, report, "stations.wok.timeout_ms",
                Math.max(0, secondsToMillis(oldConfig.getDouble("Setting.Wok.TimeOut", 30D))));
        putUpdate(updates, report, "stations.wok.invalid_result_source",
                convertLegacySource(oldConfig.getString("Setting.Wok.InvalidRecipeOutput"), report,
                        "old/Config.yml:Setting.Wok.InvalidRecipeOutput"));
        putUpdate(updates, report, "stations.wok.spatula_sources",
                convertLegacySourceList(oldConfig.getStringList("Setting.Wok.Spatula.Material"), report,
                        "old/Config.yml:Setting.Wok.Spatula.Material"));
        putUpdate(updates, report, "stations.wok.heat_levels",
                convertLegacyHeatLevels(oldConfig.getSection("Setting.Wok.HeatControl"), report,
                        "old/Config.yml:Setting.Wok.HeatControl"));
        putUpdate(updates, report, "stations.wok.scald_damage.enabled",
                oldConfig.getBoolean("Setting.Wok.Damage.Enable", true));
        putUpdate(updates, report, "stations.wok.scald_damage.value",
                oldConfig.getInt("Setting.Wok.Damage.Value", 2));
        putUpdate(updates, report, "stations.wok.failure.enabled",
                oldConfig.getBoolean("Setting.Wok.Failure.Enable", true));
        putUpdate(updates, report, "stations.wok.failure.chance",
                oldConfig.getInt("Setting.Wok.Failure.Chance", 5));
        putUpdate(updates, report, "stations.wok.failure.output_source",
                convertLegacySource(oldConfig.getString("Setting.Wok.Failure.Type"), report,
                        "old/Config.yml:Setting.Wok.Failure.Type"));

        putUpdate(updates, report, "stations.grinder.block_source",
                convertLegacyBlockSource(
                        oldConfig.getBoolean("Setting.Grinder.Custom", false),
                        oldConfig.getString("Setting.Grinder.Material"),
                        "old/Config.yml:Setting.Grinder.Material",
                        report));
        putUpdate(updates, report, "stations.grinder.require_sneaking",
                oldConfig.getBoolean("Setting.Grinder.StealthInteraction", true));
        putUpdate(updates, report, "stations.grinder.drop_result",
                oldConfig.getBoolean("Setting.Grinder.Drop", true));
        putUpdate(updates, report, "stations.grinder.check_delay_ticks",
                oldConfig.getInt("Setting.Grinder.CheckDelay", 20));

        putUpdate(updates, report, "stations.steamer.block_source",
                convertLegacySource(
                        oldConfig.getString("Setting.Steamer.Material"),
                        report,
                        "old/Config.yml:Setting.Steamer.Material"));
        putUpdate(updates, report, "stations.steamer.require_sneaking",
                oldConfig.getBoolean("Setting.Steamer.StealthInteraction", true));
        putUpdate(updates, report, "stations.steamer.drop_result",
                oldConfig.getBoolean("Setting.Steamer.Drop", true));
        putUpdate(updates, report, "stations.steamer.heat_sources",
                convertLegacySourceList(oldConfig.getStringList("Setting.Steamer.HeatControl"), report,
                        "old/Config.yml:Setting.Steamer.HeatControl"));
        putUpdate(updates, report, "stations.steamer.ignite_heat_source",
                oldConfig.getBoolean("Setting.Steamer.Ignite", true));
        putUpdate(updates, report, "stations.steamer.fuels",
                convertLegacyFuelEntries(oldConfig.getSection("Setting.Steamer.Fuel"), report,
                        "old/Config.yml:Setting.Steamer.Fuel"));
        putUpdate(updates, report, "stations.steamer.moisture_sources",
                convertLegacyMoistureEntries(oldConfig.getStringList("Setting.Steamer.Moisture"), report,
                        "old/Config.yml:Setting.Steamer.Moisture"));
        putUpdate(updates, report, "stations.steamer.reset_progress_when_steam_empty",
                oldConfig.getBoolean("Setting.Steamer.ResetToZero", true));
        putUpdate(updates, report, "stations.steamer.steam_production_efficiency",
                oldConfig.getInt("Setting.Steamer.SteamProductionEfficiency", 10));
        putUpdate(updates, report, "stations.steamer.steam_conversion_efficiency",
                oldConfig.getInt("Setting.Steamer.SteamConversionEfficiency", 1));
        putUpdate(updates, report, "stations.steamer.steam_consumption_efficiency",
                oldConfig.getInt("Setting.Steamer.SteamConsumptionEfficiency", 1));

        return updates;
    }

    private Map<String, Object> buildGuiUpdates(YamlSection oldConfig, ImportReport report) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (oldConfig == null || oldConfig.isEmpty()) {
            return updates;
        }
        putGuiUpdate(updates, report, "inventory_type",
                oldConfig.getString("Setting.Steamer.OpenInventory.Type", "HOPPER"));
        putGuiUpdate(updates, report, "title",
                oldConfig.getString("Setting.Steamer.OpenInventory.Title", "<dark_gray>蒸锅"));
        putGuiUpdate(updates, report, "inventory_slots",
                oldConfig.getInt("Setting.Steamer.OpenInventory.Slot", 9));
        return updates;
    }

    private DisplayAdjustmentConversion buildDisplayAdjustmentConversion(YamlSection oldConfig, ImportReport report) {
        Map<String, Object> configUpdates = new LinkedHashMap<>();
        Map<String, Map<String, Object>> adjustmentFiles = new LinkedHashMap<>();
        if (oldConfig == null || oldConfig.isEmpty()) {
            return new DisplayAdjustmentConversion(configUpdates, List.of());
        }

        convertLegacyDisplaySection(
                oldConfig.getSection("Setting.ChoppingBoard.DisplayEntity"),
                StationType.CHOPPING_BOARD,
                true,
                configUpdates,
                adjustmentFiles,
                report
        );
        convertLegacyDisplaySection(
                oldConfig.getSection("Setting.Wok.DisplayEntity"),
                StationType.WOK,
                false,
                configUpdates,
                adjustmentFiles,
                report
        );

        List<ConvertedFile> files = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : adjustmentFiles.entrySet()) {
            addItemAdjustmentFile(files, report, entry.getKey(), entry.getValue());
        }
        return new DisplayAdjustmentConversion(configUpdates, files);
    }

    private void convertLegacyDisplaySection(YamlSection displaySection,
            StationType stationType,
            boolean migrateAsGlobalDefaults,
            Map<String, Object> configUpdates,
            Map<String, Map<String, Object>> adjustmentFiles,
            ImportReport report) {
        if (displaySection == null || displaySection.isEmpty() || stationType == null) {
            return;
        }
        Map<String, Object> itemDefaults = convertLegacyDisplayAdjustment(displaySection.getSection("Item"));
        if (!itemDefaults.isEmpty()) {
            String basePath = migrateAsGlobalDefaults
                    ? "display_adjustments.defaults.item"
                    : "display_adjustments.station_defaults." + stationType.folderName() + ".item";
            putNestedUpdates(configUpdates, report, basePath, itemDefaults);
        }

        Map<String, Object> blockDefaults = convertLegacyDisplayAdjustment(displaySection.getSection("Block"));
        if (!blockDefaults.isEmpty()) {
            String basePath = migrateAsGlobalDefaults
                    ? "display_adjustments.defaults.block"
                    : "display_adjustments.station_defaults." + stationType.folderName() + ".block";
            putNestedUpdates(configUpdates, report, basePath, blockDefaults);
        }

        for (String key : displaySection.getKeys(false)) {
            if ("Item".equalsIgnoreCase(key) || "Block".equalsIgnoreCase(key)) {
                continue;
            }
            Map<String, Object> adjustment = convertLegacyDisplayAdjustment(displaySection.getSection(key));
            if (adjustment.isEmpty()) {
                continue;
            }
            String source = convertLegacySource(
                    key,
                    report,
                    "old/Config.yml:Setting." + stationType.legacySection() + ".DisplayEntity." + key
            );
            if (Texts.isBlank(source)) {
                continue;
            }
            mergeItemAdjustmentOverride(adjustmentFiles, source, stationType, adjustment);
        }
    }

    private Map<String, Object> convertLegacyDisplayAdjustment(YamlSection section) {
        Map<String, Object> adjustment = orderedMap();
        if (section == null || section.isEmpty()) {
            return adjustment;
        }
        Map<String, Object> offset = orderedMap();
        putIfPresent(offset, "x", section.getDouble("Offset.X", null));
        putIfPresent(offset, "y", section.getDouble("Offset.Y", null));
        putIfPresent(offset, "z", section.getDouble("Offset.Z", null));
        if (!offset.isEmpty()) {
            adjustment.put("offset", offset);
        }

        Map<String, Object> rotation = orderedMap();
        putIfPresent(rotation, "x", section.get("Rotation.X"));
        putIfPresent(rotation, "y", section.get("Rotation.Y"));
        putIfPresent(rotation, "z", section.get("Rotation.Z"));
        if (!rotation.isEmpty()) {
            adjustment.put("rotation", rotation);
        }

        Object scaleRaw = section.get("Scale");
        if (scaleRaw != null) {
            Double scale = scaleRaw instanceof Number number
                    ? number.doubleValue()
                    : parseDouble(Texts.toStringSafe(scaleRaw), null);
            if (scale != null) {
                adjustment.put("scale", Map.of(
                        "x", scale,
                        "y", scale,
                        "z", scale
                ));
            }
        }
        return adjustment;
    }

    private void mergeItemAdjustmentOverride(Map<String, Map<String, Object>> adjustmentFiles,
            String source,
            StationType stationType,
            Map<String, Object> adjustment) {
        if (adjustmentFiles == null || stationType == null || adjustment == null || adjustment.isEmpty() || Texts.isBlank(source)) {
            return;
        }
        Map<String, Object> file = adjustmentFiles.computeIfAbsent(source, ignored -> {
            Map<String, Object> created = orderedMap();
            created.put("source", source);
            created.put("stations", orderedMap());
            return created;
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> stations = (Map<String, Object>) file.computeIfAbsent("stations", ignored -> orderedMap());
        stations.put(stationType.folderName(), new LinkedHashMap<>(adjustment));
    }

    private void putNestedUpdates(Map<String, Object> updates, ImportReport report, String basePath, Map<String, Object> values) {
        if (updates == null || values == null || values.isEmpty() || Texts.isBlank(basePath)) {
            return;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String path = basePath + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                putNestedUpdates(updates, report, path, MapYamlSection.normalizeMap(map));
                continue;
            }
            putUpdate(updates, report, path, value);
        }
    }

    private void convertChoppingBoardRecipes(YamlSection legacyRecipes, ImportReport report, List<ConvertedFile> sink) {
        if (legacyRecipes == null || legacyRecipes.isEmpty()) {
            return;
        }
        for (String legacyKey : legacyRecipes.getKeys(false)) {
            String source = convertLegacySource(legacyKey, report, "old/Recipe/ChoppingBoard.yml:" + legacyKey);
            if (Texts.isBlank(source)) {
                report.skipped.add("跳过砧板配方 " + legacyKey + "，无法识别输入物来源。");
                continue;
            }
            String id = report.uniqueRecipeId(source);
            OutcomeAccumulator accumulator = new OutcomeAccumulator();
            for (String reward : legacyRecipes.getStringList(legacyKey + ".Output")) {
                collectReward(accumulator, reward, 1, 100, ExecutionContext.PLAYER,
                        "old/Recipe/ChoppingBoard.yml:" + legacyKey + ".Output", report);
            }
            Map<String, Object> recipe = orderedMap();
            recipe.put("schema_version", SCHEMA_VERSION);
            recipe.put("id", id);
            recipe.put("display_name", legacyKey);
            recipe.put("input", Map.of("source", source));
            recipe.put("cuts_required", legacyRecipes.getInt(legacyKey + ".Count", 0));
            recipe.put("tool_damage", normalizeToolDamage(legacyRecipes.getInt(legacyKey + ".Durability", 1)));
            putString(recipe, "permission", legacyRecipes.getString(legacyKey + ".Permission"));
            Map<String, Object> result = orderedMap();
            putList(result, "outputs", accumulator.outputs);
            putList(result, "actions", accumulator.actions);
            recipe.put("result", result);
            YamlSection damage = legacyRecipes.getSection(legacyKey + ".Damage");
            if (damage != null && !damage.isEmpty()) {
                Map<String, Object> damageOverride = orderedMap();
                damageOverride.put("chance", damage.getInt("Chance", 0));
                damageOverride.put("value", damage.getInt("Value", 0));
                recipe.put("damage_override", damageOverride);
            }
            recipe.put("structured_presentation", orderedMap());
            addRecipeFile(sink, report, StationType.CHOPPING_BOARD, id, recipe);
        }
    }

    private void convertWokRecipes(YamlSection legacyRecipes, YamlSection oldConfig, ImportReport report, List<ConvertedFile> sink) {
        if (legacyRecipes == null || legacyRecipes.isEmpty()) {
            return;
        }
        String invalidOutput = oldConfig == null ? null : oldConfig.getString("Setting.Wok.InvalidRecipeOutput");
        for (String legacyKey : legacyRecipes.getKeys(false)) {
            String id = report.uniqueRecipeId(legacyKey);
            List<Map<String, Object>> ingredients = new ArrayList<>();
            for (String rawItem : legacyRecipes.getStringList(legacyKey + ".Item")) {
                String[] parts = Texts.normalizeWhitespace(rawItem).split(" ");
                if (parts.length < 4) {
                    report.skipped.add("跳过炒锅配方 " + legacyKey + " 中无效食材条目: " + rawItem);
                    continue;
                }
                String source = convertLegacySource(parts[0] + " " + parts[1], report,
                        "old/Recipe/Wok.yml:" + legacyKey + ".Item");
                if (Texts.isBlank(source)) {
                    report.skipped.add("跳过炒锅配方 " + legacyKey + " 中无法识别的食材来源: " + rawItem);
                    continue;
                }
                Map<String, Object> ingredient = orderedMap();
                ingredient.put("source", source);
                ingredient.put("amount", parseInteger(parts[2], 1));
                ingredient.put("stir_rule", parts[3]);
                ingredients.add(ingredient);
            }
            CountRange stirTotal = parseCountRange(legacyRecipes.get(legacyKey + ".Count"));
            Map<String, Object> recipe = orderedMap();
            recipe.put("schema_version", SCHEMA_VERSION);
            recipe.put("id", id);
            recipe.put("display_name", legacyKey);
            recipe.put("ingredients", ingredients);
            recipe.put("heat_level", legacyRecipes.getInt(legacyKey + ".HeatControl", 0));
            recipe.put("stir_total", Map.of(
                    "min", stirTotal.min(),
                    "max", stirTotal.max()
            ));
            recipe.put("fault_tolerance", legacyRecipes.getInt(legacyKey + ".FaultTolerance", 0));
            putString(recipe, "permission", legacyRecipes.getString(legacyKey + ".Permission"));

            Map<String, Object> result = orderedMap();
            putResultObject(result, "success",
                    convertSingleOutcome(legacyKey, legacyRecipes.getInt(legacyKey + ".Amount", 1), ExecutionContext.PLAYER,
                            "old/Recipe/Wok.yml:" + legacyKey, report));
            putResultObject(result, "undercooked",
                    convertSingleOutcome(legacyRecipes.getString(legacyKey + ".RAW"), 1, ExecutionContext.PLAYER,
                            "old/Recipe/Wok.yml:" + legacyKey + ".RAW", report));
            putResultObject(result, "overcooked",
                    convertSingleOutcome(legacyRecipes.getString(legacyKey + ".BURNT"), 1, ExecutionContext.PLAYER,
                            "old/Recipe/Wok.yml:" + legacyKey + ".BURNT", report));
            putResultObject(result, "invalid",
                    convertSingleOutcome(invalidOutput, 1, ExecutionContext.PLAYER,
                            "old/Config.yml:Setting.Wok.InvalidRecipeOutput", report));
            recipe.put("result", result);
            recipe.put("structured_presentation", orderedMap());
            addRecipeFile(sink, report, StationType.WOK, id, recipe);
        }
    }

    private void convertGrinderRecipes(YamlSection legacyRecipes, ImportReport report, List<ConvertedFile> sink) {
        if (legacyRecipes == null || legacyRecipes.isEmpty()) {
            return;
        }
        for (String legacyKey : legacyRecipes.getKeys(false)) {
            String source = convertLegacySource(legacyKey, report, "old/Recipe/Grinder.yml:" + legacyKey);
            if (Texts.isBlank(source)) {
                report.skipped.add("跳过研磨机配方 " + legacyKey + "，无法识别输入物来源。");
                continue;
            }
            String id = report.uniqueRecipeId(source);
            OutcomeAccumulator accumulator = new OutcomeAccumulator();
            for (String reward : legacyRecipes.getStringList(legacyKey + ".Output")) {
                collectReward(accumulator, reward, 1, 100, ExecutionContext.AMBIGUOUS_AUTO,
                        "old/Recipe/Grinder.yml:" + legacyKey + ".Output", report);
            }
            Map<String, Object> recipe = orderedMap();
            recipe.put("schema_version", SCHEMA_VERSION);
            recipe.put("id", id);
            recipe.put("display_name", legacyKey);
            recipe.put("input", Map.of("source", source));
            recipe.put("grind_time_seconds", legacyRecipes.getInt(legacyKey + ".GrindingTime", 5));
            putString(recipe, "permission", legacyRecipes.getString(legacyKey + ".Permission"));
            Map<String, Object> result = orderedMap();
            putList(result, "outputs", accumulator.outputs);
            putList(result, "actions", accumulator.actions);
            recipe.put("result", result);
            recipe.put("structured_presentation", orderedMap());
            report.registerGrinderRecipeId(source, id);
            addRecipeFile(sink, report, StationType.GRINDER, id, recipe);
        }
    }

    private void convertSteamerRecipes(YamlSection legacyRecipes, ImportReport report, List<ConvertedFile> sink) {
        if (legacyRecipes == null || legacyRecipes.isEmpty()) {
            return;
        }
        for (String legacyKey : legacyRecipes.getKeys(false)) {
            String source = convertLegacySource(legacyKey, report, "old/Recipe/Steamer.yml:" + legacyKey);
            if (Texts.isBlank(source)) {
                report.skipped.add("跳过蒸锅配方 " + legacyKey + "，无法识别输入物来源。");
                continue;
            }
            String id = report.uniqueRecipeId(source);
            Map<String, Object> recipe = orderedMap();
            recipe.put("schema_version", SCHEMA_VERSION);
            recipe.put("id", id);
            recipe.put("display_name", legacyKey);
            recipe.put("input", Map.of("source", source));
            recipe.put("required_steam", legacyRecipes.getInt(legacyKey + ".Steam", 0));
            putString(recipe, "permission", legacyRecipes.getString(legacyKey + ".Permission"));

            Map<String, Object> result = orderedMap();
            putResultObject(result, "output",
                    convertSingleOutcome(legacyRecipes.getString(legacyKey + ".Output"), 1, ExecutionContext.AMBIGUOUS_AUTO,
                            "old/Recipe/Steamer.yml:" + legacyKey + ".Output", report));
            recipe.put("result", result);
            recipe.put("structured_presentation", orderedMap());
            addRecipeFile(sink, report, StationType.STEAMER, id, recipe);
        }
    }

    private void convertStationStates(YamlSection legacyData, ImportReport report, List<ConvertedFile> sink) {
        if (legacyData == null || legacyData.isEmpty()) {
            return;
        }
        convertChoppingBoardStates(legacyData.getSection("ChoppingBoard"), report, sink);
        convertWokStates(legacyData.getSection("Wok"), report, sink);
        convertGrinderStates(legacyData.getSection("Grinder"), report, sink);
        convertSteamerStates(legacyData.getSection("Steamer"), report, sink);
    }

    private void convertChoppingBoardStates(YamlSection section, ImportReport report, List<ConvertedFile> sink) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String fileKey : section.getKeys(false)) {
            StationLocation location = parseStationLocation(fileKey, "old/Data.yml:ChoppingBoard." + fileKey, report);
            if (location == null) {
                continue;
            }
            Map<String, Object> state = createBaseStationState(location, StationType.CHOPPING_BOARD);
            String source = convertLegacySource(section.getString(fileKey + ".Input"), report,
                    "old/Data.yml:ChoppingBoard." + fileKey + ".Input");
            if (Texts.isNotBlank(source)) {
                state.put("input_item", Map.of("source", source));
            }
            Map<String, Object> chopping = orderedMap();
            chopping.put("cut_count", section.getInt(fileKey + ".CutCount", 0));
            state.put("chopping_board", chopping);
            Map<String, Object> timestamps = orderedMap();
            timestamps.put("last_interaction_ms", section.get(fileKey + ".LastInteractTime"));
            state.put("timestamps", timestamps);
            addStationFile(sink, report, StationType.CHOPPING_BOARD, location, state);
        }
    }

    private void convertWokStates(YamlSection section, ImportReport report, List<ConvertedFile> sink) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String fileKey : section.getKeys(false)) {
            StationLocation location = parseStationLocation(fileKey, "old/Data.yml:Wok." + fileKey, report);
            if (location == null) {
                continue;
            }
            Map<String, Object> state = createBaseStationState(location, StationType.WOK);
            List<Map<String, Object>> ingredients = new ArrayList<>();
            for (String rawItem : section.getStringList(fileKey + ".Items")) {
                String[] parts = Texts.normalizeWhitespace(rawItem).split(" ");
                if (parts.length < 4) {
                    continue;
                }
                String source = convertLegacySource(parts[0] + " " + parts[1], report,
                        "old/Data.yml:Wok." + fileKey + ".Items");
                if (Texts.isBlank(source)) {
                    continue;
                }
                Map<String, Object> ingredient = orderedMap();
                ingredient.put("source", source);
                ingredient.put("amount", parseInteger(parts[2], 1));
                ingredient.put("stir_times", parseInteger(parts[3], 0));
                ingredients.add(ingredient);
            }
            Map<String, Object> wok = orderedMap();
            wok.put("total_stir_count", section.getInt(fileKey + ".Count", 0));
            wok.put("ingredients", ingredients);
            state.put("wok", wok);
            Map<String, Object> timestamps = orderedMap();
            timestamps.put("last_stir_time_ms", section.get(fileKey + ".LastStirTime"));
            timestamps.put("stir_fried_time_ms", section.get(fileKey + ".StirFriedTime"));
            state.put("timestamps", timestamps);
            addStationFile(sink, report, StationType.WOK, location, state);
        }
    }

    private void convertGrinderStates(YamlSection section, ImportReport report, List<ConvertedFile> sink) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String fileKey : section.getKeys(false)) {
            StationLocation location = parseStationLocation(fileKey, "old/Data.yml:Grinder." + fileKey, report);
            if (location == null) {
                continue;
            }
            Map<String, Object> state = createBaseStationState(location, StationType.GRINDER);
            String source = convertLegacySource(section.getString(fileKey + ".Input"), report,
                    "old/Data.yml:Grinder." + fileKey + ".Input");
            if (Texts.isNotBlank(source)) {
                state.put("input_item", Map.of("source", source));
            }
            String recipeId = report.grinderRecipeId(source);
            if (Texts.isBlank(recipeId)) {
                report.skipped.add("跳过研磨机工位状态 old/Data.yml:Grinder." + fileKey
                        + "，未找到输入物 " + source + " 对应的导入配方 ID。");
                continue;
            }
            Map<String, Object> grinder = orderedMap();
            grinder.put("recipe_id", recipeId);
            grinder.put("start_time_ms", section.get(fileKey + ".StartTime"));
            putString(grinder, "player_name", section.getString(fileKey + ".Player"));
            state.put("grinder", grinder);
            addStationFile(sink, report, StationType.GRINDER, location, state);
        }
    }

    private void convertSteamerStates(YamlSection section, ImportReport report, List<ConvertedFile> sink) {
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String fileKey : section.getKeys(false)) {
            StationLocation location = parseStationLocation(fileKey, "old/Data.yml:Steamer." + fileKey, report);
            if (location == null) {
                continue;
            }
            Map<String, Object> state = createBaseStationState(location, StationType.STEAMER);
            Map<String, Object> steamer = orderedMap();
            steamer.put("burning_until_ms", section.get(fileKey + ".CoolingTime"));
            steamer.put("moisture", section.getInt(fileKey + ".Moisture", 0));
            steamer.put("steam", section.getInt(fileKey + ".Steam", 0));
            state.put("steamer", steamer);

            List<Map<String, Object>> slots = new ArrayList<>();
            List<String> rawSlots = section.getStringList(fileKey + ".Slots");
            for (int index = 0; index < rawSlots.size(); index++) {
                String rawSlot = rawSlots.get(index);
                if (Texts.isBlank(rawSlot) || "AIR".equalsIgnoreCase(rawSlot)) {
                    continue;
                }
                String source = convertLegacySource(rawSlot, report,
                        "old/Data.yml:Steamer." + fileKey + ".Slots[" + index + "]");
                if (Texts.isBlank(source)) {
                    continue;
                }
                Map<String, Object> slot = orderedMap();
                slot.put("index", index);
                slot.put("source", source);
                slots.add(slot);
            }
            if (!slots.isEmpty()) {
                state.put("gui_slots", slots);
            }

            List<Map<String, Object>> slotProgress = new ArrayList<>();
            List<?> rawProgress = section.getList(fileKey + ".CookingProgress", List.of());
            for (int index = 0; index < rawProgress.size(); index++) {
                Integer progress = parseInteger(rawProgress.get(index), 0);
                Map<String, Object> entry = orderedMap();
                entry.put("index", index);
                entry.put("progress", progress);
                slotProgress.add(entry);
            }
            if (!slotProgress.isEmpty()) {
                state.put("slot_progress", slotProgress);
            }
            addStationFile(sink, report, StationType.STEAMER, location, state);
        }
    }

    private Path backupCurrentRuntime(ImportReport report) {
        Path backupRoot = plugin.dataPath("backup", "pre-import-" + BACKUP_TIME_FORMAT.format(LocalDateTime.now()));
        try {
            YamlFiles.ensureDirectory(backupRoot);
            copyIfExists(plugin.dataPath("config.yml"), backupRoot.resolve("config.yml"));
            copyIfExists(plugin.dataPath("gui"), backupRoot.resolve("gui"));
            copyIfExists(plugin.dataPath("recipes"), backupRoot.resolve("recipes"));
            copyIfExists(plugin.dataPath("item_adjustments"), backupRoot.resolve("item_adjustments"));
            copyIfExists(plugin.dataPath("data", "stations"), backupRoot.resolve("data").resolve("stations"));
            copyIfExists(plugin.dataPath("data", "legacy-import-report.yml"), backupRoot.resolve("data").resolve("legacy-import-report.yml"));
            report.writtenFiles.add(backupRoot.toString());
            return backupRoot;
        } catch (Exception exception) {
            report.conflicts.add("写入旧版导入备份失败: " + exception.getMessage());
            return null;
        }
    }

    private void applyYamlUpdates(String relativePath, Map<String, Object> updates, ImportReport report) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        Path targetPath = plugin.getDataFolder().toPath().resolve(Path.of(relativePath));
        YamlSection configuration = loadYaml(targetPath);
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            configuration.set(entry.getKey(), entry.getValue());
        }
        try {
            YamlFiles.save(targetPath.toFile(), configuration);
            report.writtenFiles.add(targetPath.toString());
        } catch (IOException exception) {
            report.conflicts.add("保存文件失败 " + relativePath + ": " + exception.getMessage());
        }
    }

    private void writeConvertedFiles(List<ConvertedFile> files, ImportReport report) {
        for (ConvertedFile file : files) {
            try {
                Path target = plugin.getDataFolder().toPath().resolve(file.relativePath());
                YamlFiles.save(target.toFile(), file.content());
                report.writtenFiles.add(target.toString());
            } catch (Exception exception) {
                report.conflicts.add("写入文件失败 " + file.relativePath() + ": " + exception.getMessage());
            }
        }
    }

    private Path writeReport(ConversionBundle bundle, ImportReport report, Path backupPath) {
        Path reportPath = plugin.dataPath("data", "legacy-import-report.yml");
        try {
            YamlFiles.save(reportPath.toFile(), report.toYaml(bundle, backupPath));
            if (report.applied) {
                report.writtenFiles.add(reportPath.toString());
            }
            return reportPath;
        } catch (Exception exception) {
            messageService.warning("loader.load_failed", Map.of(
                    "type", "导入报告",
                    "file", reportPath.getFileName().toString(),
                    "error", String.valueOf(exception.getMessage())
            ));
            return null;
        }
    }

    private void addRecipeFile(List<ConvertedFile> sink,
            ImportReport report,
            StationType stationType,
            String id,
            Map<String, Object> content) {
        String relativePath = "recipes/" + stationType.folderName() + "/" + safeFileName(id) + ".yml";
        String uniquePath = report.uniqueRelativePath(relativePath);
        sink.add(new ConvertedFile(uniquePath, content));
        report.incrementRecipe(stationType);
    }

    private void addStationFile(List<ConvertedFile> sink,
            ImportReport report,
            StationType stationType,
            StationLocation location,
            Map<String, Object> content) {
        String relativePath = "data/stations/" + sanitizeWorldSegment(location.world()) + "/"
                + location.x() + "_" + location.y() + "_" + location.z() + ".yml";
        String uniquePath = report.uniqueRelativePath(relativePath);
        sink.add(new ConvertedFile(uniquePath, content));
        report.incrementStation(stationType);
    }

    private void addItemAdjustmentFile(List<ConvertedFile> sink,
            ImportReport report,
            String source,
            Map<String, Object> content) {
        String relativePath = "item_adjustments/" + safeFileName(source) + ".yml";
        String uniquePath = report.uniqueRelativePath(relativePath);
        sink.add(new ConvertedFile(uniquePath, content));
    }

    private Map<String, Object> createBaseStationState(StationLocation location, StationType stationType) {
        Map<String, Object> base = orderedMap();
        base.put("schema_version", SCHEMA_VERSION);
        base.put("station_type", stationType.folderName());
        base.put("world", location.world());
        base.put("x", location.x());
        base.put("y", location.y());
        base.put("z", location.z());
        base.put("display_entity", orderedMap());
        return base;
    }

    private Map<String, Object> convertSingleOutcome(String raw,
            int defaultAmount,
            ExecutionContext context,
            String location,
            ImportReport report) {
        OutcomeAccumulator accumulator = new OutcomeAccumulator();
        collectReward(accumulator, raw, defaultAmount, 100, context, location, report);
        if (!accumulator.outputs.isEmpty()) {
            Map<String, Object> single = orderedMap();
            single.putAll(accumulator.outputs.getFirst());
            if (!accumulator.actions.isEmpty()) {
                single.put("actions", List.copyOf(accumulator.actions));
            }
            return single;
        }
        if (!accumulator.actions.isEmpty()) {
            return Map.of("actions", List.copyOf(accumulator.actions));
        }
        return Map.of();
    }

    private void collectReward(OutcomeAccumulator accumulator,
            String raw,
            int defaultAmount,
            int defaultChance,
            ExecutionContext context,
            String location,
            ImportReport report) {
        if (accumulator == null || Texts.isBlank(raw)) {
            return;
        }
        String trimmed = Texts.normalizeWhitespace(raw);
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("command ")) {
            String converted = convertLegacyCommand(trimmed, context, location, report);
            if (Texts.isNotBlank(converted)) {
                accumulator.actions.add(converted);
            }
            return;
        }
        String[] parts = trimmed.split(" ");
        if (parts.length < 2) {
            report.skipped.add("无法解析旧版奖励条目: " + location + " -> " + raw);
            return;
        }
        String source = convertLegacySource(parts[0] + " " + parts[1], report, location);
        if (Texts.isBlank(source)) {
            report.skipped.add("奖励来源无法识别: " + location + " -> " + raw);
            return;
        }
        Map<String, Object> output = orderedMap();
        output.put("source", source);
        Object amountObject = parts.length >= 3 ? parts[2] : defaultAmount;
        applyAmount(output, amountObject);
        Integer chance = parts.length >= 4 ? parseInteger(parts[3], defaultChance) : defaultChance;
        if (chance != null && chance > 0 && chance < 100) {
            output.put("chance", chance);
        }
        accumulator.outputs.add(output);
    }

    private String convertLegacyCommand(String raw, ExecutionContext context, String location, ImportReport report) {
        String commandContent = raw.length() <= 8 ? "" : raw.substring(8).trim();
        if (commandContent.isEmpty()) {
            report.skipped.add("空命令条目已跳过: " + location);
            return "";
        }
        String[] tokens = commandContent.split(" ");
        List<String> commandParts = new ArrayList<>();
        Integer chance = null;
        boolean removedExecuteCount = false;
        for (String token : tokens) {
            if (token.startsWith("a:")) {
                removedExecuteCount = true;
                continue;
            }
            if (token.startsWith("c:")) {
                chance = parseInteger(token.substring(2), null);
                continue;
            }
            commandParts.add(token);
        }
        if (removedExecuteCount) {
            report.removedExecuteCount++;
        }
        String actionId = switch (context) {
            case PLAYER -> "runcommandasplayer";
            case CONSOLE -> "runcommandasconsole";
            case AMBIGUOUS_AUTO -> {
                report.actionContextIssues.add(location + " -> " + raw);
                yield "runcommandasconsole";
            }
        };
        StringBuilder builder = new StringBuilder();
        if (chance != null && chance > 0 && chance < 100) {
            builder.append("@chance=").append(chance).append(' ');
        }
        builder.append(actionId)
                .append(" command=\"")
                .append(escapeActionArgument(String.join(" ", commandParts)))
                .append('"');
        return builder.toString();
    }

    private List<String> convertLegacySourceList(List<String> values, ImportReport report, String locationPrefix) {
        List<String> converted = new ArrayList<>();
        if (values == null) {
            return converted;
        }
        int index = 0;
        for (String raw : values) {
            String convertedValue = convertLegacySource(raw, report, locationPrefix + "[" + index + "]");
            if (Texts.isNotBlank(convertedValue)) {
                converted.add(convertedValue);
            }
            index++;
        }
        return converted;
    }

    private List<Map<String, Object>> convertLegacyHeatLevels(YamlSection section, ImportReport report, String locationPrefix) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (section == null || section.isEmpty()) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            String source = convertLegacySource(key, report, locationPrefix + "." + key);
            if (Texts.isBlank(source)) {
                continue;
            }
            Map<String, Object> entry = orderedMap();
            entry.put("source", source);
            entry.put("level", section.getInt(key, 0));
            values.add(entry);
        }
        return values;
    }

    private List<Map<String, Object>> convertLegacyFuelEntries(YamlSection section, ImportReport report, String locationPrefix) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (section == null || section.isEmpty()) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            String source = convertLegacySource(key, report, locationPrefix + "." + key);
            if (Texts.isBlank(source)) {
                continue;
            }
            Map<String, Object> entry = orderedMap();
            entry.put("source", source);
            entry.put("duration_seconds", section.getInt(key, 0));
            values.add(entry);
        }
        return values;
    }

    private List<Map<String, Object>> convertLegacyMoistureEntries(List<String> values, ImportReport report, String locationPrefix) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (int index = 0; index < values.size(); index++) {
            String raw = values.get(index);
            String[] parts = raw.split(" & ");
            if (parts.length < 3) {
                report.skipped.add("跳过无效蒸锅水分规则: " + raw);
                continue;
            }
            String inputSource = convertLegacySource(parts[0], report, locationPrefix + "[" + index + "].input");
            String outputSource = convertLegacySource(parts[1], report, locationPrefix + "[" + index + "].output");
            if (Texts.isBlank(inputSource)) {
                continue;
            }
            Map<String, Object> entry = orderedMap();
            entry.put("input_source", inputSource);
            if (Texts.isNotBlank(outputSource)) {
                entry.put("output_source", outputSource);
            }
            entry.put("moisture", parseInteger(parts[2], 0));
            result.add(entry);
        }
        return result;
    }

    private String convertLegacyBlockSource(boolean custom, String raw, String location, ImportReport report) {
        if (Texts.isBlank(raw)) {
            return "";
        }
        String text = Texts.normalizeWhitespace(raw);
        if (custom && !text.toLowerCase(Locale.ROOT).startsWith("craftengine")) {
            text = "craftengine " + text;
        }
        if (!custom && !text.contains(" ") && !text.contains(":")) {
            text = "minecraft " + text;
        }
        return convertLegacySource(text, report, location);
    }

    private String convertLegacySource(String raw, ImportReport report, String location) {
        if (Texts.isBlank(raw)) {
            return "";
        }
        String text = Texts.normalizeWhitespace(raw);
        String normalized;
        String[] parts = text.split(" ", 2);
        if (parts.length >= 2) {
            String namespace = parts[0].toLowerCase(Locale.ROOT);
            String identifier = parts[1].trim();
            normalized = switch (namespace) {
                case "minecraft" -> normalizeVanillaIdentifier(identifier);
                case "craftengine" -> "craftengine-" + normalizeIdentifier(identifier);
                case "mmoitems" -> "mmoitems-" + normalizeIdentifier(identifier);
                case "neigeitems" -> "neigeitems-" + normalizeIdentifier(identifier);
                default -> text.toLowerCase(Locale.ROOT);
            };
        } else if (text.contains(":")) {
            normalized = normalizeVanillaIdentifier(text);
        } else {
            normalized = normalizeVanillaIdentifier(text);
        }
        ItemSource source = ItemSourceUtil.parseShorthand(normalized);
        if (source == null) {
            report.unknownSources.add(location + " -> " + raw);
            return "";
        }
        if (!itemSourceService.isAvailable(source)) {
            report.unknownSources.add(location + " -> " + raw);
        }
        return ItemSourceUtil.toShorthand(source);
    }

    private String normalizeVanillaIdentifier(String identifier) {
        String value = normalizeIdentifier(identifier);
        if (value.startsWith("minecraft-") || value.startsWith("mc-") || value.startsWith("v-")) {
            return value;
        }
        if (value.startsWith("minecraft:")) {
            value = value.substring("minecraft:".length());
        }
        return "minecraft-" + value;
    }

    private String normalizeIdentifier(String identifier) {
        return Texts.toStringSafe(identifier).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void applyAmount(Map<String, Object> output, Object amountObject) {
        String amountText = Texts.toStringSafe(amountObject).trim();
        if (amountText.contains("-")) {
            String[] range = amountText.split("-");
            Integer min = range.length >= 1 ? parseInteger(range[0], 1) : 1;
            Integer max = range.length >= 2 ? parseInteger(range[1], min) : min;
            output.put("amount_range", Map.of(
                    "min", min == null ? 1 : min,
                    "max", max == null ? (min == null ? 1 : min) : max
            ));
            return;
        }
        output.put("amount", parseInteger(amountText, 1));
    }

    private void putUpdate(Map<String, Object> updates, ImportReport report, String path, Object value) {
        if (updates == null || Texts.isBlank(path)) {
            return;
        }
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        updates.put(path, value);
        report.configUpdatedPaths.add(path);
    }

    private void putGuiUpdate(Map<String, Object> updates, ImportReport report, String path, Object value) {
        if (updates == null || Texts.isBlank(path)) {
            return;
        }
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        updates.put(path, value);
        report.guiUpdatedPaths.add(path);
    }

    private void putString(Map<String, Object> target, String key, String value) {
        if (target == null || Texts.isBlank(key) || Texts.isBlank(value)) {
            return;
        }
        target.put(key, value);
    }

    private void putList(Map<String, Object> target, String key, List<?> values) {
        if (target == null || Texts.isBlank(key) || values == null || values.isEmpty()) {
            return;
        }
        target.put(key, List.copyOf(values));
    }

    private void putResultObject(Map<String, Object> target, String key, Map<String, Object> value) {
        if (target == null || Texts.isBlank(key) || value == null || value.isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    private StationLocation parseStationLocation(String fileKey, String location, ImportReport report) {
        if (Texts.isBlank(fileKey)) {
            report.skipped.add("跳过空工位坐标: " + location);
            return null;
        }
        String[] parts = fileKey.split(",");
        if (parts.length < 4) {
            report.skipped.add("跳过无效工位坐标: " + location + " -> " + fileKey);
            return null;
        }
        Integer x = parseInteger(parts[0], null);
        Integer y = parseInteger(parts[1], null);
        Integer z = parseInteger(parts[2], null);
        String world = parts[3].trim();
        if (x == null || y == null || z == null || world.isBlank()) {
            report.skipped.add("跳过无效工位坐标: " + location + " -> " + fileKey);
            return null;
        }
        return new StationLocation(world, x, y, z);
    }

    private CountRange parseCountRange(Object raw) {
        String text = Texts.toStringSafe(raw).trim();
        if (text.contains("-")) {
            String[] parts = text.split("-");
            Integer min = parts.length >= 1 ? parseInteger(parts[0], 0) : 0;
            Integer max = parts.length >= 2 ? parseInteger(parts[1], min) : min;
            if (min != null && max != null && min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
            return new CountRange(min == null ? 0 : min, max == null ? 0 : max);
        }
        Integer value = parseInteger(text, 0);
        return new CountRange(value == null ? 0 : value, value == null ? 0 : value);
    }

    private int normalizeToolDamage(Integer value) {
        if (value == null || value == 0) {
            return 1;
        }
        return Math.max(1, value);
    }

    private long secondsToMillis(Double seconds) {
        return Math.round((seconds == null ? 0D : seconds) * 1000D);
    }

    private Integer parseInteger(Object raw, Integer fallback) {
        try {
            return Integer.parseInt(Texts.toStringSafe(raw).trim());
        } catch (Exception _) {
            return fallback;
        }
    }

    private Double parseDouble(Object raw, Double fallback) {
        try {
            return Double.parseDouble(Texts.toStringSafe(raw).trim());
        } catch (Exception _) {
            return fallback;
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target == null || Texts.isBlank(key) || value == null) {
            return;
        }
        target.put(key, value);
    }

    private String escapeActionArgument(String raw) {
        return Texts.toStringSafe(raw)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String safeFileName(String raw) {
        String normalized = FILE_NAME_PATTERN.matcher(Texts.normalizeId(raw)).replaceAll("_");
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "imported_recipe" : normalized;
    }

    private String sanitizeWorldSegment(String raw) {
        String normalized = WORLD_SEGMENT_PATTERN.matcher(Texts.toStringSafe(raw).trim()).replaceAll("_");
        return normalized.isBlank() ? "world" : normalized;
    }

    private void copyIfExists(Path source, Path target) throws IOException {
        if (source == null || target == null || !Files.exists(source)) {
            return;
        }
        if (Files.isDirectory(source)) {
            try (var stream = Files.walk(source)) {
                for (Path path : stream.toList()) {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        YamlFiles.ensureDirectory(destination);
                    } else {
                        YamlFiles.ensureDirectory(destination.getParent());
                        Files.copy(path, destination);
                    }
                }
            }
            return;
        }
        YamlFiles.ensureDirectory(target.getParent());
        Files.copy(source, target);
    }

    private YamlSection loadYaml(Path path) {
        return path == null ? YamlFiles.load("") : YamlFiles.load(path.toFile());
    }

    private static Map<String, Object> orderedMap() {
        return new LinkedHashMap<>();
    }

    public record LegacyImportResult(boolean applied,
            String reportPath,
            String backupPath,
            int convertedRecipeCount,
            int convertedStationCount,
            int skippedCount,
            int conflictCount,
            int unknownSourceCount,
            int actionContextIssueCount) {
    }

    private record LegacyInputs(Path root,
            YamlSection config,
            YamlSection choppingBoardRecipes,
            YamlSection wokRecipes,
            YamlSection grinderRecipes,
            YamlSection steamerRecipes,
            YamlSection data) {
    }

    private record ConversionBundle(Map<String, Object> configUpdates,
            Map<String, Object> guiUpdates,
            List<ConvertedFile> recipeFiles,
            List<ConvertedFile> stationFiles,
            List<ConvertedFile> adjustmentFiles) {
    }

    private record DisplayAdjustmentConversion(Map<String, Object> configUpdates,
            List<ConvertedFile> files) {
    }

    private record ConvertedFile(String relativePath, Map<String, Object> content) {
    }

    private record StationLocation(String world, int x, int y, int z) {
    }

    private record CountRange(int min, int max) {
    }

    private enum ExecutionContext {
        PLAYER,
        CONSOLE,
        AMBIGUOUS_AUTO
    }

    private static final class OutcomeAccumulator {
        private final List<Map<String, Object>> outputs = new ArrayList<>();
        private final List<String> actions = new ArrayList<>();
    }

    private static final class ImportReport {
        private final boolean applied;
        private final Set<String> usedRelativePaths = new LinkedHashSet<>();
        private final Set<String> usedRecipeIds = new LinkedHashSet<>();
        private final Map<String, String> grinderRecipeIdsBySource = new LinkedHashMap<>();
        private final Map<String, Integer> recipeCounts = new LinkedHashMap<>();
        private final Map<String, Integer> stationCounts = new LinkedHashMap<>();
        private final List<String> skipped = new ArrayList<>();
        private final List<String> conflicts = new ArrayList<>();
        private final List<String> unknownSources = new ArrayList<>();
        private final List<String> actionContextIssues = new ArrayList<>();
        private final List<String> configUpdatedPaths = new ArrayList<>();
        private final List<String> guiUpdatedPaths = new ArrayList<>();
        private final List<String> writtenFiles = new ArrayList<>();
        private int removedExecuteCount;
        private String oldDirectory = "";

        private ImportReport(boolean applied) {
            this.applied = applied;
        }

        private void incrementRecipe(StationType stationType) {
            increment(recipeCounts, stationType.folderName());
        }

        private void incrementStation(StationType stationType) {
            increment(stationCounts, stationType.folderName());
        }

        private void increment(Map<String, Integer> target, String key) {
            target.put(key, target.getOrDefault(key, 0) + 1);
        }

        private void registerGrinderRecipeId(String inputSource, String recipeId) {
            if (Texts.isBlank(inputSource) || Texts.isBlank(recipeId)) {
                return;
            }
            grinderRecipeIdsBySource.put(normalizeRecipeSourceKey(inputSource), recipeId);
        }

        private String grinderRecipeId(String inputSource) {
            if (Texts.isBlank(inputSource)) {
                return "";
            }
            return Texts.toStringSafe(grinderRecipeIdsBySource.get(normalizeRecipeSourceKey(inputSource)));
        }

        private String uniqueRecipeId(String raw) {
            String base = FILE_NAME_PATTERN.matcher(Texts.normalizeId(raw)).replaceAll("_").replaceAll("^_+|_+$", "");
            if (base.isBlank()) {
                base = "imported_recipe";
            }
            String candidate = base;
            int counter = 2;
            while (!usedRecipeIds.add(candidate)) {
                conflicts.add("检测到重复配方 ID，已自动重命名: " + candidate);
                candidate = base + "_" + counter++;
            }
            return candidate;
        }

        private String uniqueRelativePath(String relativePath) {
            if (usedRelativePaths.add(relativePath)) {
                return relativePath;
            }
            int dot = relativePath.lastIndexOf('.');
            String stem = dot < 0 ? relativePath : relativePath.substring(0, dot);
            String ext = dot < 0 ? "" : relativePath.substring(dot);
            int counter = 2;
            String candidate = stem + "_" + counter + ext;
            while (!usedRelativePaths.add(candidate)) {
                counter++;
                candidate = stem + "_" + counter + ext;
            }
            conflicts.add("检测到重复输出文件，已自动重命名: " + relativePath + " -> " + candidate);
            return candidate;
        }

        private int convertedRecipeCount() {
            return recipeCounts.values().stream().mapToInt(Integer::intValue).sum();
        }

        private int convertedStationCount() {
            return stationCounts.values().stream().mapToInt(Integer::intValue).sum();
        }

        private int skippedCount() {
            return skipped.size();
        }

        private int conflictCount() {
            return conflicts.size();
        }

        private int unknownSourceCount() {
            return unknownSources.size();
        }

        private int actionContextIssueCount() {
            return actionContextIssues.size();
        }

        private Map<String, Object> toYaml(ConversionBundle bundle, Path backupPath) {
            Map<String, Object> root = orderedMap();
            root.put("schema_version", SCHEMA_VERSION);
            root.put("generated_at", LocalDateTime.now().toString());
            root.put("mode", applied ? "apply" : "dryrun");
            root.put("old_directory", oldDirectory);
            if (backupPath != null) {
                root.put("backup_path", backupPath.toString());
            }

            Map<String, Object> summary = orderedMap();
            summary.put("converted_recipe_count", convertedRecipeCount());
            summary.put("converted_station_state_count", convertedStationCount());
            summary.put("item_adjustment_file_count", bundle.adjustmentFiles().size());
            summary.put("config_update_count", configUpdatedPaths.size());
            summary.put("gui_update_count", guiUpdatedPaths.size());
            summary.put("skipped_count", skippedCount());
            summary.put("conflict_count", conflictCount());
            summary.put("unknown_source_count", unknownSourceCount());
            summary.put("action_context_issue_count", actionContextIssueCount());
            summary.put("removed_execute_count_field_count", removedExecuteCount);
            root.put("summary", summary);

            root.put("recipe_counts", recipeCounts.isEmpty() ? Map.of() : Map.copyOf(recipeCounts));
            root.put("station_state_counts", stationCounts.isEmpty() ? Map.of() : Map.copyOf(stationCounts));
            root.put("config_updated_paths", configUpdatedPaths.isEmpty() ? List.of() : List.copyOf(configUpdatedPaths));
            root.put("gui_updated_paths", guiUpdatedPaths.isEmpty() ? List.of() : List.copyOf(guiUpdatedPaths));
            root.put("skipped", skipped.isEmpty() ? List.of() : List.copyOf(skipped));
            root.put("conflicts", conflicts.isEmpty() ? List.of() : List.copyOf(conflicts));
            root.put("unknown_sources", unknownSources.isEmpty() ? List.of() : List.copyOf(unknownSources));
            root.put("action_context_issues", actionContextIssues.isEmpty() ? List.of() : List.copyOf(actionContextIssues));
            root.put("written_files", writtenFiles.isEmpty() ? List.of() : List.copyOf(writtenFiles));
            root.put("planned_recipe_files", bundle.recipeFiles().stream().map(ConvertedFile::relativePath).toList());
            root.put("planned_station_files", bundle.stationFiles().stream().map(ConvertedFile::relativePath).toList());
            root.put("planned_item_adjustment_files", bundle.adjustmentFiles().stream().map(ConvertedFile::relativePath).toList());
            return root;
        }

        private String normalizeRecipeSourceKey(String inputSource) {
            return Texts.toStringSafe(inputSource).trim().toLowerCase(Locale.ROOT);
        }
    }
}

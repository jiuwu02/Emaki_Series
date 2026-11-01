package emaki.jiuwu.craft.cooking.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiCookingPlugin plugin;

    public CookingConfigPrecheckContributor(EmakiCookingPlugin plugin) {
        super("cooking", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        for (StationType type : StationType.values()) {
            checkDirectory(new File(plugin.getDataFolder(), "recipes/" + type.folderName()),
                    "recipes/" + type.folderName(),
                    message("recipe_directory_missing"),
                    message("recipe_directory_not_readable"),
                    issues);
        }
        checkDirectory(new File(plugin.getDataFolder(), "data/stations"),
                "data/stations",
                message("station_state_directory_missing"),
                message("station_state_directory_not_readable"),
                issues);
        checkDirectory(new File(plugin.getDataFolder(), "data/stations/index"),
                "data/stations/index",
                message("station_state_directory_missing"),
                message("station_state_directory_not_readable"),
                issues);
        checkDirectory(new File(plugin.getDataFolder(), "data/stations-legacy-backup"),
                "data/stations-legacy-backup",
                message("station_state_directory_missing"),
                message("station_state_directory_not_readable"),
                issues);
        addLoaderIssues("recipes/chopping_board", plugin.choppingBoardRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/wok", plugin.wokRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/grinder", plugin.grinderRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/steamer", plugin.steamerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/oven", plugin.ovenRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/juicer", plugin.juicerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/fermentation_barrel", plugin.fermentationBarrelRecipeLoader().issues(), issues);
        issues.addAll(ItemRequirementSchemaValidator.validateFile(module(),
                new File(plugin.getDataFolder(), "config.yml"), "config.yml"));
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(plugin.getDataFolder(), "recipes"), "recipes"));
        boolean blockingIssue = issues.stream().anyMatch(issue -> issue.severity().blocking());
        if (!blockingIssue) {
            addMessageIssue("config.yml", ConfigPrecheckSeverity.INFO, "passed", issues);
        }
        addStationStorageHints(issues);
        return new ConfigPrecheckResult(module(), issues);
    }

    private void addStationStorageHints(List<ConfigPrecheckIssue> issues) {
        if (plugin.settingsService() == null) {
            return;
        }
        for (StationType type : StationType.values()) {
            for (ItemSourceRef source : plugin.settingsService().stationBlockSources(type)) {
                String shorthand = ItemSourceUtil.toShorthand(source);
                if (shorthand == null || shorthand.isBlank()) {
                    continue;
                }
                addIssue("stations." + type.folderName() + ".block_item_sources",
                        ConfigPrecheckSeverity.INFO,
                        message("station_storage", Map.of(
                                "station", type.folderName(),
                                "source", shorthand,
                                "backend", storageBackendHint(shorthand)
                        )),
                        issues);
            }
        }
    }

    private String storageBackendHint(String shorthand) {
        String normalized = shorthand.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("minecraft-")) {
            return message("station_storage_inspect_anchor");
        }
        String material = normalized.substring("minecraft-".length());
        if (isLikelyVanillaTileState(material)) {
            return message("station_storage_block_pdc", Map.of("material", material));
        }
        return message("station_storage_yaml_fallback");
    }

    private boolean isLikelyVanillaTileState(String material) {
        return List.of(
                "barrel",
                "furnace",
                "blast_furnace",
                "smoker",
                "brewing_stand",
                "chest",
                "trapped_chest",
                "dispenser",
                "dropper",
                "hopper",
                "lectern",
                "jukebox",
                "beacon",
                "campfire",
                "soul_campfire"
        ).contains(material) || material.endsWith("_shulker_box");
    }
}

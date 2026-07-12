package emaki.jiuwu.craft.cooking.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private static final String RECIPE_DIRECTORY_MISSING = "Recipe directory does not exist.";
    private static final String RECIPE_DIRECTORY_NOT_READABLE = "Recipe directory is not readable.";
    private static final String STATE_DIRECTORY_MISSING = "Station state directory does not exist.";
    private static final String STATE_DIRECTORY_NOT_READABLE = "Station state directory is not readable.";

    private final EmakiCookingPlugin plugin;

    public CookingConfigPrecheckContributor(EmakiCookingPlugin plugin) {
        super("cooking");
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        for (StationType type : StationType.values()) {
            checkDirectory(new File(plugin.getDataFolder(), "recipes/" + type.folderName()),
                    "recipes/" + type.folderName(),
                    RECIPE_DIRECTORY_MISSING,
                    RECIPE_DIRECTORY_NOT_READABLE,
                    issues);
        }
        checkDirectory(new File(plugin.getDataFolder(), "data/stations"),
                "data/stations",
                STATE_DIRECTORY_MISSING,
                STATE_DIRECTORY_NOT_READABLE,
                issues);
        checkDirectory(new File(plugin.getDataFolder(), "data/stations/index"),
                "data/stations/index",
                STATE_DIRECTORY_MISSING,
                STATE_DIRECTORY_NOT_READABLE,
                issues);
        checkDirectory(new File(plugin.getDataFolder(), "data/stations-legacy-backup"),
                "data/stations-legacy-backup",
                STATE_DIRECTORY_MISSING,
                STATE_DIRECTORY_NOT_READABLE,
                issues);
        addLoaderIssues("recipes/chopping_board", plugin.choppingBoardRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/wok", plugin.wokRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/grinder", plugin.grinderRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/steamer", plugin.steamerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/oven", plugin.ovenRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/juicer", plugin.juicerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/fermentation_barrel", plugin.fermentationBarrelRecipeLoader().issues(), issues);
        boolean blockingIssue = issues.stream().anyMatch(issue -> issue.severity().blocking());
        if (!blockingIssue) {
            addSuccessIssue(issues, "config.yml", "Cooking config precheck passed.");
        }
        addStationStorageHints(issues);
        return new ConfigPrecheckResult(module(), issues);
    }

    private void addStationStorageHints(List<ConfigPrecheckIssue> issues) {
        if (plugin.settingsService() == null) {
            return;
        }
        for (StationType type : StationType.values()) {
            for (ItemSource source : plugin.settingsService().stationBlockSources(type)) {
                String shorthand = ItemSourceUtil.toShorthand(source);
                if (shorthand == null || shorthand.isBlank()) {
                    continue;
                }
                addIssue("stations." + type.folderName() + ".block_item_sources",
                        ConfigPrecheckSeverity.INFO,
                        "Station storage precheck: " + type.folderName() + " source " + shorthand + " -> " + storageBackendHint(shorthand),
                        issues);
            }
        }
    }

    private String storageBackendHint(String shorthand) {
        String normalized = shorthand.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("minecraft-")) {
            return "backend depends on actual anchor BlockState; inspect a placed station.";
        }
        String material = normalized.substring("minecraft-".length());
        if (isLikelyVanillaTileState(material)) {
            return "placed vanilla " + material + " supports BLOCK_PDC.";
        }
        return "YAML fallback unless the placed block is TileState.";
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

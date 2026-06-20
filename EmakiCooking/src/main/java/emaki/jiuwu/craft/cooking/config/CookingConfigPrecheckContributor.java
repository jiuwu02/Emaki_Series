package emaki.jiuwu.craft.cooking.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private static final String RECIPE_DIRECTORY_MISSING = "Recipe directory does not exist.";
    private static final String RECIPE_DIRECTORY_NOT_READABLE = "Recipe directory is not readable.";

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
        addLoaderIssues("recipes/chopping_board", plugin.choppingBoardRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/wok", plugin.wokRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/grinder", plugin.grinderRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/steamer", plugin.steamerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/oven", plugin.ovenRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/juicer", plugin.juicerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/fermentation_barrel", plugin.fermentationBarrelRecipeLoader().issues(), issues);
        if (issues.isEmpty()) {
            addSuccessIssue(issues, "config.yml", "Cooking config precheck passed.");
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

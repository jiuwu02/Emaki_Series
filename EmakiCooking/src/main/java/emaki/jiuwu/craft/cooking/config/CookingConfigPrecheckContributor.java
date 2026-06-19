package emaki.jiuwu.craft.cooking.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class CookingConfigPrecheckContributor implements ConfigPrecheckContributor {

    private final EmakiCookingPlugin plugin;

    public CookingConfigPrecheckContributor(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String module() {
        return "cooking";
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        for (StationType type : StationType.values()) {
            checkDirectory(new File(plugin.getDataFolder(), "recipes/" + type.folderName()), "recipes/" + type.folderName(), issues);
        }
        addLoaderIssues("recipes/chopping_board", plugin.choppingBoardRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/wok", plugin.wokRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/grinder", plugin.grinderRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/steamer", plugin.steamerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/oven", plugin.ovenRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/juicer", plugin.juicerRecipeLoader().issues(), issues);
        addLoaderIssues("recipes/fermentation_barrel", plugin.fermentationBarrelRecipeLoader().issues(), issues);
        if (issues.isEmpty()) {
            issues.add(ConfigPrecheckIssue.of(module(), "config.yml", ConfigPrecheckSeverity.INFO, "Cooking config precheck passed."));
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkFile(File file, String path, List<ConfigPrecheckIssue> issues) {
        if (file == null || !file.exists()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Required file does not exist."));
            return;
        }
        if (!file.isFile()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Path is not a file."));
            return;
        }
        if (!file.canRead()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "File is not readable."));
        }
    }

    private void checkDirectory(File directory, String path, List<ConfigPrecheckIssue> issues) {
        if (directory == null || !directory.exists()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Recipe directory does not exist."));
            return;
        }
        if (!directory.isDirectory()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Path is not a directory."));
            return;
        }
        if (!directory.canRead()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Recipe directory is not readable."));
        }
    }

    private void addLoaderIssues(String path, List<String> loaderIssues, List<ConfigPrecheckIssue> issues) {
        if (loaderIssues == null || loaderIssues.isEmpty()) {
            return;
        }
        for (String issue : loaderIssues) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, issue));
        }
    }
}

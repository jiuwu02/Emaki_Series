package emaki.jiuwu.craft.forge.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ForgeConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiForgePlugin plugin;

    public ForgeConfigPrecheckContributor(EmakiForgePlugin plugin) {
        super("forge");
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "recipes"), "recipes", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        addLoaderIssues("recipes", plugin.recipeLoader() == null ? null : plugin.recipeLoader().issues(), issues);
        if (issues.isEmpty()) {
            addSuccessIssue(issues, "config.yml", "Forge config precheck passed.");
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

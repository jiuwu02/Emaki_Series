package emaki.jiuwu.craft.gem.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiGemPlugin plugin;

    public GemConfigPrecheckContributor(EmakiGemPlugin plugin) {
        super("gem");
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gems"), "gems", issues);
        checkDirectory(new File(plugin.getDataFolder(), "items"), "items", issues);
        checkDirectory(new File(plugin.getDataFolder(), "resonances"), "resonances", issues);
        checkDirectory(new File(plugin.getDataFolder(), "conditions"), "conditions", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        addLoaderIssues("gems", plugin.gemLoader() == null ? null : plugin.gemLoader().issues(), issues);
        addLoaderIssues("items", plugin.gemItemLoader() == null ? null : plugin.gemItemLoader().issues(), issues);
        addLoaderIssues("resonances", plugin.resonanceLoader() == null ? null : plugin.resonanceLoader().issues(), issues);
        if (issues.isEmpty()) {
            addSuccessIssue(issues, "config.yml", "Gem config precheck passed.");
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

package emaki.jiuwu.craft.level.config;

import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

public final class LevelConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiLevelPlugin plugin;

    public LevelConfigPrecheckContributor(EmakiLevelPlugin plugin) {
        super("level", plugin::messages);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkFile(new File(plugin.getDataFolder(), "requirements.yml"), "requirements.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "types"), "types", issues);
        checkDirectory(new File(plugin.getDataFolder(), "sources"), "sources", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

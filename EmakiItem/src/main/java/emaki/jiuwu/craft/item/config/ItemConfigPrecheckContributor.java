package emaki.jiuwu.craft.item.config;

import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiItemPlugin plugin;

    public ItemConfigPrecheckContributor(EmakiItemPlugin plugin) {
        super("item", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkFile(new File(plugin.getDataFolder(), "id_aliases.yml"), "id_aliases.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "items"), "items", issues);
        checkDirectory(new File(plugin.getDataFolder(), "sets"), "sets", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

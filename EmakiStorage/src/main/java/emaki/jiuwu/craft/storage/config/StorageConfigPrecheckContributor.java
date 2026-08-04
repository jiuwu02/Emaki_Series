package emaki.jiuwu.craft.storage.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;

public final class StorageConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiStoragePlugin plugin;

    public StorageConfigPrecheckContributor(EmakiStoragePlugin plugin) {
        super("storage", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        addLoaderIssues("gui", plugin.guiTemplateLoader() == null ? null : plugin.guiTemplateLoader().issues(), issues);
        // unlock_costs.yml is deliberately not checked: the coordinator treats a missing or empty file as
        // "paid expansion disabled" rather than an error, so requiring it would reject a valid install.
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

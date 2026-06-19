package emaki.jiuwu.craft.item.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemConfigPrecheckContributor implements ConfigPrecheckContributor {

    private final EmakiItemPlugin plugin;

    public ItemConfigPrecheckContributor(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String module() {
        return "item";
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
            issues.add(ConfigPrecheckIssue.of(module(), "config.yml", ConfigPrecheckSeverity.INFO, "Item config precheck passed."));
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
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Required directory does not exist."));
            return;
        }
        if (!directory.isDirectory()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Path is not a directory."));
            return;
        }
        if (!directory.canRead()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "Directory is not readable."));
        }
    }
}

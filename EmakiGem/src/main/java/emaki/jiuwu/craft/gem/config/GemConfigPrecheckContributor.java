package emaki.jiuwu.craft.gem.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemConfigPrecheckContributor implements ConfigPrecheckContributor {

    private final EmakiGemPlugin plugin;

    public GemConfigPrecheckContributor(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String module() {
        return "gem";
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
            issues.add(ConfigPrecheckIssue.of(module(), "config.yml", ConfigPrecheckSeverity.INFO, "Gem config precheck passed."));
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

    private void addLoaderIssues(String path, List<String> loaderIssues, List<ConfigPrecheckIssue> issues) {
        if (loaderIssues == null || loaderIssues.isEmpty()) {
            return;
        }
        for (String issue : loaderIssues) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, issue));
        }
    }
}

package emaki.jiuwu.craft.attribute.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity;

public final class AttributeConfigPrecheckContributor implements ConfigPrecheckContributor {

    private final EmakiAttributePlugin plugin;

    public AttributeConfigPrecheckContributor(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String module() {
        return "attribute";
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        addLoaderIssues("attributes", plugin.attributeRegistry() == null ? null : plugin.attributeRegistry().issues(), issues);
        addLoaderIssues("damage_types", plugin.damageTypeRegistry() == null ? null : plugin.damageTypeRegistry().issues(), issues);
        addLoaderIssues("default_profiles", plugin.defaultProfileRegistry() == null ? null : plugin.defaultProfileRegistry().issues(), issues);
        addLoaderIssues("lore_formats", plugin.loreFormatRegistry() == null ? null : plugin.loreFormatRegistry().issues(), issues);
        addLoaderIssues("presets", plugin.presetRegistry() == null ? null : plugin.presetRegistry().issues(), issues);
        addLoaderIssues("pdc_read_rules", plugin.pdcReadRuleLoader() == null ? null : plugin.pdcReadRuleLoader().issues(), issues);
        if (issues.isEmpty()) {
            issues.add(ConfigPrecheckIssue.of(module(), "config.yml", ConfigPrecheckSeverity.INFO, "Attribute config precheck passed."));
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

    private void addLoaderIssues(String path, List<String> loaderIssues, List<ConfigPrecheckIssue> issues) {
        if (loaderIssues == null || loaderIssues.isEmpty()) {
            return;
        }
        for (String issue : loaderIssues) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, issue));
        }
    }
}

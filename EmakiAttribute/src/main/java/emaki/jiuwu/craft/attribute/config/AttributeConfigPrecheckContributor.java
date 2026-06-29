package emaki.jiuwu.craft.attribute.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;

public final class AttributeConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiAttributePlugin plugin;

    public AttributeConfigPrecheckContributor(EmakiAttributePlugin plugin) {
        super("attribute");
        this.plugin = plugin;
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
            addSuccessIssue(issues, "config.yml", "Attribute config precheck passed.");
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

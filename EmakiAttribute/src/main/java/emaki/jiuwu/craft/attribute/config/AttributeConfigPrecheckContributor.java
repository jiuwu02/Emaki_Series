package emaki.jiuwu.craft.attribute.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;

public final class AttributeConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiAttributePlugin plugin;

    public AttributeConfigPrecheckContributor(EmakiAttributePlugin plugin) {
        super("attribute", plugin::messageService);
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
        AttributeConfig attributeConfig = plugin.configModel();
        if (!attributeConfig.readLoreAttributes() && !attributeConfig.readPdcAttributes()) {
            addMessageIssue("config.yml:attribute_sources", WARN, "attribute_sources_disabled", issues);
        } else if (attributeConfig.requireLorePdcMatch()
                && (!attributeConfig.readLoreAttributes() || !attributeConfig.readPdcAttributes())) {
            addMessageIssue("config.yml:attribute_sources.require_lore_pdc_match", WARN, "attribute_sources_match_requires_both", issues);
        }

        for (ScalingCurveConfig curve : attributeConfig.scalingCurves()) {
            if (!ScalingCurveConfig.isSupportedCurveType(curve.curveType())) {
                addMessageIssue("config.yml:scaling_curves." + curve.attributeId(), WARN,
                        "scaling_curve_type_unknown",
                        Map.of("attribute", curve.attributeId(), "curve_type", curve.curveType()),
                        issues);
            }
        }
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

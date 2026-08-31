package emaki.jiuwu.craft.mobs.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.ERROR;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;

public final class MobsConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    public static final String MODULE = "mobs";

    private final EmakiMobsPlugin plugin;

    public MobsConfigPrecheckContributor(EmakiMobsPlugin plugin) {
        super(MODULE, plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkFile(new File(plugin.getDataFolder(), "target_selectors.yml"), "target_selectors.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "mobs"), "mobs", issues);
        checkDirectory(new File(plugin.getDataFolder(), "loot_tables"), "loot_tables", issues);
        checkDirectory(new File(plugin.getDataFolder(), "spawn_rules"), "spawn_rules", issues);
        addLoaderIssues("mobs", plugin.mobDefinitionLoader() == null
                ? null : plugin.mobDefinitionLoader().issues(), issues);
        addTargetSelectorIssues(issues);
        addLoaderIssues("loot_tables", plugin.lootTableLoader() == null
                ? null : plugin.lootTableLoader().issues(), issues);
        addLoaderIssues("spawn_rules", plugin.spawnRuleLoader() == null
                ? null : plugin.spawnRuleLoader().issues(), issues);
        addDeprecations(issues);
        addStackingHints(issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void addTargetSelectorIssues(List<ConfigPrecheckIssue> issues) {
        if (plugin.targetSelectorLoader() == null) {
            return;
        }
        var severity = plugin.targetSelectorLoader().hasBlockingIssues() ? ERROR : WARN;
        for (String issue : plugin.targetSelectorLoader().issues()) {
            addIssue("target_selectors", severity, issue, issues);
        }
    }

    private void addDeprecations(List<ConfigPrecheckIssue> issues) {
        if (plugin.mobDefinitionLoader() == null) {
            return;
        }
        for (String deprecation : plugin.mobDefinitionLoader().deprecations()) {
            addIssue("mobs", WARN, deprecation, issues);
        }
    }

    private void addStackingHints(List<ConfigPrecheckIssue> issues) {
        if (plugin.mobRegistry() == null) {
            return;
        }
        plugin.mobRegistry().get().forEach((mobId, spec) -> {
            if (!spec.components().containsKey("max_health")
                    || !spec.eaAttributes().containsKey("max_health_vanilla")) {
                return;
            }
            addMessageIssue("mobs", WARN, "mobs_max_health_stacking",
                    Map.of("mob_id", mobId), issues);
        });
    }
}

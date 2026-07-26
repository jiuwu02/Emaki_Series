package emaki.jiuwu.craft.skills.config;

import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

public final class SkillsConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiSkillsPlugin plugin;

    public SkillsConfigPrecheckContributor(EmakiSkillsPlugin plugin) {
        super("skills", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "skills"), "skills", issues);
        checkDirectory(new File(plugin.getDataFolder(), "resources"), "resources", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        addLoaderIssues("skills", plugin.skillDefinitionLoader() == null ? null : plugin.skillDefinitionLoader().issues(), issues);
        addLoaderIssues("resources", plugin.localResourceDefinitionLoader() == null ? null : plugin.localResourceDefinitionLoader().issues(), issues);
        AppConfig.SkillSourceSettings skillSources = plugin.appConfig().skillSources();
        if (!skillSources.readLoreSkills() && !skillSources.readPdcSkills()) {
            addMessageIssue("config.yml:skill_sources", WARN, "skill_sources_disabled", issues);
        } else if (skillSources.requireLorePdcMatch()
                && (!skillSources.readLoreSkills() || !skillSources.readPdcSkills())) {
            addMessageIssue("config.yml:skill_sources.require_lore_pdc_match", WARN, "skill_sources_match_requires_both", issues);
        }
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}

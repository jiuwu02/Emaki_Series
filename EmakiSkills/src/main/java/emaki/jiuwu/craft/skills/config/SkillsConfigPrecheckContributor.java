package emaki.jiuwu.craft.skills.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.script.SkillPipelineRuntime;

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
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(plugin.getDataFolder(), "skills"), "skills"));
        issues.addAll(ItemRequirementSchemaValidator.validateDirectory(module(),
                new File(plugin.getDataFolder(), "resources"), "resources"));
        AppConfig.SkillSourceSettings skillSources = plugin.appConfig().skillSources();
        if (!skillSources.readLoreSkills() && !skillSources.readPdcSkills()) {
            addMessageIssue("config.yml:skill_sources", WARN, "skill_sources_disabled", issues);
        } else if (skillSources.requireLorePdcMatch()
                && (!skillSources.readLoreSkills() || !skillSources.readPdcSkills())) {
            addMessageIssue("config.yml:skill_sources.require_lore_pdc_match", WARN, "skill_sources_match_requires_both", issues);
        }
        checkSkillScripts(issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkSkillScripts(List<ConfigPrecheckIssue> issues) {
        SkillDefinitionLoader loader = plugin.skillDefinitionLoader();
        SkillPipelineRuntime runtime = plugin.skillPipelineRuntime();
        if (loader == null || runtime == null) {
            return;
        }
        for (SkillDefinition definition : loader.all().values()) {
            if (definition == null || definition.script() == null || !definition.script().enabled()) {
                continue;
            }
            for (SkillPipelineRuntime.PhaseDiagnostic entry
                    : runtime.validate(definition.id(), definition.script())) {
                addDiagnosticIssue(entry, issues);
            }
        }
    }

    private void addDiagnosticIssue(SkillPipelineRuntime.PhaseDiagnostic entry,
            List<ConfigPrecheckIssue> issues) {
        CompileDiagnostic diagnostic = entry.diagnostic();
        String path = "skills/" + entry.skillId() + ".yml:script.actions."
                + entry.phase().configKey();
        String reasonKey = diagnostic.reasonKey();
        if ("action.validate.unknown_stage".equals(reasonKey)) {

            addMessageIssue(path, WARN, "script_action_unknown", Map.of(
                    "skill", entry.skillId(),
                    "line", entry.lineNumber(),
                    "action", diagnostic.token()), issues);
            return;
        }
        if ("action.validate.missing_required_argument".equals(reasonKey)) {
            addMessageIssue(path, WARN, "script_argument_missing", Map.of(
                    "skill", entry.skillId(),
                    "line", entry.lineNumber(),
                    "action", diagnostic.token(),
                    "argument", Texts.toStringSafe(diagnostic.detail().get("argument"))), issues);
            return;
        }

        String detail = plugin.coreLib() == null || plugin.coreLib().messageService() == null
                ? diagnostic.toString()
                : plugin.coreLib().messageService().renderDiagnostic(diagnostic);
        addMessageIssue(path, WARN, "script_syntax_error", Map.of(
                "skill", entry.skillId(),
                "line", entry.lineNumber(),
                "detail", detail), issues);
    }
}

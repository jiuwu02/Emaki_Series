package emaki.jiuwu.craft.skills.config;

import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.WARN;

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
import emaki.jiuwu.craft.corelib.text.Texts;
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

    /**
     * Reports every skill script line that failed to compile.
     *
     * <p>The check itself moved into {@link SkillPipelineRuntime}: compilation already happens at load time and
     * already knows what is wrong with a line, so this method compiles each script once and translates the
     * diagnostics rather than re-implementing a parser and an argument check.</p>
     *
     * <p>Diagnostics are grouped onto the three keys this module's language files define. Anything CoreLib
     * reports that is not an unknown stage or a missing argument lands on {@code script_syntax_error}, which is
     * accurate: every remaining case is a line the author has to rewrite.</p>
     */
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
            runtime.precompile(definition.id(), definition.script());
        }
        for (SkillPipelineRuntime.PhaseDiagnostic entry : runtime.diagnostics()) {
            addDiagnosticIssue(entry, issues);
        }
    }

    private void addDiagnosticIssue(SkillPipelineRuntime.PhaseDiagnostic entry,
            List<ConfigPrecheckIssue> issues) {
        CompileDiagnostic diagnostic = entry.diagnostic();
        String path = "skills/" + entry.skillId() + ".yml:script.actions."
                + entry.phase().configKey();
        String reasonKey = diagnostic.reasonKey();
        if ("action.validate.unknown_stage".equals(reasonKey)) {
            // The offending token is what the author has to fix. The full stage list is not appended: it
            // runs to dozens of ids, buries the line number it is attached to, and is already documented.
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
        addMessageIssue(path, WARN, "script_syntax_error", Map.of(
                "skill", entry.skillId(),
                "line", entry.lineNumber(),
                "detail", diagnostic.toString()), issues);
    }
}

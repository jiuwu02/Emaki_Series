package emaki.jiuwu.craft.skills.config;

import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.INFO;
import static emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckSeverity.WARN;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.loader.SkillDefinitionLoader;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.script.SkillScriptPhase;

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
     * Validates every loaded skill's script lines against the registered actions.
     *
     * <p>The loader stores script lines verbatim, so an unknown action id, a broken
     * line or a missing required argument used to stay invisible until a player
     * cast the skill and received a generic failure message. Checking here reports
     * the mistake with its skill id and line number while the server is starting.
     *
     * <p>Runs against the action registry, so it only covers actions registered by
     * the time the precheck executes. Actions contributed later by other plugins
     * are reported as unknown and are therefore skipped rather than flagged.
     */
    private void checkSkillScripts(List<ConfigPrecheckIssue> issues) {
        SkillDefinitionLoader loader = plugin.skillDefinitionLoader();
        SkillScriptActionRegistry registry = plugin.skillScriptActionRegistry();
        if (loader == null || registry == null) {
            return;
        }
        Map<String, SkillScriptAction> actions = registry.all();
        if (actions == null || actions.isEmpty()) {
            return;
        }
        ActionLineParser lineParser = new ActionLineParser();
        for (SkillDefinition definition : loader.all().values()) {
            if (definition == null || definition.script() == null || !definition.script().enabled()) {
                continue;
            }
            for (SkillScriptPhase phase : SkillScriptPhase.values()) {
                checkScriptPhase(definition, phase, actions, lineParser, issues);
            }
        }
    }

    private void checkScriptPhase(SkillDefinition definition,
            SkillScriptPhase phase,
            Map<String, SkillScriptAction> actions,
            ActionLineParser lineParser,
            List<ConfigPrecheckIssue> issues) {
        List<String> lines = definition.script().lines(phase);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String path = "skills/" + definition.id() + ".yml:script.actions." + Texts.lower(phase.name());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (Texts.isBlank(line)) {
                continue;
            }
            int lineNumber = index + 1;
            ParsedActionLine parsed;
            try {
                parsed = lineParser.parse(lineNumber, line);
            } catch (ActionSyntaxException exception) {
                addMessageIssue(path, WARN, "script_syntax_error", Map.of(
                        "skill", definition.id(),
                        "line", lineNumber,
                        "detail", Texts.toStringSafe(exception.getMessage())), issues);
                continue;
            }
            if (parsed == null) {
                continue;
            }
            SkillScriptAction action = actions.get(Texts.normalizeId(parsed.actionId()));
            if (action == null) {
                addMessageIssue(path, WARN, "script_action_unknown", Map.of(
                        "skill", definition.id(),
                        "line", lineNumber,
                        "action", Texts.toStringSafe(parsed.actionId()),
                        "available", String.join(", ", new TreeSet<>(actions.keySet()))), issues);
                continue;
            }
            checkRequiredArguments(definition, action, parsed, path, lineNumber, issues);
        }
    }

    /**
     * Reports required arguments that the script line never supplies. A missing
     * required argument otherwise surfaces only as a runtime failure with no hint
     * about which argument was expected.
     */
    private void checkRequiredArguments(SkillDefinition definition,
            SkillScriptAction action,
            ParsedActionLine parsed,
            String path,
            int lineNumber,
            List<ConfigPrecheckIssue> issues) {
        List<SkillActionParameter> parameters = action.parameters();
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        Map<String, String> arguments = parsed.arguments() == null ? Map.of() : parsed.arguments();
        for (SkillActionParameter parameter : parameters) {
            if (parameter == null || !parameter.required() || Texts.isBlank(parameter.name())) {
                continue;
            }
            if (!arguments.containsKey(parameter.name())) {
                addMessageIssue(path, WARN, "script_argument_missing", Map.of(
                        "skill", definition.id(),
                        "line", lineNumber,
                        "action", Texts.toStringSafe(action.id()),
                        "argument", parameter.name(),
                        "provided", arguments.isEmpty() ? "-" : String.join(", ", new TreeSet<>(arguments.keySet()))), issues);
            }
        }
    }
}

package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.text.Texts;

final class CoreLibConfigPrecheckContributor implements ConfigPrecheckContributor {

    @Override
    public String module() {
        return "corelib";
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkLoopConfig(config.loopConfig(), issues);
        checkTemplates(config.actionTemplates(), context, issues);
        checkScriptSecurity(config, issues);
        checkWebConsole(config, issues);
        if (issues.isEmpty()) {
            issues.add(ConfigPrecheckIssue.of(module(), "config.yml", ConfigPrecheckSeverity.INFO, "CoreLib config precheck passed."));
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkLoopConfig(CoreLibConfig.LoopConfig loop, List<ConfigPrecheckIssue> issues) {
        CoreLibConfig.LoopConfig safe = loop == null ? CoreLibConfig.LoopConfig.defaults() : loop;
        if (safe.maxTimes() <= 0) {
            issues.add(ConfigPrecheckIssue.of(module(), "action.loop.max_times", ConfigPrecheckSeverity.ERROR, "max_times must be greater than 0."));
        }
        if (safe.minSyncIntervalTicks() < 1L) {
            issues.add(ConfigPrecheckIssue.of(module(), "action.loop.min_sync_interval", ConfigPrecheckSeverity.ERROR, "min_sync_interval cannot be lower than 1 tick."));
        }
        if (safe.minAsyncIntervalTicks() < 1L) {
            issues.add(ConfigPrecheckIssue.of(module(), "action.loop.min_async_interval", ConfigPrecheckSeverity.ERROR, "min_async_interval cannot be lower than 1 tick."));
        }
        if (safe.maxActiveLoopsTotal() <= 0 || safe.maxActiveLoopsPerPlayer() <= 0 || safe.maxActiveLoopsPerPlugin() <= 0) {
            issues.add(ConfigPrecheckIssue.of(module(), "action.loop", ConfigPrecheckSeverity.ERROR, "Loop active limits must be greater than 0."));
        }
    }

    private void checkTemplates(Map<String, List<String>> templates, ConfigPrecheckContext context, List<ConfigPrecheckIssue> issues) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        ActionLineParser parser = context.lineParser() == null ? new ActionLineParser() : context.lineParser();
        Set<String> templateIds = new HashSet<>();
        for (String id : templates.keySet()) {
            if (Texts.isBlank(id)) {
                issues.add(ConfigPrecheckIssue.of(module(), "action.templates", ConfigPrecheckSeverity.ERROR, "Template id cannot be blank."));
                continue;
            }
            templateIds.add(Texts.lower(id));
        }
        for (Map.Entry<String, List<String>> entry : templates.entrySet()) {
            String id = entry.getKey();
            List<String> lines = entry.getValue() == null ? List.of() : entry.getValue();
            for (int index = 0; index < lines.size(); index++) {
                try {
                    ParsedActionLine parsed = parser.parse(index + 1, lines.get(index));
                    if (parsed == null) {
                        continue;
                    }
                    if ("usetemplate".equals(parsed.actionId())) {
                        String referenced = parsed.arguments().get("name");
                        if (Texts.isNotBlank(referenced) && !templateIds.contains(Texts.lower(referenced))) {
                            issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.ERROR,
                                    "Template references missing template: " + referenced));
                        }
                    }
                    if (context.actionRegistry() != null && context.actionRegistry().get(parsed.actionId()) == null && !"usetemplate".equals(parsed.actionId())) {
                        issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.WARN,
                                "Action is not registered during precheck: " + parsed.actionId()));
                    }
                    Action action = context.actionRegistry() == null ? null : context.actionRegistry().get(parsed.actionId());
                    if ("loopasync".equals(parsed.actionId()) && action != null && action.executionMode() != ActionExecutionMode.ASYNC_IO) {
                        issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.WARN,
                                "loopasync should only be used with async-safe templates."));
                    }
                } catch (ActionSyntaxException exception) {
                    issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.ERROR,
                            "Invalid action syntax at line " + (index + 1) + ": " + exception.getMessage()));
                }
            }
        }
    }

    private void checkScriptSecurity(CoreLibConfig config, List<ConfigPrecheckIssue> issues) {
        if (config.scriptConfig() == null || config.scriptConfig().security() == null) {
            return;
        }
        var security = config.scriptConfig().security();
        if (security.allowActionDispatch() && security.maxActionDepth() > 5) {
            issues.add(ConfigPrecheckIssue.of(module(), "script.security.max_action_depth", ConfigPrecheckSeverity.WARN,
                    "Large script action depth may cause difficult-to-trace action chains."));
        }
    }

    private void checkWebConsole(CoreLibConfig config, List<ConfigPrecheckIssue> issues) {
        if (config.webConsoleConfig() == null || config.webConsoleConfig().security() == null) {
            return;
        }
        var web = config.webConsoleConfig();
        if (web.enabled() && web.hasUnsafeDefaultPassword()) {
            issues.add(ConfigPrecheckIssue.of(module(), "web_console.auth.password", ConfigPrecheckSeverity.ERROR,
                    "Web Console is enabled but password is blank or still using the default value."));
        }
        if (web.security().maxRequestBodyKb() <= 0) {
            issues.add(ConfigPrecheckIssue.of(module(), "web_console.security.max_request_body_kb", ConfigPrecheckSeverity.ERROR,
                    "max_request_body_kb must be greater than 0."));
        }
        if (web.enabled() && web.security().allowConfigWrite() && web.security().allowedModules().isEmpty()) {
            issues.add(ConfigPrecheckIssue.of(module(), "web_console.security.allowed_modules", ConfigPrecheckSeverity.WARN,
                    "Config write is enabled without an allowed_modules allowlist."));
        }
    }
}

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
import emaki.jiuwu.craft.corelib.action.ActionParsers;
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
        CoreLibConfig safeConfig = config == null ? CoreLibConfig.defaults() : config;
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkLoopConfig(safeConfig.loopConfig(), issues);
        checkTemplates(safeConfig.loopConfig(), safeConfig.actionTemplates(), context, issues);
        checkScriptSecurity(safeConfig, issues);
        checkWebConsole(safeConfig, issues);
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

    private void checkTemplates(CoreLibConfig.LoopConfig loop,
            Map<String, List<String>> templates,
            ConfigPrecheckContext context,
            List<ConfigPrecheckIssue> issues) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        CoreLibConfig.LoopConfig loopConfig = loop == null ? CoreLibConfig.LoopConfig.defaults() : loop;
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
                    Action action = context.actionRegistry() == null ? null : context.actionRegistry().get(parsed.actionId());
                    if (action == null && context.actionRegistry() != null && !"usetemplate".equals(parsed.actionId())) {
                        issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.WARN,
                                "Action is not registered during precheck: " + parsed.actionId()));
                    }
                    if ("usetemplate".equals(parsed.actionId())) {
                        checkUseTemplate(id, parsed, templateIds, issues);
                    } else if ("loopsync".equals(parsed.actionId()) || "loopasync".equals(parsed.actionId())) {
                        checkLoopActionTemplate(id, parsed, loopConfig, templates, templateIds, parser, context, issues);
                    } else if ("cancelloop".equals(parsed.actionId())) {
                        checkCancelLoopTemplate(id, parsed, issues);
                    }
                } catch (ActionSyntaxException exception) {
                    issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + id, ConfigPrecheckSeverity.ERROR,
                            "Invalid action syntax at line " + (index + 1) + ": " + exception.getMessage()));
                }
            }
        }
    }

    private void checkUseTemplate(String templateId, ParsedActionLine parsed, Set<String> templateIds, List<ConfigPrecheckIssue> issues) {
        String referenced = parsed.arguments().get("name");
        if (Texts.isNotBlank(referenced) && !templateIds.contains(Texts.lower(referenced))) {
            issues.add(ConfigPrecheckIssue.of(module(), "action.templates." + templateId, ConfigPrecheckSeverity.ERROR,
                    "Template references missing template: " + referenced));
        }
    }

    private void checkLoopActionTemplate(String templateId,
            ParsedActionLine parsed,
            CoreLibConfig.LoopConfig loopConfig,
            Map<String, List<String>> templates,
            Set<String> templateIds,
            ActionLineParser parser,
            ConfigPrecheckContext context,
            List<ConfigPrecheckIssue> issues) {
        Map<String, String> arguments = parsed.arguments();
        String actionId = parsed.actionId();
        String path = "action.templates." + templateId;
        String referencedTemplate = arguments.get("template");
        if (Texts.isBlank(referencedTemplate)) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " requires template."));
        } else if (!templateIds.contains(Texts.lower(referencedTemplate))) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " references missing template: " + referencedTemplate));
        }

        Integer times = parseInt(arguments.get("times"));
        if (times == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " requires numeric times."));
        } else if (times <= 0) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " times must be greater than 0."));
        } else if (times > loopConfig.maxTimes()) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " times exceeds max_times."));
        }

        Long interval = parseTicks(arguments.get("interval"));
        if (interval == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " requires valid interval."));
        } else {
            long minInterval = "loopasync".equals(actionId) ? loopConfig.minAsyncIntervalTicks() : loopConfig.minSyncIntervalTicks();
            if (interval < minInterval) {
                issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                        actionId + " interval is lower than the configured minimum."));
            }
        }

        if (arguments.containsKey("initial_delay") && parseTicks(arguments.get("initial_delay")) == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                    actionId + " initial_delay is invalid."));
        }
        checkLoopFlags(templateId, actionId, arguments, issues);
        if ("loopasync".equals(actionId) && Texts.isNotBlank(referencedTemplate)) {
            checkAsyncSafeLoopTemplate(templateId, referencedTemplate, templates, parser, context, issues);
        }
    }

    private void checkAsyncSafeLoopTemplate(String ownerTemplateId,
            String referencedTemplate,
            Map<String, List<String>> templates,
            ActionLineParser parser,
            ConfigPrecheckContext context,
            List<ConfigPrecheckIssue> issues) {
        List<String> lines = templateLines(templates, referencedTemplate);
        if (lines == null) {
            return;
        }
        String path = "action.templates." + ownerTemplateId;
        for (int index = 0; index < lines.size(); index++) {
            try {
                ParsedActionLine nested = parser.parse(index + 1, lines.get(index));
                if (nested == null) {
                    continue;
                }
                if ("usetemplate".equals(nested.actionId())) {
                    issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                            "loopasync template cannot include nested usetemplate: " + referencedTemplate));
                    continue;
                }
                Action nestedAction = context.actionRegistry() == null ? null : context.actionRegistry().get(nested.actionId());
                if (nestedAction == null || nestedAction.executionMode() != ActionExecutionMode.ASYNC_IO) {
                    issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                            "loopasync template contains non-async-safe action: " + nested.actionId()));
                }
            } catch (ActionSyntaxException exception) {
                issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                        "Invalid loopasync template syntax at line " + (index + 1) + ": " + exception.getMessage()));
            }
        }
    }

    private List<String> templateLines(Map<String, List<String>> templates, String id) {
        if (templates == null || Texts.isBlank(id)) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : templates.entrySet()) {
            if (Texts.lower(entry.getKey()).equals(Texts.lower(id))) {
                return entry.getValue() == null ? List.of() : entry.getValue();
            }
        }
        return null;
    }

    private void checkCancelLoopTemplate(String templateId, ParsedActionLine parsed, List<ConfigPrecheckIssue> issues) {
        Map<String, String> arguments = parsed.arguments();
        String path = "action.templates." + templateId;
        if (Texts.isBlank(arguments.get("key"))) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "cancelloop requires key."));
        }
        String match = Texts.lower(arguments.getOrDefault("match", "exact"));
        if (!"exact".equals(match) && !"prefix".equals(match)) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "cancelloop match must be exact or prefix."));
        }
        if (arguments.containsKey("silent") && ActionParsers.parseBoolean(arguments.get("silent")) == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, "cancelloop silent must be boolean."));
        }
    }

    private void checkLoopFlags(String templateId, String actionId, Map<String, String> arguments, List<ConfigPrecheckIssue> issues) {
        String path = "action.templates." + templateId;
        if (arguments.containsKey("mode")) {
            String mode = Texts.lower(arguments.get("mode"));
            if (!"replace".equals(mode) && !"refresh".equals(mode) && !"ignore".equals(mode) && !"allow_duplicate".equals(mode)) {
                issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR,
                        actionId + " mode must be replace, refresh, ignore, or allow_duplicate."));
            }
        }
        if (arguments.containsKey("stop_if_offline") && ActionParsers.parseBoolean(arguments.get("stop_if_offline")) == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, actionId + " stop_if_offline must be boolean."));
        }
        if (arguments.containsKey("stop_if_dead") && ActionParsers.parseBoolean(arguments.get("stop_if_dead")) == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, actionId + " stop_if_dead must be boolean."));
        }
        if (arguments.containsKey("stop_on_failure") && ActionParsers.parseBoolean(arguments.get("stop_on_failure")) == null) {
            issues.add(ConfigPrecheckIssue.of(module(), path, ConfigPrecheckSeverity.ERROR, actionId + " stop_on_failure must be boolean."));
        }
    }

    private Integer parseInt(String raw) {
        try {
            return Texts.isBlank(raw) ? null : Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseTicks(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        long parsed = ActionParsers.parseTicks(raw);
        return parsed < 0L ? null : parsed;
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

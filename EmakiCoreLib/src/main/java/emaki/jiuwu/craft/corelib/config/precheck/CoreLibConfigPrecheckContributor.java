package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

final class CoreLibConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    CoreLibConfigPrecheckContributor(Supplier<? extends LogMessages> messagesSupplier) {
        super("corelib", messagesSupplier);
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
            addMessageIssue("config.yml", ConfigPrecheckSeverity.INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkLoopConfig(CoreLibConfig.LoopConfig loop, List<ConfigPrecheckIssue> issues) {
        CoreLibConfig.LoopConfig safe = loop == null ? CoreLibConfig.LoopConfig.defaults() : loop;
        if (safe.maxTimes() <= 0) {
            addMessageIssue("action.loop.max_times", ConfigPrecheckSeverity.ERROR, "loop_max_times_invalid", issues);
        }
        if (safe.minSyncIntervalTicks() < 1L) {
            addMessageIssue("action.loop.min_sync_interval", ConfigPrecheckSeverity.ERROR, "loop_min_sync_interval_invalid", issues);
        }
        if (safe.minAsyncIntervalTicks() < 1L) {
            addMessageIssue("action.loop.min_async_interval", ConfigPrecheckSeverity.ERROR, "loop_min_async_interval_invalid", issues);
        }
        if (safe.maxActiveLoopsTotal() <= 0
                || safe.maxActiveLoopsPerPlayer() <= 0
                || safe.maxActiveLoopsPerPlugin() <= 0) {
            addMessageIssue("action.loop", ConfigPrecheckSeverity.ERROR, "loop_active_limits_invalid", issues);
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
                addMessageIssue("action.templates", ConfigPrecheckSeverity.ERROR, "template_blank_id", issues);
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
                        addMessageIssue(
                                "action.templates." + id,
                                ConfigPrecheckSeverity.WARN,
                                "template_action_unregistered",
                                Map.of("action", parsed.actionId()),
                                issues
                        );
                    }
                    if ("usetemplate".equals(parsed.actionId())) {
                        checkUseTemplate(id, parsed, templateIds, issues);
                    } else if ("loopsync".equals(parsed.actionId()) || "loopasync".equals(parsed.actionId())) {
                        checkLoopActionTemplate(id, parsed, loopConfig, templates, templateIds, parser, context, issues);
                    } else if ("cancelloop".equals(parsed.actionId())) {
                        checkCancelLoopTemplate(id, parsed, issues);
                    }
                } catch (ActionSyntaxException exception) {
                    addMessageIssue(
                            "action.templates." + id,
                            ConfigPrecheckSeverity.ERROR,
                            "template_invalid_syntax",
                            Map.of(
                                    "line", index + 1,
                                    "error", Texts.toStringSafe(exception.getMessage())
                            ),
                            issues
                    );
                }
            }
        }
    }

    private void checkUseTemplate(String templateId,
            ParsedActionLine parsed,
            Set<String> templateIds,
            List<ConfigPrecheckIssue> issues) {
        String referenced = parsed.arguments().get("name");
        if (Texts.isNotBlank(referenced) && !templateIds.contains(Texts.lower(referenced))) {
            addMessageIssue(
                    "action.templates." + templateId,
                    ConfigPrecheckSeverity.ERROR,
                    "template_missing_reference",
                    Map.of("template", referenced),
                    issues
            );
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
            addActionIssue(path, "loop_template_required", actionId, issues);
        } else if (!templateIds.contains(Texts.lower(referencedTemplate))) {
            addMessageIssue(
                    path,
                    ConfigPrecheckSeverity.ERROR,
                    "loop_template_missing",
                    Map.of("action", actionId, "template", referencedTemplate),
                    issues
            );
        }

        Integer times = parseInt(arguments.get("times"));
        if (times == null) {
            addActionIssue(path, "loop_times_numeric", actionId, issues);
        } else if (times <= 0) {
            addActionIssue(path, "loop_times_positive", actionId, issues);
        } else if (times > loopConfig.maxTimes()) {
            addActionIssue(path, "loop_times_exceeds_max", actionId, issues);
        }

        Long interval = parseTicks(arguments.get("interval"));
        if (interval == null) {
            addActionIssue(path, "loop_interval_invalid", actionId, issues);
        } else {
            long minInterval = "loopasync".equals(actionId)
                    ? loopConfig.minAsyncIntervalTicks()
                    : loopConfig.minSyncIntervalTicks();
            if (interval < minInterval) {
                addActionIssue(path, "loop_interval_below_min", actionId, issues);
            }
        }

        if (arguments.containsKey("initial_delay") && parseTicks(arguments.get("initial_delay")) == null) {
            addActionIssue(path, "loop_initial_delay_invalid", actionId, issues);
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
                    addMessageIssue(
                            path,
                            ConfigPrecheckSeverity.ERROR,
                            "loop_async_nested_template",
                            Map.of("template", referencedTemplate),
                            issues
                    );
                    continue;
                }
                Action nestedAction = context.actionRegistry() == null ? null : context.actionRegistry().get(nested.actionId());
                if (nestedAction == null || nestedAction.executionMode() != ActionExecutionMode.ASYNC_IO) {
                    addMessageIssue(
                            path,
                            ConfigPrecheckSeverity.ERROR,
                            "loop_async_unsafe_action",
                            Map.of("action", nested.actionId()),
                            issues
                    );
                }
            } catch (ActionSyntaxException exception) {
                addMessageIssue(
                        path,
                        ConfigPrecheckSeverity.ERROR,
                        "loop_async_invalid_syntax",
                        Map.of(
                                "line", index + 1,
                                "error", Texts.toStringSafe(exception.getMessage())
                        ),
                        issues
                );
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

    private void checkCancelLoopTemplate(String templateId,
            ParsedActionLine parsed,
            List<ConfigPrecheckIssue> issues) {
        Map<String, String> arguments = parsed.arguments();
        String path = "action.templates." + templateId;
        if (Texts.isBlank(arguments.get("key"))) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "cancel_loop_key_required", issues);
        }
        String match = Texts.lower(arguments.getOrDefault("match", "exact"));
        if (!"exact".equals(match) && !"prefix".equals(match)) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "cancel_loop_match_invalid", issues);
        }
        if (arguments.containsKey("silent") && ActionParsers.parseBoolean(arguments.get("silent")) == null) {
            addMessageIssue(path, ConfigPrecheckSeverity.ERROR, "cancel_loop_silent_invalid", issues);
        }
    }

    private void checkLoopFlags(String templateId,
            String actionId,
            Map<String, String> arguments,
            List<ConfigPrecheckIssue> issues) {
        String path = "action.templates." + templateId;
        if (arguments.containsKey("mode")) {
            String mode = Texts.lower(arguments.get("mode"));
            if (!"replace".equals(mode)
                    && !"refresh".equals(mode)
                    && !"ignore".equals(mode)
                    && !"allow_duplicate".equals(mode)) {
                addActionIssue(path, "loop_mode_invalid", actionId, issues);
            }
        }
        if (arguments.containsKey("stop_if_offline")
                && ActionParsers.parseBoolean(arguments.get("stop_if_offline")) == null) {
            addActionIssue(path, "loop_stop_if_offline_invalid", actionId, issues);
        }
        if (arguments.containsKey("stop_if_dead")
                && ActionParsers.parseBoolean(arguments.get("stop_if_dead")) == null) {
            addActionIssue(path, "loop_stop_if_dead_invalid", actionId, issues);
        }
        if (arguments.containsKey("stop_on_failure")
                && ActionParsers.parseBoolean(arguments.get("stop_on_failure")) == null) {
            addActionIssue(path, "loop_stop_on_failure_invalid", actionId, issues);
        }
    }

    private void addActionIssue(String path,
            String key,
            String actionId,
            List<ConfigPrecheckIssue> issues) {
        addMessageIssue(path, ConfigPrecheckSeverity.ERROR, key, Map.of("action", actionId), issues);
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
        var script = config.scriptConfig();
        var security = script.security();
        if (script.enabled() && !script.runtimeOptIn()) {
            addMessageIssue("script.runtime_opt_in", ConfigPrecheckSeverity.ERROR, "script_runtime_opt_in_required", issues);
        }
        if (security.allowActionDispatch() && security.maxActionDepth() > 5) {
            addMessageIssue("script.security.max_action_depth", ConfigPrecheckSeverity.WARN, "script_action_depth_large", issues);
        }
    }

    private void checkWebConsole(CoreLibConfig config, List<ConfigPrecheckIssue> issues) {
        if (config.webConsoleConfig() == null || config.webConsoleConfig().security() == null) {
            return;
        }
        var web = config.webConsoleConfig();
        if (web.enabled() && !web.runtimeOptIn()) {
            addMessageIssue("web_console.runtime_opt_in", ConfigPrecheckSeverity.ERROR, "web_runtime_opt_in_required", issues);
        }
        if (web.enabled() && !web.isLoopbackOnly()) {
            addMessageIssue("web_console.host", ConfigPrecheckSeverity.ERROR, "web_loopback_required", issues);
        }
        if (web.enabled() && !web.isReadOnly()) {
            addMessageIssue("web_console.security.mode", ConfigPrecheckSeverity.ERROR, "web_readonly_required", issues);
        }
        if (web.enabled() && web.hasUnsafeDefaultPassword()) {
            addMessageIssue("web_console.auth.password", ConfigPrecheckSeverity.ERROR, "web_password_unsafe", issues);
        }
        if (web.security().maxRequestBodyKb() <= 0) {
            addMessageIssue(
                    "web_console.security.max_request_body_kb",
                    ConfigPrecheckSeverity.ERROR,
                    "web_max_request_body_invalid",
                    issues
            );
        }
        if (web.enabled() && web.security().mode().configWriteAllowed() && web.security().allowedModules().isEmpty()) {
            addMessageIssue(
                    "web_console.security.allowed_modules",
                    ConfigPrecheckSeverity.WARN,
                    "web_allowed_modules_missing",
                    issues
            );
        }
    }
}

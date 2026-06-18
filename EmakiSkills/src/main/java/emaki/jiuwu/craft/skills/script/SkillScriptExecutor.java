package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.config.AppConfig;

public final class SkillScriptExecutor {

    private final SkillScriptActionRegistry registry;
    private final ActionLineParser lineParser = new ActionLineParser();

    public SkillScriptExecutor(SkillScriptActionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SkillActionResult> executePhase(SkillScriptContext context,
            SkillScriptDefinition script,
            SkillScriptPhase phase) {
        List<String> conditions = script.conditions(phase);
        if (!conditions.isEmpty() && !ConditionEvaluator.evaluate(conditions, "all_of", null,
                text -> resolveText(context, text), true)) {
            return CompletableFuture.completedFuture(SkillActionResult.skipped("Script phase condition did not pass."));
        }
        List<String> lines = script.lines(phase);
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(SkillActionResult.ok());
        }
        AppConfig.ScriptEngineSettings settings = context.plugin().appConfig().scriptEngine();
        int maxLines = Math.min(lines.size(), settings.maxLinesPerPhase());
        List<String> limited = new ArrayList<>(lines.subList(0, maxLines));
        CompletableFuture<SkillActionResult> future = CompletableFuture.completedFuture(SkillActionResult.ok());
        for (int index = 0; index < limited.size(); index++) {
            final int lineNumber = index + 1;
            final String line = limited.get(index);
            future = future.thenCompose(previous -> {
                if (!previous.success() && script.stopOnFailure()) {
                    return CompletableFuture.completedFuture(previous);
                }
                return executeLine(context, lineNumber, line);
            });
        }
        return future;
    }

    public CompletableFuture<SkillActionResult> executeLine(SkillScriptContext context, int lineNumber, String line) {
        ParsedActionLine parsed;
        try {
            parsed = lineParser.parse(lineNumber, resolveText(context, line));
        } catch (ActionSyntaxException exception) {
            return CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.SYNTAX_ERROR, exception.getMessage()));
        }
        if (parsed == null) {
            return CompletableFuture.completedFuture(SkillActionResult.ok());
        }
        String condition = resolveText(context, parsed.control().condition());
        if (Texts.isNotBlank(condition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(condition, value -> resolveText(context, value));
            if (passes == null) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                        "Invalid @if expression: " + condition));
            }
            if (!passes) {
                return CompletableFuture.completedFuture(SkillActionResult.skipped("Condition did not pass."));
            }
        }
        String chanceRaw = resolveText(context, parsed.control().chance());
        if (Texts.isNotBlank(chanceRaw)) {
            long threshold = ActionParsers.parseChanceThreshold(chanceRaw);
            if (threshold < 0L || threshold > ActionParsers.chanceDenominator()) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                        "Invalid @chance value: " + chanceRaw));
            }
            if (threshold <= 0L || ThreadLocalRandom.current().nextLong(ActionParsers.chanceDenominator()) >= threshold) {
                return CompletableFuture.completedFuture(SkillActionResult.skipped("Chance did not pass."));
            }
        }
        long delay = 0L;
        String delayRaw = resolveText(context, parsed.control().delay());
        if (Texts.isNotBlank(delayRaw)) {
            delay = ActionParsers.parseTicks(delayRaw);
            if (delay < 0L) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                        "Invalid @delay value: " + delayRaw));
            }
        }
        Map<String, String> arguments = resolveArguments(context, parsed.arguments());
        SkillScriptAction action = registry.get(parsed.actionId());
        CompletableFuture<SkillActionResult> result;
        if (action != null) {
            SkillActionResult validation = action.validate(arguments);
            result = validation.success()
                    ? action.execute(context, arguments)
                    : CompletableFuture.completedFuture(validation);
        } else {
            result = CompletableFuture.completedFuture(SkillActionResult.failure(SkillActionErrorType.ACTION_NOT_FOUND,
                    "Skill script action not found: " + parsed.actionId()));
        }
        if (delay <= 0L) {
            return result.thenApply(r -> afterLine(context, r));
        }
        CompletableFuture<SkillActionResult> delayed = new CompletableFuture<>();
        FoliaSchedulerAdapter.runEntityTaskLater(context.plugin(), context.caster(),
                () -> result.whenComplete((r, t) -> delayed.complete(t == null ? afterLine(context, r)
                        : SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION, t.getMessage()))), delay);
        return delayed;
    }

    private SkillActionResult afterLine(SkillScriptContext context, SkillActionResult result) {
        context.refreshTargetVariables();
        return result == null ? SkillActionResult.ok() : result;
    }

    private Map<String, String> resolveArguments(SkillScriptContext context, Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> resolved = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            resolved.put(entry.getKey(), resolveText(context, entry.getValue()));
        }
        return Map.copyOf(resolved);
    }

    private String resolveText(SkillScriptContext context, String text) {
        if (Texts.isBlank(text)) {
            return text;
        }
        String resolved = Texts.formatTemplate(text, context.variables());
        if (context.plugin().coreLib().placeholderRegistry() != null) {
            ActionContext actionContext = ActionContext.create(context.plugin(), context.caster(), "skill_script", false)
                    .withPlaceholders(context.variables());
            resolved = context.plugin().coreLib().placeholderRegistry().resolve(actionContext, resolved);
        }
        return resolved;
    }
}

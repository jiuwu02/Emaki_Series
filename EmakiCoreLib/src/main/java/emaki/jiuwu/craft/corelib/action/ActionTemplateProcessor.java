package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

final class ActionTemplateProcessor {

    private static final int MAX_TEMPLATE_DEPTH = 8;

    private final Plugin plugin;
    private final ActionTemplateRegistry templateRegistry;

    ActionTemplateProcessor(Plugin plugin, ActionTemplateRegistry templateRegistry) {
        this.plugin = plugin;
        this.templateRegistry = templateRegistry;
    }

    CompletableFuture<ActionResult> execute(ActionContext context,
                                            Map<String, String> arguments,
                                            TemplateExecutor executor) {
        String name = arguments.get("name");
        List<String> lines = templateRegistry.get(name);
        if (lines == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.TEMPLATE_NOT_FOUND, "Template not found: " + name));
        }
        int depth = resolveTemplateDepth(context);
        if (depth >= MAX_TEMPLATE_DEPTH) {
            return CompletableFuture.completedFuture(
                ActionResult.failure(ActionErrorType.INVALID_STATE, "Template expansion exceeded max depth of " + MAX_TEMPLATE_DEPTH + ".")
            );
        }
        Map<String, String> templateValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (Texts.lower(entry.getKey()).startsWith("with.")) {
                templateValues.put("template_" + Texts.lower(entry.getKey()).substring("with.".length()), entry.getValue());
            }
        }
        ActionContext nextContext = (context == null ? ActionContext.create(plugin, null, "template", false) : context)
            .withPlaceholders(templateValues)
            .withAttribute("action_template_depth", depth + 1);
        return executor.execute(nextContext, lines)
            .thenApply(batch -> batch.success()
                ? ActionResult.ok(Map.of("template", name))
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, firstFailureMessage(batch)));
    }

    private String firstFailureMessage(ActionBatchResult batch) {
        ActionStepResult failure = batch.firstFailure();
        return failure == null || failure.result() == null ? "Template execution failed." : failure.result().errorMessage();
    }

    private int resolveTemplateDepth(ActionContext context) {
        if (context == null) {
            return 0;
        }
        Object raw = context.attribute("action_template_depth");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return ActionParsers.parseInt(Texts.toStringSafe(raw), 0);
    }

    @FunctionalInterface
    interface TemplateExecutor {
        CompletableFuture<ActionBatchResult> execute(ActionContext context, List<String> lines);
    }
}

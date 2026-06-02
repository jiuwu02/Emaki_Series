package emaki.jiuwu.craft.gem.service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemActionCoordinator {

    public record ExecutionResult(boolean success, String message) {

        public static ExecutionResult ok() {
            return new ExecutionResult(true, "");
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(false, Texts.toStringSafe(message));
        }
    }

    private final EmakiGemPlugin plugin;
    private final Supplier<ActionExecutor> actionExecutorSupplier;

    public GemActionCoordinator(EmakiGemPlugin plugin, Supplier<ActionExecutor> actionExecutorSupplier) {
        this.plugin = plugin;
        this.actionExecutorSupplier = actionExecutorSupplier;
    }

    public ExecutionResult execute(Player player, String phase, List<String> actions, Map<String, ?> placeholders) {
        if (actions == null || actions.isEmpty()) {
            return ExecutionResult.ok();
        }
        ActionExecutor executor = actionExecutorSupplier == null ? null : actionExecutorSupplier.get();
        if (executor == null) {
            return ExecutionResult.failure("Action executor unavailable.");
        }
        ActionContext context = ActionContext.create(plugin, player, phase, false)
                .withPlaceholders(placeholders)
                .withAttribute("phase", phase);
        executor.executeAll(context, actions, true).whenComplete((result, throwable) -> {
            if (throwable != null) {
                warnActionFailure(phase, throwable.getMessage());
                return;
            }
            if (result != null && !result.success()) {
                warnActionFailure(phase, result.firstFailure() == null
                        ? "Unknown action failure."
                        : result.firstFailure().result().errorMessage());
            }
        });
        return ExecutionResult.ok();
    }

    private void warnActionFailure(String phase, String message) {
        if (plugin != null) {
            plugin.getLogger().warning("Gem action phase '" + Texts.toStringSafe(phase) + "' failed: " + Texts.toStringSafe(message));
        }
    }
}

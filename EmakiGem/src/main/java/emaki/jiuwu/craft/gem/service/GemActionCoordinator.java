package emaki.jiuwu.craft.gem.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.api.text.Texts;
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

    /** Reported when the pipeline engine has not finished starting up, so nothing can run yet. */
    private static final String RUNNER_UNAVAILABLE = "Action executor unavailable.";

    /** Reported for a failed batch: the pipeline runner answers with a verdict, not a per-line diagnosis. */
    private static final String UNKNOWN_FAILURE = "Unknown action failure.";

    private final EmakiGemPlugin plugin;
    private final ActionLineRunner actionLines;

    /**
     * Creates a coordinator.
     *
     * @param plugin the owning plugin, used for failure logging
     * @param actionLines the pipeline runner; safe to hold because it reads the live engine per call
     */
    public GemActionCoordinator(EmakiGemPlugin plugin, ActionLineRunner actionLines) {
        this.plugin = plugin;
        this.actionLines = actionLines;
    }

    /**
     * Starts a phase without waiting for it.
     *
     * @param player the acting player
     * @param phase phase name
     * @param actions configured pipeline lines
     * @param placeholders values readable as {@code %var.name%}
     * @return {@code ok} once the batch is started, or a failure when it could not be started at all
     */
    public ExecutionResult execute(Player player, String phase, List<String> actions, Map<String, ?> placeholders) {
        if (actions == null || actions.isEmpty()) {
            return ExecutionResult.ok();
        }
        if (!available()) {
            return ExecutionResult.failure(RUNNER_UNAVAILABLE);
        }
        executeAsync(actionLines, player, phase, actions, placeholders);
        return ExecutionResult.ok();
    }

    /**
     * Runs a phase and reports its verdict.
     *
     * @param player the acting player
     * @param phase phase name
     * @param actions configured pipeline lines
     * @param placeholders values readable as {@code %var.name%}
     * @return the batch verdict
     */
    public CompletionStage<ExecutionResult> executeAsync(Player player,
            String phase,
            List<String> actions,
            Map<String, ?> placeholders) {
        if (actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(ExecutionResult.ok());
        }
        if (!available()) {
            return CompletableFuture.completedFuture(ExecutionResult.failure(RUNNER_UNAVAILABLE));
        }
        return executeAsync(actionLines, player, phase, actions, placeholders);
    }

    private boolean available() {
        return actionLines != null && actionLines.available();
    }

    /**
     * Runs the batch and folds its outcome into an {@link ExecutionResult}.
     *
     * <p>The phase is no longer also written as a context attribute: a pipeline context carries the phase itself,
     * so stages read it from {@code context.phase()} instead.</p>
     */
    private CompletionStage<ExecutionResult> executeAsync(ActionLineRunner runner,
            Player player,
            String phase,
            List<String> actions,
            Map<String, ?> placeholders) {
        return runner.run(actions, player, phase, false, placeholders, true).handle((success, throwable) -> {
            if (throwable != null) {
                String message = Texts.toStringSafe(throwable.getMessage());
                warnActionFailure(phase, message);
                return ExecutionResult.failure(message);
            }
            if (!Boolean.TRUE.equals(success)) {
                warnActionFailure(phase, UNKNOWN_FAILURE);
                return ExecutionResult.failure(UNKNOWN_FAILURE);
            }
            return ExecutionResult.ok();
        });
    }

    private void warnActionFailure(String phase, String message) {
        if (plugin != null) {
            plugin.getLogger().warning("Gem action phase '" + Texts.toStringSafe(phase) + "' failed: " + Texts.toStringSafe(message));
        }
    }
}

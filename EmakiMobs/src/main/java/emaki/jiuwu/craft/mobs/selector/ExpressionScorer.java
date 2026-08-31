package emaki.jiuwu.craft.mobs.selector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;

final class ExpressionScorer {

    private static final String DEBUG_MODULE = "mobs";

    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    ExpressionScorer(Supplier<DebugLogger> debugLoggerSupplier) {
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    double score(Player player, String expressionId, String expression) {
        String prepared = PlaceholderRenderer.renderPapi(
                player, expression, null, "mob_target_selector");
        var result = ExpressionEngine.evaluateNumericDetailed(prepared);
        if (result.success() && Double.isFinite(result.value())) {
            return result.value();
        }
        reportFailure(player, expressionId, result.issues());
        return 0D;
    }

    void resetFailures() {
        reportedFailures.clear();
    }

    private void reportFailure(Player player, String expressionId, List<String> issues) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog(DEBUG_MODULE, player)
                || !reportedFailures.add(expressionId)) {
            return;
        }
        String error = issues == null || issues.isEmpty()
                ? "non-finite result"
                : String.join("; ", issues);
        debugLogger.log(DEBUG_MODULE, player, "target_selector_expression_failed", Map.of(
                "expression_id", expressionId,
                "error", error));
    }
}

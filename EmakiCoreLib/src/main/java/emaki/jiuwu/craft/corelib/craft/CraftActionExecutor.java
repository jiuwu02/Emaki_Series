package emaki.jiuwu.craft.corelib.craft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;

public final class CraftActionExecutor {

    private static final long TIMEOUT_SECONDS = 30L;

    private CraftActionExecutor() {
    }

    public static CompletableFuture<Boolean> run(
            @Nullable ActionLineRunner runner,
            @Nullable Player player,
            @Nullable String phase,
            @Nullable List<String> actions,
            @Nullable Map<String, ?> placeholders) {
        return run(runner, player, phase, actions, placeholders, true, null);
    }

    public static CompletableFuture<Boolean> run(
            @Nullable ActionLineRunner runner,
            @Nullable Player player,
            @Nullable String phase,
            @Nullable List<String> actions,
            @Nullable Map<String, ?> placeholders,
            boolean stopOnFailure,
            @Nullable BiConsumer<String, Throwable> errorLogger) {
        if (actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        if (runner == null || !runner.available()) {
            return CompletableFuture.completedFuture(false);
        }
        return runner.run(actions, player, phase, false, placeholders, stopOnFailure)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((success, throwable) -> {
                    if (errorLogger != null) {
                        if (throwable != null) {
                            errorLogger.accept(phase, throwable);
                        } else if (Boolean.FALSE.equals(success)) {
                            errorLogger.accept(phase, null);
                        }
                    }
                });
    }
}

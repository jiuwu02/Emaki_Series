package emaki.jiuwu.craft.corelib.craft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;

/**
 * 统一的 craft 阶段 action 执行器。封装 timeout、错误日志模板，供强化、锻造、宝石、技能、等级等模块复用。
 * <p>
 * 所有方法对 null 或空 actions 返回 {@code CompletableFuture.completedFuture(true)}，
 * 对 unavailable runner 返回 {@code CompletableFuture.completedFuture(false)}。
 */
public final class CraftActionExecutor {

    private static final long TIMEOUT_SECONDS = 30L;

    private CraftActionExecutor() {
    }

    /**
     * 执行 craft 阶段的 action 列表。默认 stopOnFailure=true，不记录错误日志。
     *
     * @param runner       ActionLineRunner 实例，null 时返回 false
     * @param player       玩家，可为 null
     * @param phase        阶段名称，可为 null
     * @param actions      action 行列表，null 或空时返回 true
     * @param placeholders 占位符变量，可为 null
     * @return CompletableFuture&lt;Boolean&gt;，true 表示全部成功
     */
    public static CompletableFuture<Boolean> run(
            @Nullable ActionLineRunner runner,
            @Nullable Player player,
            @Nullable String phase,
            @Nullable List<String> actions,
            @Nullable Map<String, ?> placeholders) {
        return run(runner, player, phase, actions, placeholders, true, null);
    }

    /**
     * 执行 craft 阶段的 action 列表。
     *
     * @param runner        ActionLineRunner 实例，null 时返回 false
     * @param player        玩家，可为 null
     * @param phase         阶段名称，可为 null
     * @param actions       action 行列表，null 或空时返回 true
     * @param placeholders  占位符变量，可为 null
     * @param stopOnFailure 遇到失败时是否停止，默认 true
     * @param errorLogger   错误日志记录器，接收 (phase, throwable)，可为 null。throwable 为 null 时表示 action 返回 false。
     * @return CompletableFuture&lt;Boolean&gt;，true 表示全部成功
     */
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

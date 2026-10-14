package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

/**
 * 管道逐步执行观测口。
 *
 * <p>默认空实现（{@link #NOOP}）不产生任何分配与副作用，可安全挂在热路径上；干跑或调试时注入
 * 具体实现即可逐段回显 source/gate 的输入输出与 action 的参数，用于排查复杂管道的决策过程。</p>
 */
public interface StepReporter {

    StepReporter NOOP = new StepReporter() {
    };

    default void source(@Nullable String stageId, int selected, @Nullable String reasonKey) {
    }

    default void gate(@Nullable String stageId, int inbound, int outbound, @Nullable String reasonKey) {
    }

    default void action(@Nullable String stageId,
            @Nullable CoreStageKind kind,
            @Nullable Map<String, String> arguments,
            int success,
            int failure,
            int skipped,
            @Nullable String reasonKey) {
    }

    default void control(@Nullable String keyword, @Nullable String detail) {
    }

    default void retry(@Nullable String stageId, int attempt, int maxAttempts, @Nullable String reasonKey) {
    }

    default void onError(@Nullable String stageId, @Nullable String failureKind, @Nullable String reasonKey) {
    }
}

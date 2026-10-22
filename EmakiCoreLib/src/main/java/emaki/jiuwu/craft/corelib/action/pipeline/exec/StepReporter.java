package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

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

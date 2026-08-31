package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

public record StageRegistrationResult(boolean accepted,
        @NotNull String stageId,
        @NotNull CoreStageKind kind,
        @NotNull String reasonKey,
        @NotNull Map<String, Object> args) {

    public StageRegistrationResult {
        stageId = stageId == null ? "" : stageId;
        kind = kind == null ? CoreStageKind.ACTION : kind;
        reasonKey = reasonKey == null ? "" : reasonKey;
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    public static @NotNull StageRegistrationResult accepted(@NotNull String stageId, @NotNull CoreStageKind kind) {
        return new StageRegistrationResult(true, stageId, kind, "", Map.of());
    }

    public static @NotNull StageRegistrationResult rejected(@Nullable String stageId,
            @Nullable CoreStageKind kind,
            @NotNull String reasonKey,
            @Nullable Map<String, Object> args) {
        return new StageRegistrationResult(false, stageId == null ? "" : stageId, kind, reasonKey,
                args == null ? Map.of() : args);
    }
}

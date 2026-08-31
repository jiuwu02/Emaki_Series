package emaki.jiuwu.craft.strengthen.enhancement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService.ExecutionPlan;

public record EnhancementPreviewSession(@NotNull EnhancementAttemptPreview preview,
        @Nullable EnhancementTargetVariables.Snapshot snapshot,
        @Nullable ExecutionPlan plan) {

    public EnhancementPreviewSession {
        preview = preview == null ? EnhancementAttemptPreview.rejected("strengthen.error.no_target") : preview;
    }

    public EnhancementPreviewSession(@NotNull EnhancementAttemptPreview preview,
            @Nullable EnhancementTargetVariables.Snapshot snapshot) {
        this(preview, snapshot, null);
    }

    public static @NotNull EnhancementPreviewSession rejected(@NotNull String errorKey) {
        return new EnhancementPreviewSession(EnhancementAttemptPreview.rejected(errorKey), null, null);
    }

    public boolean valid() {
        return preview.valid();
    }
}

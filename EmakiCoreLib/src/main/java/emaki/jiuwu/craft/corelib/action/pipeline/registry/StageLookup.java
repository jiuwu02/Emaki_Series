package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

public sealed interface StageLookup {

    record Found(@NotNull RegisteredStage entry) implements StageLookup {}

    record Unknown(@NotNull String id, @NotNull CoreStageKind kind) implements StageLookup {}

    record OwnerDisabled(@NotNull String id,
            @NotNull CoreStageKind kind,
            @NotNull String ownerName) implements StageLookup {}

    default @Nullable RegisteredStage entryOrNull() {
        return this instanceof Found found ? found.entry() : null;
    }
}

package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

public record RegisteredStage(@NotNull String id,
        @NotNull CoreStageKind kind,
        @NotNull Object stage,
        @Nullable Plugin owner,
        @NotNull String ownerName,
        long generation) {

    public RegisteredStage {
        id = id == null ? "" : id;
        kind = kind == null ? CoreStageKind.ACTION : kind;
        ownerName = ownerName == null ? "" : ownerName;
    }

    public boolean ownerEnabled() {
        return owner != null && owner.isEnabled();
    }
}

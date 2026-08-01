package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;

/**
 * One live stage entry.
 *
 * @param id normalised stage id
 * @param kind which table it belongs to
 * @param stage the implementation, one of the three SPI types
 * @param owner the plugin that registered it
 * @param ownerName owner name, retained for tombstone diagnostics after the plugin unloads
 * @param generation monotonic sequence used for compare-and-revoke
 */
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

    /** {@return whether the owning plugin is still enabled} */
    public boolean ownerEnabled() {
        return owner != null && owner.isEnabled();
    }
}

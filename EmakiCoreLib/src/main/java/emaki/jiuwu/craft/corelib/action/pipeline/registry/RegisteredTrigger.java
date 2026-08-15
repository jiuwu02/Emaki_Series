package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;

public record RegisteredTrigger(@NotNull String id,
        @NotNull CoreActionTrigger trigger,
        @NotNull TriggerContract contract,
        @Nullable Plugin owner,
        @NotNull String ownerName,
        long generation) {

    public RegisteredTrigger {
        id = id == null ? "" : id;
        ownerName = ownerName == null ? "" : ownerName;
    }

    public boolean ownerEnabled() {
        return owner != null && owner.isEnabled();
    }
}

package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;

/**
 * One live trigger entry.
 *
 * <p>The contract is captured at registration time rather than read from {@link #trigger()} on every
 * lookup, because the compile path consults it per pipeline compilation and a third-party
 * {@code contract()} implementation is free to be expensive or non-deterministic.</p>
 *
 * @param id normalised trigger id
 * @param trigger the declaring implementation
 * @param contract the contract captured when the trigger was registered
 * @param owner the plugin that registered it
 * @param ownerName owner name, retained for duplicate-id diagnostics
 * @param generation monotonic sequence used for compare-and-revoke
 */
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

    /** {@return whether the owning plugin is still enabled} */
    public boolean ownerEnabled() {
        return owner != null && owner.isEnabled();
    }
}

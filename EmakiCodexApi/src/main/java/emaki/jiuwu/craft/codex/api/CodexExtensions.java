package emaki.jiuwu.craft.codex.api;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Owner-scoped extension points for external advancements and trigger providers. */
@ApiStatus.NonExtendable
public interface CodexExtensions {

    /**
     * Registers one advancement in the owner's namespace. Must run on the server global thread.
     * The owner is removed automatically when disabled.
     */
    @NotNull AdvancementRegistration registerAdvancement(
            @Nullable Plugin owner, @Nullable AdvancementSpec spec);

    /** Registers a trigger provider, automatically removed when its owner is disabled. */
    @NotNull AdvancementTriggerRegistration registerTrigger(
            @Nullable Plugin owner, @Nullable AdvancementTrigger trigger);
}

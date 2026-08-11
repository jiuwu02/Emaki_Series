package emaki.jiuwu.craft.codex.api;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;

/** External provider that maps gameplay trigger contexts to advancement ids to grant. */
public interface AdvancementTrigger {

    /** Stable id unique within the owning plugin. */
    @NotNull String id();

    /** Lower values are evaluated first. */
    default int priority() { return 100; }

    /**
     * Returns advancement ids to grant for one gameplay trigger.
     *
     * <p>Called synchronously on the context player's owner thread. Implementations must be fast and must
     * not return {@code null}. Duplicate ids are collapsed before mutation.
     */
    @NotNull Collection<String> advancements(@NotNull AdvancementTriggerContext context);
}

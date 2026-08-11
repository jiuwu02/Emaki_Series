package emaki.jiuwu.craft.gem.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a gem resonance that a set of inlaid gems currently satisfies.
 *
 * <p>Resonance effect scripts are configuration internals and are not exposed; what a third party can
 * act on is which resonance is active and how it is identified.
 *
 * @param id             canonical lowercase resonance id
 * @param displayName    display name; falls back to the id when unset
 * @param priority       resolution priority; higher wins when resonances compete
 * @param exclusiveGroup group in which only one resonance may be active; empty when unrestricted
 * @param pattern        the chain positions, each matched by gem id, gem type, or wildcard
 * @param ordered        whether the chain must appear in the declared slot order
 */
public record GemResonanceView(@NotNull String id,
                               @NotNull String displayName,
                               int priority,
                               @NotNull String exclusiveGroup,
                               @NotNull List<GemResonanceSlotView> pattern,
                               boolean ordered) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id             canonical lowercase resonance id
     * @param displayName    display name
     * @param priority       resolution priority
     * @param exclusiveGroup exclusivity group
     * @param pattern        resonance chain gem ids
     * @param ordered        whether order matters
     */
    public GemResonanceView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        priority = Math.max(0, priority);
        exclusiveGroup = exclusiveGroup == null ? "" : exclusiveGroup;
        pattern = pattern == null ? List.of() : List.copyOf(pattern);
    }
}

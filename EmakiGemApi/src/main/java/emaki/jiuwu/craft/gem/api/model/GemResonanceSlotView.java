package emaki.jiuwu.craft.gem.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * One position in a gem resonance chain.
 *
 * <p>A position may be matched by gem id, by gem type, or by neither — an entry with both fields empty
 * is a wildcard that any gem satisfies.
 *
 * @param gemId    the gem id this position requires; empty when matched by type or wildcard
 * @param gemType  the gem type this position requires; empty when matched by id or wildcard
 * @param minLevel the minimum gem level this position requires; {@code 0} when unrestricted
 */
public record GemResonanceSlotView(@NotNull String gemId,
                                   @NotNull String gemType,
                                   int minLevel) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param gemId    required gem id
     * @param gemType  required gem type
     * @param minLevel minimum gem level
     */
    public GemResonanceSlotView {
        gemId = gemId == null ? "" : gemId;
        gemType = gemType == null ? "" : gemType;
        minLevel = Math.max(0, minLevel);
    }

    /** {@return whether this position accepts any gem} */
    public boolean wildcard() {
        return gemId.isEmpty() && gemType.isEmpty();
    }
}

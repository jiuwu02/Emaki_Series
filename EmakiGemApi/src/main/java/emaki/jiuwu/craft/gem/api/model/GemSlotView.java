package emaki.jiuwu.craft.gem.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of one socket slot on a piece of equipment.
 *
 * @param index       zero-based slot index
 * @param socketType  the socket type this slot accepts; empty when it accepts anything
 * @param displayName the slot's display name; falls back to the socket type when unset
 * @param opened      whether the slot has been opened and can hold a gem
 * @param gemId       the id of the gem currently inlaid, or {@code null} when the slot is empty
 * @param gemLevel    the level of the inlaid gem; {@code 0} when the slot is empty
 */
public record GemSlotView(int index,
                          @NotNull String socketType,
                          @NotNull String displayName,
                          boolean opened,
                          @Nullable String gemId,
                          int gemLevel) {

    /**
     * Normalises every reference component so no accessor except {@code gemId} can return
     * {@code null}.
     *
     * @param index       zero-based slot index
     * @param socketType  accepted socket type
     * @param displayName slot display name
     * @param opened      whether the slot is opened
     * @param gemId       inlaid gem id, or {@code null}
     * @param gemLevel    inlaid gem level
     */
    public GemSlotView {
        socketType = socketType == null ? "" : socketType;
        displayName = displayName == null || displayName.isBlank() ? socketType : displayName;
        gemId = gemId == null || gemId.isBlank() ? null : gemId;
        gemLevel = gemId == null ? 0 : Math.max(1, gemLevel);
    }

    /** {@return whether a gem is currently inlaid in this slot} */
    public boolean occupied() {
        return gemId != null;
    }

    /** {@return whether this slot is opened and free to receive a gem} */
    public boolean availableForInlay() {
        return opened && gemId == null;
    }
}

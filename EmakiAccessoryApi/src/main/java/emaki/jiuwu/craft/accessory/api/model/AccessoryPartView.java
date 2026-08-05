package emaki.jiuwu.craft.accessory.api.model;

import java.util.List;

/**
 * Immutable view of one configured accessory part.
 *
 * <p>A part is what a server owner declares in {@code parts.yml}: a free-form id plus how many slots
 * it provides. A part with {@code count} 2 always expands into two slot instances, and single-slot
 * parts expand the same way rather than being special-cased, so item declarations and player data
 * only ever have to deal with one id shape.
 *
 * @param partId          normalized part id, matching {@code ^[a-z0-9_]+$}
 * @param count           number of slot instances this part provides; always at least 1
 * @param displayName     MiniMessage display name, may be empty
 * @param slotInstanceIds expanded slot instance ids in index order, each {@code <partId>_<index>}
 *                        with index starting at 1
 */
public record AccessoryPartView(String partId,
        int count,
        String displayName,
        List<String> slotInstanceIds) {

    /** Canonical constructor; defends the id list against later mutation. */
    public AccessoryPartView {
        partId = partId == null ? "" : partId;
        displayName = displayName == null ? "" : displayName;
        slotInstanceIds = slotInstanceIds == null ? List.of() : List.copyOf(slotInstanceIds);
        count = Math.max(1, count);
    }
}

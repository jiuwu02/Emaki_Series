package emaki.jiuwu.craft.accessory.model;

/**
 * One expanded accessory slot instance.
 *
 * <p>This is the unit everything downstream keys on: player data, the GUI grid, the {@code active_slot}
 * gate, and the {@code sourceSlot} reported to EmakiSkills. Part-level ids only appear in configuration
 * and in the widening step of slot matching.
 *
 * @param slotInstanceId the {@code <partId>_<index>} id
 * @param partId         the owning part id
 * @param index          one-based index within the part
 * @param displayName    inherited part display name; may be empty
 * @param icon           inherited part placeholder icon; may be empty
 */
public record AccessorySlot(String slotInstanceId,
        String partId,
        int index,
        String displayName,
        String icon) {

    /**
     * Builds a slot instance from its owning part.
     *
     * @param part  the owning part
     * @param index one-based index within the part
     * @return the slot instance
     */
    public static AccessorySlot of(AccessoryPart part, int index) {
        return new AccessorySlot(
                AccessoryPart.slotInstanceId(part.partId(), index),
                part.partId(),
                index,
                part.displayName(),
                part.icon()
        );
    }
}

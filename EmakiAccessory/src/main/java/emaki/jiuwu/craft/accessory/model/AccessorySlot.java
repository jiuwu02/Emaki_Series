package emaki.jiuwu.craft.accessory.model;

public record AccessorySlot(String slotInstanceId,
        String partId,
        int index,
        String displayName,
        String icon) {

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

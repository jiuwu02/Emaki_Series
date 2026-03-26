package emaki.jiuwu.craft.corelib.item;

public enum ItemSourceType {
    VANILLA,
    CRAFTENGINE,
    NEIGEITEMS;

    public static ItemSourceType fromText(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "craftengine", "ce" -> CRAFTENGINE;
            case "neigeitems", "ni" -> NEIGEITEMS;
            case "vanilla", "minecraft", "v" -> VANILLA;
            default -> null;
        };
    }
}

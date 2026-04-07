package emaki.jiuwu.craft.corelib.item;

public enum ItemSourceType {
    VANILLA,
    CRAFTENGINE,
    ITEMSADDER,
    NEIGEITEMS,
    MMOITEMS,
    NEXO;

    public String displayName() {
        return switch (this) {
            case VANILLA ->
                "Vanilla";
            case CRAFTENGINE ->
                "CraftEngine";
            case ITEMSADDER ->
                "ItemsAdder";
            case NEIGEITEMS ->
                "NeigeItems";
            case MMOITEMS ->
                "MMOItems";
            case NEXO ->
                "Nexo";
        };
    }

    public static ItemSourceType fromText(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "craftengine", "ce" ->
                CRAFTENGINE;
            case "itemsadder", "ia" ->
                ITEMSADDER;
            case "neigeitems", "ni" ->
                NEIGEITEMS;
            case "mmoitems", "mi" ->
                MMOITEMS;
            case "nexo", "no" ->
                NEXO;
            case "vanilla", "minecraft", "v" ->
                VANILLA;
            default ->
                null;
        };
    }
}

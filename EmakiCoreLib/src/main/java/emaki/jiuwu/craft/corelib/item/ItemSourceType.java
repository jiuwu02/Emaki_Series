package emaki.jiuwu.craft.corelib.item;

public enum ItemSourceType {
    VANILLA,
    CRAFTENGINE,
    ITEMSADDER,
    NEIGEITEMS,
    MMOITEMS,
    EMAKIITEM,
    NEXO,
    ORAXEN,
    ECOITEMS;

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
            case EMAKIITEM ->
                "EmakiItem";
            case NEXO ->
                "Nexo";
            case ORAXEN ->
                "Oraxen";
            case ECOITEMS ->
                "EcoItems";
        };
    }

    public static ItemSourceType fromText(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "craftengine", "ce" ->
                CRAFTENGINE;
            case "itemsadder", "ia" ->
                ITEMSADDER;
            case "neigeitems", "ni" ->
                NEIGEITEMS;
            case "mmoitems", "mi" ->
                MMOITEMS;
            case "emakiitem", "ei" ->
                EMAKIITEM;
            case "nexo", "no" ->
                NEXO;
            case "oraxen", "ox" ->
                ORAXEN;
            case "ecoitems", "eco", "eci" ->
                ECOITEMS;
            case "vanilla", "minecraft", "v" ->
                VANILLA;
            default ->
                null;
        };
    }
}

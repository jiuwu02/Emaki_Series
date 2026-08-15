package emaki.jiuwu.craft.storage.model;

import java.util.Locale;

public enum SortMode {

    MATERIAL_ASC("material_asc", Dimension.MATERIAL, true),
    MATERIAL_DESC("material_desc", Dimension.MATERIAL, false),
    NAME_ASC("name_asc", Dimension.NAME, true),
    NAME_DESC("name_desc", Dimension.NAME, false),
    AMOUNT_ASC("amount_asc", Dimension.AMOUNT, true),
    AMOUNT_DESC("amount_desc", Dimension.AMOUNT, false);

    public enum Dimension {
        MATERIAL,
        NAME,
        AMOUNT
    }

    private final String id;
    private final Dimension dimension;
    private final boolean ascending;

    SortMode(String id, Dimension dimension, boolean ascending) {
        this.id = id;
        this.dimension = dimension;
        this.ascending = ascending;
    }

    public String id() {
        return id;
    }

    public Dimension dimension() {
        return dimension;
    }

    public boolean ascending() {
        return ascending;
    }

    public static SortMode fromId(String raw, SortMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (SortMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return fallback;
    }

    public SortMode reversed() {
        for (SortMode mode : values()) {
            if (mode.dimension == dimension && mode.ascending != ascending) {
                return mode;
            }
        }
        return this;
    }

    public SortMode nextDimension() {
        Dimension target = switch (dimension) {
            case MATERIAL -> Dimension.NAME;
            case NAME -> Dimension.AMOUNT;
            case AMOUNT -> Dimension.MATERIAL;
        };
        for (SortMode mode : values()) {
            if (mode.dimension == target && mode.ascending == ascending) {
                return mode;
            }
        }
        return this;
    }
}

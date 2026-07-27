package emaki.jiuwu.craft.storage.model;

import java.util.Locale;

/**
 * The six supported entry orderings.
 *
 * <p>Sorting is an explicit "tidy up" action that rewrites {@link PlayerStorage#entryOrder()};
 * it never reorders entries during rendering, so slot positions stay stable between clicks.
 */
public enum SortMode {

    MATERIAL_ASC("material_asc", Dimension.MATERIAL, true),
    MATERIAL_DESC("material_desc", Dimension.MATERIAL, false),
    NAME_ASC("name_asc", Dimension.NAME, true),
    NAME_DESC("name_desc", Dimension.NAME, false),
    AMOUNT_ASC("amount_asc", Dimension.AMOUNT, true),
    AMOUNT_DESC("amount_desc", Dimension.AMOUNT, false);

    /** The comparison dimension, independent of direction. */
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

    /** {@return the stable lower-case id used in {@code meta.yml} and {@code config.yml}} */
    public String id() {
        return id;
    }

    public Dimension dimension() {
        return dimension;
    }

    public boolean ascending() {
        return ascending;
    }

    /**
     * Resolves a configured id.
     *
     * @param raw      the configured value, normalised with {@link Locale#ROOT}
     * @param fallback returned when {@code raw} is blank or unknown
     * @return the resolved mode, never {@code null} unless {@code fallback} is
     */
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

    /** {@return the same dimension with the direction flipped} */
    public SortMode reversed() {
        for (SortMode mode : values()) {
            if (mode.dimension == dimension && mode.ascending != ascending) {
                return mode;
            }
        }
        return this;
    }

    /** {@return the next dimension keeping the current direction, cycling MATERIAL to NAME to AMOUNT} */
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

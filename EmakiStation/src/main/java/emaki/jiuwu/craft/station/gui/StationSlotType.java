package emaki.jiuwu.craft.station.gui;

import java.util.Locale;
import java.util.Set;

/**
 * The slot types a station layout may declare.
 *
 * <p>CoreLib's template parser only auto-infers a handful of Forge-specific slot keys, so every station
 * slot must state its {@code type:} explicitly. These constants are the accepted values.
 */
public final class StationSlotType {

    /** Slots the player places materials into. */
    public static final String INPUT = "input";

    /** Slot showing the currently matched recipe. */
    public static final String RECIPE_PREVIEW = "recipe_preview";

    /** Slots listing the matched recipe's material requirements. */
    public static final String MATERIAL_LIST = "material_list";

    /** Slot toggling between the inventory and warehouse channels. */
    public static final String CHANNEL_TOGGLE = "channel_toggle";

    /** Slot cycling the batch multiplier. */
    public static final String BATCH_MULTIPLIER = "batch_multiplier";

    /** Slot showing how many batches the current materials support. */
    public static final String MAX_CRAFTABLE = "max_craftable";

    /** Slot toggling where finished outputs are delivered. */
    public static final String OUTPUT_TOGGLE = "output_toggle";

    /** Slots listing queue entries. */
    public static final String QUEUE_VIEW = "queue_view";

    /** Slot submitting the current selection. */
    public static final String CONFIRM = "confirm";

    /** Slot paging the material list backwards. */
    public static final String PREV_PAGE = "prev_page";

    /** Slot paging the material list forwards. */
    public static final String NEXT_PAGE = "next_page";

    /** Every recognised interactive type. */
    private static final Set<String> KNOWN = Set.of(INPUT, RECIPE_PREVIEW, MATERIAL_LIST,
            CHANNEL_TOGGLE, BATCH_MULTIPLIER, MAX_CRAFTABLE, OUTPUT_TOGGLE, QUEUE_VIEW,
            CONFIRM, PREV_PAGE, NEXT_PAGE);

    private StationSlotType() {
    }

    /**
     * Normalises a raw type token.
     *
     * @param raw the configured token
     * @return the lower-cased token, or an empty string when absent
     */
    public static String normalize(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Tests whether a token names a recognised interactive type.
     *
     * @param raw the configured token
     * @return whether EmakiStation knows how to render and handle it
     */
    public static boolean known(String raw) {
        return KNOWN.contains(normalize(raw));
    }

    /** {@return every recognised interactive type} */
    public static Set<String> known() {
        return KNOWN;
    }
}

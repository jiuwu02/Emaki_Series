package emaki.jiuwu.craft.station.gui;

import java.util.Locale;
import java.util.Set;

/**
 * The slot types a station layout may declare.
 *
 * <p>CoreLib's template parser treats {@code type} as a free-form string: there is no registry and no
 * validation, and {@code GuiTemplate.slotsByType} only groups by string equality. Every station slot must
 * therefore state its {@code type:} explicitly, and these constants are the accepted values.
 *
 * <p>Types are grouped by the page that understands them. A type declared on the wrong page is reported by
 * {@link StationLayoutValidator} rather than silently ignored, because a mis-placed slot looks like a
 * decorative pane at runtime and gives an administrator no clue why their button does nothing.
 */
public final class StationSlotType {

    // --- Catalog page -------------------------------------------------------

    /** Slots listing the station's recipes, one recipe per slot. */
    public static final String RECIPE_LIST = "recipe_list";

    /** Slot opening the craft-queue page. */
    public static final String QUEUE_OPEN = "queue_open";

    // --- Preview page -------------------------------------------------------

    /** Slots listing the selected recipe's material requirements. */
    public static final String MATERIAL_LIST = "material_list";

    /** Slot showing which recipe is being previewed. */
    public static final String RECIPE_DISPLAY = "recipe_display";

    /** Slot showing the currency charged and the viewer's balance. */
    public static final String COST_DISPLAY = "cost_display";

    /** Slot showing how many batches the viewer's materials support. */
    public static final String MAX_CRAFTABLE = "max_craftable";

    /** Slot submitting the previewed recipe. */
    public static final String CONFIRM = "confirm";

    // --- Queue page ---------------------------------------------------------

    /** Slots listing queue entries. */
    public static final String QUEUE_VIEW = "queue_view";

    /** Slot claiming every deliverable pending output. */
    public static final String CLAIM_ALL = "claim_all";

    /** Slot buying additional queue length. */
    public static final String QUEUE_PURCHASE = "queue_purchase";

    /** Slot showing used/total queue length and where the ceiling comes from. */
    public static final String CAPACITY_DISPLAY = "capacity_display";

    // --- Shared -------------------------------------------------------------

    /** Slot cycling the batch multiplier; shared by the catalog and preview pages. */
    public static final String BATCH_MULTIPLIER = "batch_multiplier";

    /** Slot toggling where finished outputs are delivered. */
    public static final String OUTPUT_TOGGLE = "output_toggle";

    /** Slot paging the current list backwards. */
    public static final String PREV_PAGE = "prev_page";

    /** Slot paging the current list forwards. */
    public static final String NEXT_PAGE = "next_page";

    /** Slot showing the current page number. */
    public static final String PAGE_INFO = "page_info";

    /** Slot returning to the catalog page, preserving its page number. */
    public static final String BACK = "back";

    /** Slot closing the window. */
    public static final String CLOSE = "close";

    /** Types every page accepts. */
    private static final Set<String> SHARED = Set.of(BATCH_MULTIPLIER, OUTPUT_TOGGLE,
            PREV_PAGE, NEXT_PAGE, PAGE_INFO, BACK, CLOSE);

    /** Types the catalog page accepts on top of {@link #SHARED}. */
    private static final Set<String> CATALOG_ONLY = Set.of(RECIPE_LIST, QUEUE_OPEN);

    /** Types the preview page accepts on top of {@link #SHARED}. */
    private static final Set<String> PREVIEW_ONLY = Set.of(MATERIAL_LIST, RECIPE_DISPLAY,
            COST_DISPLAY, MAX_CRAFTABLE, CONFIRM);

    /** Types the queue page accepts on top of {@link #SHARED}. */
    private static final Set<String> QUEUE_ONLY = Set.of(QUEUE_VIEW, CLAIM_ALL,
            QUEUE_PURCHASE, CAPACITY_DISPLAY);

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
     * Tests whether a token names a type EmakiStation understands on any page.
     *
     * @param raw the configured token
     * @return whether the type is recognised somewhere
     */
    public static boolean known(String raw) {
        String type = normalize(raw);
        return SHARED.contains(type)
                || CATALOG_ONLY.contains(type)
                || PREVIEW_ONLY.contains(type)
                || QUEUE_ONLY.contains(type);
    }

    /** {@return the types the catalog page accepts} */
    public static Set<String> catalogTypes() {
        return union(CATALOG_ONLY);
    }

    /** {@return the types the preview page accepts} */
    public static Set<String> previewTypes() {
        return union(PREVIEW_ONLY);
    }

    /** {@return the types the queue page accepts} */
    public static Set<String> queueTypes() {
        return union(QUEUE_ONLY);
    }

    private static Set<String> union(Set<String> pageTypes) {
        Set<String> combined = new java.util.LinkedHashSet<>(pageTypes);
        combined.addAll(SHARED);
        return Set.copyOf(combined);
    }
}

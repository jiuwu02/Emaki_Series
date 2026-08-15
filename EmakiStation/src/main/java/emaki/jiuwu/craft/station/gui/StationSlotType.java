package emaki.jiuwu.craft.station.gui;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class StationSlotType {

    public static final String RECIPE_LIST = "recipe_list";

    public static final String QUEUE_OPEN = "queue_open";

    public static final String MATERIAL_LIST = "material_list";

    public static final String RECIPE_DISPLAY = "recipe_display";

    public static final String COST_DISPLAY = "cost_display";

    public static final String MAX_CRAFTABLE = "max_craftable";

    public static final String CONFIRM = "confirm";

    public static final String DISMANTLE_INPUT = "dismantle_input";

    public static final String DISMANTLE_CONFIRM = "dismantle_confirm";

    public static final String DISMANTLE_OUTPUT_LIST = "dismantle_output_list";

    public static final String DISMANTLE_ITEM_DISPLAY = "dismantle_item_display";

    public static final String DISMANTLE_ROLLS_DISPLAY = "dismantle_rolls_display";

    public static final String QUEUE_VIEW = "queue_view";

    public static final String CLAIM_ALL = "claim_all";

    public static final String QUEUE_PURCHASE = "queue_purchase";

    public static final String CAPACITY_DISPLAY = "capacity_display";

    public static final String BATCH_MULTIPLIER = "batch_multiplier";

    public static final String OUTPUT_TOGGLE = "output_toggle";

    public static final String PREV_PAGE = "prev_page";

    public static final String NEXT_PAGE = "next_page";

    public static final String PAGE_INFO = "page_info";

    public static final String BACK = "back";

    public static final String CLOSE = "close";

    private static final Set<String> SHARED = Set.of(BATCH_MULTIPLIER, OUTPUT_TOGGLE,
            PREV_PAGE, NEXT_PAGE, PAGE_INFO, BACK, CLOSE);

    private static final Set<String> CATALOG_ONLY = Set.of(RECIPE_LIST, QUEUE_OPEN);

    private static final Set<String> PREVIEW_ONLY = Set.of(MATERIAL_LIST, RECIPE_DISPLAY,
            COST_DISPLAY, MAX_CRAFTABLE, CONFIRM);

    private static final Set<String> QUEUE_ONLY = Set.of(QUEUE_VIEW, CLAIM_ALL,
            QUEUE_PURCHASE, CAPACITY_DISPLAY);

    private static final Set<String> DISMANTLE_ONLY = Set.of(DISMANTLE_INPUT, DISMANTLE_CONFIRM,
            DISMANTLE_OUTPUT_LIST, DISMANTLE_ITEM_DISPLAY, DISMANTLE_ROLLS_DISPLAY);

    private StationSlotType() {
    }

    public static String normalize(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean known(String raw) {
        String type = normalize(raw);
        return SHARED.contains(type)
                || CATALOG_ONLY.contains(type)
                || PREVIEW_ONLY.contains(type)
                || QUEUE_ONLY.contains(type)
                || DISMANTLE_ONLY.contains(type);
    }

    public static Set<String> catalogTypes() {
        return union(CATALOG_ONLY);
    }

    public static Set<String> previewTypes() {
        return union(PREVIEW_ONLY);
    }

    public static Set<String> queueTypes() {
        return union(QUEUE_ONLY);
    }

    public static Set<String> dismantleTypes() {
        return union(DISMANTLE_ONLY);
    }

    private static Set<String> union(Set<String> pageTypes) {
        Set<String> combined = new LinkedHashSet<>(pageTypes);
        combined.addAll(SHARED);
        return Set.copyOf(combined);
    }
}

package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.gui.SlotParser;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

/**
 * Startup validation for station layouts.
 *
 * <p>Runs during the configuration precheck stage. A layout that fails validation is reported and the
 * previous valid runtime configuration is kept, because replacing a working station set with a broken one
 * is worse than refusing the change.
 *
 * <p>Each of the three pages is validated against its own accepted type set. A slot type that exists but
 * belongs to another page is reported as {@code wrong_page_type} rather than {@code unknown_type}, because
 * the fix is different: one is a typo, the other is a slot in the wrong file.
 */
public final class StationLayoutValidator {

    /** The page a layout is being validated as. */
    public enum Page {

        /** The recipe catalog. */
        CATALOG(StationSlotType.RECIPE_LIST, "missing_recipe_list"),

        /** The material preview. */
        PREVIEW(StationSlotType.MATERIAL_LIST, "missing_material_list"),

        /** The craft queue. */
        QUEUE(StationSlotType.QUEUE_VIEW, "missing_queue_view"),

        /** The dismantle page. */
        DISMANTLE(StationSlotType.DISMANTLE_CONFIRM, "missing_dismantle_confirm");

        private final String requiredType;
        private final String missingCode;

        Page(String requiredType, String missingCode) {
            this.requiredType = requiredType;
            this.missingCode = missingCode;
        }

        /** {@return the slot type this page cannot work without} */
        public String requiredType() {
            return requiredType;
        }

        /** {@return the diagnostic code emitted when the required type is absent} */
        public String missingCode() {
            return missingCode;
        }

        /** {@return every slot type this page accepts} */
        public Set<String> acceptedTypes() {
            return switch (this) {
                case CATALOG -> StationSlotType.catalogTypes();
                case PREVIEW -> StationSlotType.previewTypes();
                case QUEUE -> StationSlotType.queueTypes();
                case DISMANTLE -> StationSlotType.dismantleTypes();
            };
        }
    }

    /**
     * One problem found in one layout.
     *
     * @param layoutId the layout the problem belongs to
     * @param code     a stable machine-readable problem code
     * @param detail   the offending value or slot
     */
    public record LayoutIssue(String layoutId, String code, String detail) {
    }

    private StationLayoutValidator() {
    }

    /**
     * Validates one layout as a given page.
     *
     * <p>Checks, in order: the row count is inside the chest range, every declared slot resolves to a
     * position that exists in that many rows, no two typed groups claim the same slot, every declared type
     * is one this page can render, and the page's required slot type is present.
     *
     * @param template the parsed template; {@code null} yields a single {@code missing_template} issue
     * @param page     the page this layout is meant to be
     * @return the issues found; empty when the layout is usable
     */
    public static List<LayoutIssue> validate(GuiTemplate template, Page page) {
        List<LayoutIssue> issues = new ArrayList<>();
        if (template == null) {
            issues.add(new LayoutIssue("?", "missing_template", ""));
            return issues;
        }
        String layoutId = template.id();
        if (template.isChest() && (template.rows() < 1 || template.rows() > 6)) {
            issues.add(new LayoutIssue(layoutId, "bad_rows", String.valueOf(template.rows())));
        }
        int rows = template.rows();
        Set<String> accepted = page.acceptedTypes();
        Map<Integer, String> claimedByType = new HashMap<>();
        boolean hasRequired = false;
        for (GuiSlot slot : template.slots().values()) {
            if (slot == null) {
                continue;
            }
            String type = StationSlotType.normalize(slot.type());
            if (!type.isEmpty() && !accepted.contains(type)) {
                // A recognised type on the wrong page is a different mistake from an unrecognised one, and
                // saying so saves an administrator from hunting a typo that is not there.
                String code = StationSlotType.known(type) ? "wrong_page_type" : "unknown_type";
                issues.add(new LayoutIssue(layoutId, code, slot.key() + "=" + type));
            }
            if (page.requiredType().equals(type) && !slot.slots().isEmpty()) {
                hasRequired = true;
            }
            for (Integer position : slot.slots()) {
                if (template.isChest() && !SlotParser.isValidSlot(position, rows)) {
                    issues.add(new LayoutIssue(layoutId, "slot_out_of_range",
                            slot.key() + "=" + position));
                    continue;
                }
                if (type.isEmpty()) {
                    continue;
                }
                String previous = claimedByType.putIfAbsent(position, slot.key());
                if (previous != null) {
                    issues.add(new LayoutIssue(layoutId, "duplicate_slot",
                            position + " claimed by " + previous + " and " + slot.key()));
                }
            }
        }
        if (!hasRequired) {
            issues.add(new LayoutIssue(layoutId, page.missingCode(), ""));
        }
        return issues;
    }
}

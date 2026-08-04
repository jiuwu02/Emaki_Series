package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.gui.SlotParser;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

/**
 * Startup validation for station layouts.
 *
 * <p>Runs during the configuration precheck stage. A layout that fails validation is reported and the
 * previous valid runtime configuration is kept, because replacing a working station set with a broken one
 * is worse than refusing the change.
 */
public final class StationLayoutValidator {

    private StationLayoutValidator() {
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

    /**
     * Validates one layout.
     *
     * <p>Checks, in order: the row count is inside the chest range, every declared slot resolves to a
     * position that exists in that many rows, no two typed groups claim the same slot, every declared type
     * is one EmakiStation can actually render, and at least one input slot exists.
     *
     * @param template the parsed template; {@code null} yields a single {@code missing_template} issue
     * @return the issues found; empty when the layout is usable
     */
    public static List<LayoutIssue> validate(GuiTemplate template) {
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
        Map<Integer, String> claimedByType = new HashMap<>();
        boolean hasInput = false;
        for (GuiSlot slot : template.slots().values()) {
            if (slot == null) {
                continue;
            }
            String type = StationSlotType.normalize(slot.type());
            if (!type.isEmpty() && !StationSlotType.known(type)) {
                issues.add(new LayoutIssue(layoutId, "unknown_type", slot.key() + "=" + type));
            }
            if (StationSlotType.INPUT.equals(type) && !slot.slots().isEmpty()) {
                hasInput = true;
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
        if (!hasInput) {
            issues.add(new LayoutIssue(layoutId, "missing_input", ""));
        }
        return issues;
    }
}

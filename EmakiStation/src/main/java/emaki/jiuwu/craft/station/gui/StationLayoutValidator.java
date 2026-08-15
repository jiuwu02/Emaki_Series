package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.gui.SlotParser;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

public final class StationLayoutValidator {

    public enum Page {

        CATALOG(StationSlotType.RECIPE_LIST, "missing_recipe_list"),

        PREVIEW(StationSlotType.MATERIAL_LIST, "missing_material_list"),

        QUEUE(StationSlotType.QUEUE_VIEW, "missing_queue_view"),

        DISMANTLE(StationSlotType.DISMANTLE_CONFIRM, "missing_dismantle_confirm");

        private final String requiredType;
        private final String missingCode;

        Page(String requiredType, String missingCode) {
            this.requiredType = requiredType;
            this.missingCode = missingCode;
        }

        public String requiredType() {
            return requiredType;
        }

        public String missingCode() {
            return missingCode;
        }

        public Set<String> acceptedTypes() {
            return switch (this) {
                case CATALOG -> StationSlotType.catalogTypes();
                case PREVIEW -> StationSlotType.previewTypes();
                case QUEUE -> StationSlotType.queueTypes();
                case DISMANTLE -> StationSlotType.dismantleTypes();
            };
        }
    }

    public record LayoutIssue(String layoutId, String code, String detail) {
    }

    private StationLayoutValidator() {
    }

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

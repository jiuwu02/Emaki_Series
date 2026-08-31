package emaki.jiuwu.craft.corelib.gui;

public final class GuiPagination {

    private GuiPagination() {
    }

    public static int pageSize(GuiTemplate template, String type) {
        if (template == null || type == null) {
            return 0;
        }
        int size = 0;
        for (GuiSlot slot : template.slotsByType(type)) {
            size += slot.slots().size();
        }
        return size;
    }

    public static int totalPages(int count, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil((double) count / pageSize));
    }
}

package emaki.jiuwu.craft.corelib.gui;

/**
 * Pure pagination arithmetic shared by paged GUIs.
 *
 * <p>Deliberately limited to stateless computation. Page dispatch, session state and
 * reachable-page clamping stay in the owning module, because those carry per-module
 * semantics that must not be unified here.
 */
public final class GuiPagination {

    private GuiPagination() {
    }

    /**
     * Sums the slot count of every slot entry matching {@code type} in the template.
     *
     * @return the per-page capacity, or {@code 0} when the template is null or has no
     *         matching slots
     */
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

    /**
     * Computes the page count for {@code count} entries at {@code pageSize} per page.
     *
     * @return at least {@code 1}; also {@code 1} when {@code pageSize <= 0}
     */
    public static int totalPages(int count, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil((double) count / pageSize));
    }
}

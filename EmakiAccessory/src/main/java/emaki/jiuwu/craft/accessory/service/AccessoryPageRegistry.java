package emaki.jiuwu.craft.accessory.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.accessory.model.AccessoryPage;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessoryPageRegistry {

    private static final AccessoryPageRegistry EMPTY =
            new AccessoryPageRegistry(Map.of(), Map.of(), List.of());

    private final Map<String, AccessoryPage> pages;
    private final Map<String, List<String>> pageSlots;
    private final List<String> order;

    private AccessoryPageRegistry(Map<String, AccessoryPage> pages,
            Map<String, List<String>> pageSlots,
            List<String> order) {
        this.pages = pages;
        this.pageSlots = pageSlots;
        this.order = order;
    }

    public static AccessoryPageRegistry empty() {
        return EMPTY;
    }

    public static AccessoryPageRegistry of(Map<String, AccessoryPage> loaded,
            AccessoryPartRegistry partRegistry) {
        if (loaded == null || loaded.isEmpty()) {
            return EMPTY;
        }
        AccessoryPartRegistry parts = partRegistry == null ? AccessoryPartRegistry.empty() : partRegistry;
        List<AccessoryPage> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparingInt(AccessoryPage::order).thenComparing(AccessoryPage::pageId));
        Map<String, AccessoryPage> accepted = new LinkedHashMap<>();
        Map<String, List<String>> slots = new LinkedHashMap<>();
        List<String> pageOrder = new ArrayList<>();
        for (AccessoryPage page : sorted) {
            if (page == null || Texts.isBlank(page.pageId())) {
                continue;
            }
            List<String> resolved = new ArrayList<>();
            for (String partId : page.parts()) {
                for (String slotInstanceId : parts.slotInstanceIdsOf(partId)) {
                    if (!resolved.contains(slotInstanceId)) {
                        resolved.add(slotInstanceId);
                    }
                }
            }
            accepted.put(page.pageId(), page);
            slots.put(page.pageId(), List.copyOf(resolved));
            pageOrder.add(page.pageId());
        }
        return new AccessoryPageRegistry(
                Map.copyOf(accepted),
                Map.copyOf(slots),
                List.copyOf(pageOrder));
    }

    public boolean isEmpty() {
        return order.isEmpty();
    }

    public List<String> pageIds() {
        return order;
    }

    public Map<String, AccessoryPage> pages() {
        return pages;
    }

    public AccessoryPage page(String pageId) {
        return pages.get(Texts.normalizeId(pageId));
    }

    public boolean hasPage(String pageId) {
        return pages.containsKey(Texts.normalizeId(pageId));
    }

    public String firstPageId() {
        return order.isEmpty() ? "" : order.get(0);
    }

    public List<String> slotsOf(String pageId) {
        List<String> slots = pageSlots.get(Texts.normalizeId(pageId));
        return slots == null ? List.of() : slots;
    }

    public boolean declaresSlot(String pageId, String slotInstanceId) {
        return slotsOf(pageId).contains(Texts.normalizeId(slotInstanceId));
    }

    public String resolveEnabledPage(String requested) {
        String normalized = Texts.normalizeId(requested);
        if (Texts.isNotBlank(normalized) && pages.containsKey(normalized)) {
            return normalized;
        }
        return firstPageId();
    }

    public String templateOf(String pageId) {
        AccessoryPage page = page(pageId);
        return page == null ? "" : page.guiTemplate();
    }

    public String permissionOf(String pageId) {
        AccessoryPage page = page(pageId);
        return page == null ? "" : page.permission();
    }
}

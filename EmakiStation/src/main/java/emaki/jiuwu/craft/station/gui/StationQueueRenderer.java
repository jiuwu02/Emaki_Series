package emaki.jiuwu.craft.station.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.QueueEntry;
import emaki.jiuwu.craft.station.queue.StationQueueUnlockService;

public final class StationQueueRenderer {

    private final Supplier<ConfiguredItemService> itemServiceSupplier;
    private final ConfiguredGuiSupport guiSupport;

    public StationQueueRenderer(Supplier<ConfiguredItemService> itemServiceSupplier,
            ConfiguredGuiSupport guiSupport) {
        this.itemServiceSupplier = itemServiceSupplier;
        this.guiSupport = guiSupport;
    }

    public ItemStack render(StationViewState state,
            CraftQueue queue,
            int capacity,
            int purchased,
            StationQueueUnlockService.Quote quote,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (state == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = StationSlotType.normalize(slot.type());
        if (type.isEmpty()) {
            type = StationSlotType.normalize(slot.key());
        }
        return switch (type) {
            case StationSlotType.QUEUE_VIEW -> renderEntry(state, queue, slot, resolvedSlot);
            case StationSlotType.CAPACITY_DISPLAY -> renderCapacity(state, queue, slot, capacity, purchased);
            case StationSlotType.QUEUE_PURCHASE -> renderPurchase(state, slot, quote);
            case StationSlotType.CLAIM_ALL -> renderClaimAll(state, queue, slot);
            case StationSlotType.PAGE_INFO, StationSlotType.PREV_PAGE, StationSlotType.NEXT_PAGE ->
                    renderPageInfo(state, queue, slot);
            default -> null;
        };
    }

    public Map<String, Object> titleReplacements(StationViewState state, CraftQueue queue, int capacity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("station_name", state.station().displayName());
        values.put("station", state.station().id());
        values.put("player", state.viewer().getName());
        values.put("used", String.valueOf(activeCount(queue)));
        values.put("max", String.valueOf(capacity));
        return values;
    }

    private ItemStack renderEntry(StationViewState state,
            CraftQueue queue,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (queue == null) {
            return null;
        }
        List<QueueEntry> entries = queue.entries();
        int pageSize = Math.max(1, slot.slots().size());
        int offset = state.queuePage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= entries.size()) {
            return null;
        }
        QueueEntry entry = entries.get(offset);
        long remaining = entry.remainingMillis(state.station().progressMode(), System.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("index", String.valueOf(offset + 1));
        values.put("recipe", entry.recipeId());
        values.put("batch", AmountDisplay.compact(entry.batch()));
        values.put("state", entry.state().token());
        values.put("remaining", DurationDisplay.format(remaining));
        values.put("cost", AmountDisplay.precise(entry.costAmount()));
        values.put("currency", entry.costProviderId());
        values.put("page", String.valueOf(state.queuePage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(entries.size(), pageSize)));
        String path = entry.state() == QueueEntryState.PENDING_CLAIM
                ? "virtual_items.queue_pending"
                : "virtual_items.queue_entry";
        ItemStack base = GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
        return guiSupport.apply(state.station().queueLayoutId(), path, base, values);
    }

    private ItemStack renderCapacity(StationViewState state,
            CraftQueue queue,
            GuiSlot slot,
            int capacity,
            int purchased) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("used", String.valueOf(activeCount(queue)));
        values.put("max", String.valueOf(capacity));
        values.put("purchased", String.valueOf(purchased));
        values.put("base", String.valueOf(state.station().queueSettings().baseLength()));
        values.put("ceiling", String.valueOf(state.station().queueSettings().maxLength()));
        values.put("pending", String.valueOf(pendingCount(queue)));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderPurchase(StationViewState state,
            GuiSlot slot,
            StationQueueUnlockService.Quote quote) {
        String layoutId = state.station().queueLayoutId();
        Map<String, Object> values = new LinkedHashMap<>();
        if (quote == null || !quote.valid()) {
            String reason = quote == null ? "unavailable" : quote.rejection();
            values.put("reason", reason);
            values.put("reason_text", guiSupport.text(layoutId,
                    "texts.purchase_reason." + reason, reason, Map.of()));
            return guiSupport.build(layoutId, "virtual_items.purchase_unavailable", values,
                    StationRenderFallbacks.purchaseUnavailable(reason));
        }
        values.put("slots", String.valueOf(quote.slots()));
        values.put("cost", AmountDisplay.precise((long) quote.currencyCost()));
        values.put("currency", quote.currencyId());
        values.put("items", String.valueOf(quote.itemCosts().size()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderClaimAll(StationViewState state, CraftQueue queue, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pending", String.valueOf(pendingCount(queue)));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderPageInfo(StationViewState state, CraftQueue queue, GuiSlot slot) {
        int pageSize = state.guiSession() == null
                ? 1
                : Math.max(1, GuiPagination.pageSize(state.guiSession().template(),
                        StationSlotType.QUEUE_VIEW));
        int total = queue == null ? 0 : queue.entries().size();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("page", String.valueOf(state.queuePage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(total, pageSize)));
        values.put("total", String.valueOf(total));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private static int activeCount(CraftQueue queue) {
        if (queue == null) {
            return 0;
        }
        int count = 0;
        for (QueueEntry entry : queue.entries()) {
            if (entry.state() != QueueEntryState.PENDING_CLAIM) {
                count++;
            }
        }
        return count;
    }

    private static int pendingCount(CraftQueue queue) {
        if (queue == null) {
            return 0;
        }
        int count = 0;
        for (QueueEntry entry : queue.entries()) {
            if (entry.state() == QueueEntryState.PENDING_CLAIM) {
                count++;
            }
        }
        return count;
    }
}

package emaki.jiuwu.craft.storage.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.service.StorageCapacityService;
import emaki.jiuwu.craft.storage.service.StorageOverflowService;
import emaki.jiuwu.craft.storage.service.StorageSearchService;

public final class StorageGuiService {

    public static final String KEY_CURRENT_PAGE = "current_page";

    private final GuiService guiService;
    private final StorageCapacityService capacityService;
    private final StorageSearchService searchService;
    private final StorageOverflowService overflowService;
    private final StorageAmountFormatter amountFormatter;
    private final MessageService messageService;

    private final Map<UUID, ViewState> viewStates = new ConcurrentHashMap<>();

    private volatile AppConfig config;
    private volatile StorageLayoutResolver.Layout layout;

    public record SlotView(StorageKey key, int spanIndex, int spanCount, long slice) {

        public boolean spanned() {
            return spanCount > 1;
        }
    }

    public static final class ViewState {

        private SearchQuery query = SearchQuery.empty();
        private String queryText = "";
        private List<StorageKey> visible = List.of();
        private List<SlotView> slots = List.of();
        private StorageCapacity capacity = StorageCapacity.empty();
        private StorageOverflowService.OverflowState overflow = StorageOverflowService.OverflowState.none();

        public SearchQuery query() {
            return query;
        }

        public String queryText() {
            return queryText;
        }

        public boolean searching() {
            return !query.isEmpty();
        }

        public List<StorageKey> visible() {
            return visible;
        }

        public List<SlotView> slots() {
            return slots;
        }

        public StorageCapacity capacity() {
            return capacity;
        }

        public StorageOverflowService.OverflowState overflow() {
            return overflow;
        }

        public void applyQuery(SearchQuery query, String queryText) {
            this.query = query == null ? SearchQuery.empty() : query;
            this.queryText = queryText == null ? "" : queryText;
        }

        void visible(List<StorageKey> visible) {
            this.visible = visible == null ? List.of() : List.copyOf(visible);
        }

        void slots(List<SlotView> slots) {
            this.slots = slots == null ? List.of() : List.copyOf(slots);
        }

        void capacity(StorageCapacity capacity) {
            this.capacity = capacity == null ? StorageCapacity.empty() : capacity;
        }

        void overflow(StorageOverflowService.OverflowState overflow) {
            this.overflow = overflow == null ? StorageOverflowService.OverflowState.none() : overflow;
        }
    }

    public StorageGuiService(GuiService guiService,
            StorageCapacityService capacityService,
            StorageSearchService searchService,
            StorageOverflowService overflowService,
            StorageAmountFormatter amountFormatter,
            MessageService messageService,
            AppConfig config) {
        this.guiService = guiService;
        this.capacityService = capacityService;
        this.searchService = searchService;
        this.overflowService = overflowService;
        this.amountFormatter = amountFormatter;
        this.messageService = messageService;
        this.config = config;
    }

    public void reconfigure(AppConfig config, StorageLayoutResolver.Layout layout) {
        if (config != null) {
            this.config = config;
        }
        if (layout != null) {
            this.layout = layout;
        }
    }

    public StorageLayoutResolver.Layout layout() {
        return layout;
    }

    public int slotsPerPage() {
        StorageLayoutResolver.Layout active = layout;
        return active == null ? 1 : active.slotsPerPage();
    }

    public ViewState viewState(UUID viewerId) {
        return viewStates.computeIfAbsent(viewerId, id -> new ViewState());
    }

    public void releaseViewState(UUID viewerId) {
        viewStates.remove(viewerId);
    }

    public GuiSession open(Player player, PlayerStorage storage, StorageGuiHandler handler) {
        StorageLayoutResolver.Layout active = layout;
        if (player == null || storage == null || active == null) {
            return null;
        }
        ViewState state = viewState(player.getUniqueId());
        state.applyQuery(SearchQuery.empty(), "");
        refreshView(player, storage, state);

        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_CURRENT_PAGE, 0);

        GuiOpenRequest request = new GuiOpenRequest(handler.plugin(), player, active.template(),
                replacements, this::renderSlot, handler);
        return guiService.open(request);
    }

    public void refreshView(Player player, PlayerStorage storage, ViewState state) {
        StorageCapacity capacity = capacityService.capacityOf(storage, player, slotsPerPage());
        state.capacity(capacity);
        state.overflow(overflowService.evaluate(storage, player, capacity));
        List<StorageKey> visible = searchService.filter(storage, state.query());
        state.visible(visible);
        state.slots(expandSlots(storage, visible));
    }

    private List<SlotView> expandSlots(PlayerStorage storage, List<StorageKey> visible) {
        if (storage == null || visible.isEmpty()) {
            return List.of();
        }
        List<SlotView> expanded = new ArrayList<>(visible.size());
        for (StorageKey key : visible) {
            StorageEntry entry = storage.entry(key);
            if (entry == null) {
                continue;
            }
            long limit = capacityService.effectiveStackLimit(storage, entry);
            int span = capacityService.slotSpan(storage, entry);
            for (int spanIndex = 0; spanIndex < span; spanIndex++) {
                expanded.add(new SlotView(key, spanIndex, span,
                        capacityService.sliceAmount(entry.amount(), limit, spanIndex)));
            }
        }
        return expanded;
    }

    public static int currentPage(GuiSession session) {
        Object raw = session.replacements().get(KEY_CURRENT_PAGE);
        return raw instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    public void applyPage(GuiSession session, int page) {
        session.putReplacement(KEY_CURRENT_PAGE, Math.max(0, page));
        session.refresh();
    }

    public void refresh(GuiSession session) {
        if (session != null) {
            session.refresh();
        }
    }

    private ItemStack renderSlot(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        if (resolved == null || resolved.definition() == null) {
            return null;
        }
        String type = resolved.definition().type();
        if (type == null) {
            return null;
        }
        StorageGuiHandler handler = session.handler() instanceof StorageGuiHandler storageHandler
                ? storageHandler
                : null;
        if (handler == null) {
            return null;
        }
        PlayerStorage storage = handler.storage();
        if (storage == null) {
            return null;
        }
        ViewState state = viewState(session.viewer().getUniqueId());
        StorageCapacity capacity = state.capacity();
        return switch (type) {
            case StorageLayoutResolver.TYPE_STORAGE_SLOT ->
                renderStorageSlot(session, resolved, storage, state, capacity);
            case StorageLayoutResolver.TYPE_PAGE_INFO ->
                renderPageInfo(session, resolved, state, capacity);
            case StorageLayoutResolver.TYPE_SORT -> renderSortButton(resolved, storage);
            case StorageLayoutResolver.TYPE_SEARCH -> renderSearchButton(resolved, state);
            case StorageLayoutResolver.TYPE_UNLOCK -> renderUnlockButton(resolved, capacity);
            default -> null;
        };
    }

    private ItemStack renderStorageSlot(GuiSession session,
            GuiTemplate.ResolvedSlot resolved,
            PlayerStorage storage,
            ViewState state,
            StorageCapacity capacity) {
        int page = currentPage(session);
        int index = page * slotsPerPage() + resolved.slotIndex();
        List<SlotView> slots = state.slots();
        if (index < 0 || index >= slots.size()) {

            if (index >= 0 && index < capacity.effectiveSlots()) {
                return new ItemStack(Material.AIR);
            }
            return renderLockedSlot(resolved);
        }
        SlotView view = slots.get(index);
        StorageEntry entry = storage.entry(view.key());
        if (entry == null) {
            return new ItemStack(Material.AIR);
        }
        long limit = capacityService.effectiveStackLimit(storage, entry);
        return renderEntry(view, entry, limit, state.overflow().locked(view.key()), state.searching());
    }

    private ItemStack renderEntry(SlotView view,
            StorageEntry entry,
            long stackLimit,
            boolean locked,
            boolean searching) {
        ItemStack rendered = view.key().toItemStack();

        int displayAmount = amountFormatter.displayAmount(view.slice(), stackLimit);
        rendered.setAmount(displayAmount);
        ItemMeta meta = rendered.getItemMeta();
        if (meta != null) {
            int scale = amountFormatter.clampScale(config.display().percentScale());
            if (config.display().amountMode() == AppConfig.AmountMode.PERCENT
                    && amountFormatter.percentMeaningful(stackLimit)) {
                meta.setMaxStackSize(scale);
            }
            List<Component> lore = new ArrayList<>();
            List<Component> existing = meta.lore();
            List<Component> generated = buildEntryLore(view, entry, stackLimit, locked, searching);
            if (amountFormatter.lorePosition() == AppConfig.LorePosition.TOP) {
                lore.addAll(generated);
                if (existing != null) {
                    lore.addAll(existing);
                }
            } else {
                if (existing != null) {
                    lore.addAll(existing);
                }
                lore.addAll(generated);
            }
            meta.lore(lore);
            rendered.setItemMeta(meta);
        }
        return rendered;
    }

    private List<Component> buildEntryLore(SlotView view,
            StorageEntry entry,
            long stackLimit,
            boolean locked,
            boolean searching) {
        List<Component> lines = new ArrayList<>();
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("amount", amountFormatter.compact(view.slice()));
        replacements.put("limit", amountFormatter.compactLimit(stackLimit));
        replacements.put("percent", amountFormatter.percentText(view.slice(), stackLimit));
        replacements.put("exact", amountFormatter.exact(view.slice()));

        if (amountFormatter.percentMeaningful(stackLimit)) {
            lines.add(render("gui.entry.amount_with_limit", replacements));
        } else {
            lines.add(render("gui.entry.amount_unlimited", replacements));
        }
        if (amountFormatter.showExactAmount()) {
            lines.add(render("gui.entry.exact", replacements));
        }
        if (view.spanned()) {

            lines.add(render("gui.entry.span_total", Map.of(
                    "total", amountFormatter.compact(entry.amount()),
                    "slots", view.spanCount(),
                    "slot", view.spanIndex() + 1)));
        }
        AppConfig.WithdrawAmounts amounts = config.behavior().withdrawAmounts();
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("left", amounts.left());
        hints.put("right", amounts.right());
        hints.put("shift_left", amounts.shiftLeft());
        hints.put("shift_right", amounts.shiftRight());
        lines.add(render("gui.entry.withdraw_hint", hints));
        if (config.behavior().withdrawPromptEnabled()) {
            lines.add(render("gui.entry.custom_hint", Map.of()));
        }
        if (locked) {
            lines.add(render("gui.entry.overflow_locked", Map.of()));
        } else if (searching) {

            lines.add(render("gui.entry.search_readonly", Map.of()));
        }
        return lines;
    }

    private ItemStack renderLockedSlot(GuiTemplate.ResolvedSlot resolved) {
        GuiSlot definition = resolved.definition();
        String item = Texts.isBlank(definition.item()) ? "gray_stained_glass_pane" : definition.item();
        return GuiItemBuilder.build(
                null,
                item,
                messageService.message("gui.locked.name"),
                List.of(messageService.message("gui.locked.lore")),
                Map.of(),
                guiService.configuredItemService());
    }

    private ItemStack renderPageInfo(GuiSession session,
            GuiTemplate.ResolvedSlot resolved,
            ViewState state,
            StorageCapacity capacity) {
        int page = currentPage(session);
        int perPage = slotsPerPage();

        int slotCount = state.slots().size();
        int visibleCount = state.visible().size();
        int reachable = GuiPagination.totalPages(slotCount, perPage);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("current_page", page + 1);
        replacements.put("total_pages", Math.max(reachable, 1));
        replacements.put("capacity_pages", capacity.totalPages());
        replacements.put("used_slots", capacity.usedSlots());
        replacements.put("total_slots", capacity.effectiveSlots());
        replacements.put("free_slots", capacity.freeSlots());
        replacements.put("visible", visibleCount);
        List<String> lore = new ArrayList<>();
        lore.add(messageService.message("gui.page_info.capacity", replacements));
        if (state.searching()) {
            lore.add(messageService.message("gui.page_info.searching",
                    Map.of("query", state.queryText(), "visible", visibleCount)));
        }
        if (state.overflow().hasOverflow()) {
            lore.add(messageService.message("gui.page_info.overflow",
                    Map.of("count", state.overflow().lockedKeys().size())));
        }
        return buildButton(resolved, "paper", messageService.message("gui.page_info.name", replacements),
                lore, replacements);
    }

    private ItemStack renderSortButton(GuiTemplate.ResolvedSlot resolved, PlayerStorage storage) {
        Map<String, Object> replacements = Map.of("mode",
                messageService.message("gui.sort.modes." + storage.sortMode().id()));
        return buildButton(resolved, "hopper",
                messageService.message("gui.sort.name", replacements),
                List.of(messageService.message("gui.sort.current", replacements),
                        messageService.message("gui.sort.hint")),
                replacements);
    }

    private ItemStack renderSearchButton(GuiTemplate.ResolvedSlot resolved, ViewState state) {
        Map<String, Object> replacements = Map.of("query",
                state.searching() ? state.queryText() : messageService.message("gui.search.none"));
        List<String> lore = new ArrayList<>();
        lore.add(messageService.message("gui.search.current", replacements));
        lore.add(messageService.message("gui.search.hint"));
        if (state.searching()) {
            lore.add(messageService.message("gui.search.clear_hint"));
        }
        return buildButton(resolved, "compass",
                messageService.message("gui.search.name", replacements), lore, replacements);
    }

    private ItemStack renderUnlockButton(GuiTemplate.ResolvedSlot resolved, StorageCapacity capacity) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("total_slots", capacity.effectiveSlots());
        replacements.put("purchased_slots", capacity.purchasedSlots());
        replacements.put("max_slots", capacity.maxSlots() == 0
                ? messageService.message("gui.unlock.unlimited")
                : String.valueOf(capacity.maxSlots()));
        List<String> lore = new ArrayList<>();
        boolean atCeiling = capacity.maxSlots() > 0 && capacity.effectiveSlots() >= capacity.maxSlots();
        lore.add(messageService.message("gui.unlock.status", replacements));
        lore.add(atCeiling
                ? messageService.message("gui.unlock.at_ceiling")
                : messageService.message("gui.unlock.hint"));
        return buildButton(resolved, atCeiling ? "barrier" : "emerald",
                messageService.message("gui.unlock.name", replacements), lore, replacements);
    }

    private ItemStack buildButton(GuiTemplate.ResolvedSlot resolved,
            String fallbackItem,
            String fallbackName,
            List<String> fallbackLore,
            Map<String, Object> replacements) {
        GuiSlot definition = resolved.definition();
        String item = Texts.isBlank(definition.item()) ? fallbackItem : definition.item();
        return GuiItemBuilder.build(
                definition,
                item,
                fallbackName,
                fallbackLore == null ? List.of() : fallbackLore,
                replacements == null ? Map.of() : replacements,
                guiService.configuredItemService());
    }

    private Component render(String key, Map<String, Object> replacements) {
        return MiniMessages.parse(messageService.message(key, replacements));
    }
}

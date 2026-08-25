package emaki.jiuwu.craft.item.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

final class ItemBrowserGuiHandler implements GuiSessionHandler {

    private final EmakiItemPlugin plugin;
    private final ItemBrowserGuiService browserService;

    ItemBrowserGuiHandler(EmakiItemPlugin plugin, ItemBrowserGuiService browserService) {
        this.plugin = plugin;
        this.browserService = browserService;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        click.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player player = session.viewer();
        switch (slot.definition().type()) {
            case ItemBrowserGuiService.TYPE_PACK_ENTRY ->
                enterPack(session, player, slot.slotIndex());
            case ItemBrowserGuiService.TYPE_ITEM_ENTRY ->
                takeItem(session, click, player, slot.slotIndex());
            case "back" ->
                returnToPackBrowser(player);
            case "page_prev" ->
                turnToPreviousPage(session);
            case "page_next" ->
                turnToNextPage(session);
            case "close" ->
                player.closeInventory();
            default -> {
            }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
        if (click.isBlockedTransfer()) {
            click.setCancelled(true);
        }
    }

    private void enterPack(GuiSession session, Player player, int slotIndex) {
        List<String> packs = browserService.packIds();
        int pageSize = Math.max(1, GuiPagination.pageSize(session.template(), ItemBrowserGuiService.TYPE_PACK_ENTRY));
        int index = currentPage(session) * pageSize + slotIndex;
        if (index < 0 || index >= packs.size()) {
            return;
        }
        String packId = packs.get(index);
        player.closeInventory();
        plugin.scheduling().runForEntity(plugin, player, () -> {
            if (!browserService.openItemBrowser(player, packId, 0)) {
                plugin.messageService().send(player, "browser.gui_open_failed");
            }
        }, () -> {
        });
    }

    private void returnToPackBrowser(Player player) {
        player.closeInventory();
        plugin.scheduling().runForEntity(plugin, player, () -> {
            if (!browserService.openPackBrowser(player, 0)) {
                plugin.messageService().send(player, "browser.gui_open_failed");
            }
        }, () -> {
        });
    }

    private void takeItem(GuiSession session, GuiClickContext click, Player player, int slotIndex) {
        if (click.isRightClick()) {
            return;
        }
        if (!click.isLeftClick()) {
            return;
        }
        String itemId = browserService.itemIdAt(session, slotIndex);
        if (itemId == null) {
            return;
        }
        int delivered = click.isShiftClick()
                ? browserService.deliverFullStack(player, itemId)
                : browserService.deliverSingle(player, itemId);
        if (delivered <= 0) {
            plugin.messageService().send(player, "browser.take_failed", Map.of("id", itemId));
            return;
        }
        plugin.messageService().send(player, "browser.take_success", Map.of(
                "id", itemId,
                "name", plugin.itemApi().catalog().displayName(itemId).orElse(itemId),
                "amount", delivered
        ));
    }

    private void turnToPreviousPage(GuiSession session) {
        int page = currentPage(session);
        if (page <= 0) {
            return;
        }
        session.putReplacement(ItemBrowserGuiService.KEY_CURRENT_PAGE, page - 1);
        session.refresh();
    }

    private void turnToNextPage(GuiSession session) {
        int page = currentPage(session);
        if (page >= totalPages(session) - 1) {
            return;
        }
        session.putReplacement(ItemBrowserGuiService.KEY_CURRENT_PAGE, page + 1);
        session.refresh();
    }

    private int totalPages(GuiSession session) {
        int packPageSize = GuiPagination.pageSize(session.template(), ItemBrowserGuiService.TYPE_PACK_ENTRY);
        if (packPageSize > 0) {
            return GuiPagination.totalPages(browserService.packIds().size(), packPageSize);
        }
        int itemPageSize = Math.max(1, GuiPagination.pageSize(session.template(), ItemBrowserGuiService.TYPE_ITEM_ENTRY));
        return GuiPagination.totalPages(browserService.itemCount(currentPackId(session)), itemPageSize);
    }

    static int currentPage(GuiSession session) {
        Object raw = session.replacements().get(ItemBrowserGuiService.KEY_CURRENT_PAGE);
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    static String currentPackId(GuiSession session) {
        Object raw = session.replacements().get(ItemBrowserGuiService.KEY_PACK_ID);
        return raw == null ? "" : Texts.toStringSafe(raw);
    }
}

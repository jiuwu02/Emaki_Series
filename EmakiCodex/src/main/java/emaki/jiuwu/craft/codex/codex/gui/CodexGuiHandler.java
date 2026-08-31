package emaki.jiuwu.craft.codex.codex.gui;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.codex.model.CodexCategory;
import emaki.jiuwu.craft.codex.codex.model.CodexEntry;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

public final class CodexGuiHandler implements GuiSessionHandler {

    static final String KEY_CURRENT_PAGE = "current_page";
    static final String KEY_CURRENT_CATEGORY = "current_category";

    private final CodexGuiService guiService;

    public CodexGuiHandler(CodexGuiService guiService) {
        this.guiService = guiService;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        click.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player viewer = session.viewer();
        switch (slot.definition().type()) {
            case "entry" -> clickEntry(session, click, slot, viewer);
            case "category_tab" -> selectCategory(session, slot);
            case "page_prev" -> turnBack(session);
            case "page_next" -> turnForward(session);
            case "close" -> viewer.closeInventory();
            default -> { }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
        if (click.isBlockedTransfer()) {
            click.setCancelled(true);
        }
    }

    @Override
    public void onDrag(GuiSession session, GuiDragContext drag) {
    }

    @Override
    public void onClose(GuiSession session, GuiCloseContext close) {
    }

    private void clickEntry(GuiSession session, GuiClickContext click,
            GuiTemplate.ResolvedSlot slot, Player viewer) {
        CodexCategory category = guiService.currentCategory(session);
        if (category == null) {
            return;
        }
        List<CodexEntry> visible = guiService.visibleEntries(viewer, category);
        int index = page(session) * guiService.entriesPerPage(session) + slot.slotIndex();
        if (index < 0 || index >= visible.size()) {
            return;
        }
        CodexEntry entry = visible.get(index);
        EmakiResult<?> outcome = click.isRightClick()
                ? guiService.claim(viewer, category.categoryId(), entry.entryId())
                : guiService.activate(viewer, category.categoryId(), entry.entryId());
        guiService.reportOutcome(viewer, outcome);
        session.refresh();
    }

    private void selectCategory(GuiSession session, GuiTemplate.ResolvedSlot slot) {
        List<CodexCategory> categories = guiService.categories();
        int index = slot.slotIndex();
        if (index < 0 || index >= categories.size()) {
            return;
        }
        session.putReplacement(KEY_CURRENT_CATEGORY, categories.get(index).categoryId());
        session.putReplacement(KEY_CURRENT_PAGE, 0);
        session.refresh();
    }

    private void turnBack(GuiSession session) {
        int page = page(session);
        if (page > 0) {
            session.putReplacement(KEY_CURRENT_PAGE, page - 1);
            session.refresh();
        }
    }

    private void turnForward(GuiSession session) {
        Player viewer = session.viewer();
        CodexCategory category = guiService.currentCategory(session);
        if (category == null) {
            return;
        }
        int pageSize = Math.max(1, guiService.entriesPerPage(session));
        int totalPages = GuiPagination.totalPages(
                guiService.visibleEntries(viewer, category).size(), pageSize);
        int page = page(session);
        if (page < totalPages - 1) {
            session.putReplacement(KEY_CURRENT_PAGE, page + 1);
            session.refresh();
        }
    }

    static int page(GuiSession session) {
        Object raw = session.replacements().get(KEY_CURRENT_PAGE);
        return raw instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    static String categoryId(GuiSession session) {
        Object raw = session.replacements().get(KEY_CURRENT_CATEGORY);
        return raw == null ? "" : String.valueOf(raw);
    }
}

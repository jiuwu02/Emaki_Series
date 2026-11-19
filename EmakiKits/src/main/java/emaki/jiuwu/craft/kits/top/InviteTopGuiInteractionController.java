package emaki.jiuwu.craft.kits.top;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

final class InviteTopGuiInteractionController implements GuiSessionHandler {

    private final InviteTopService top;

    InviteTopGuiInteractionController(InviteTopService top) {
        this.top = top;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        if (session == null || slot == null || slot.definition() == null) {
            return;
        }
        Player player = session.viewer();
        String type = slot.definition().type() == null ? "" : Texts.lower(slot.definition().type());
        switch (type) {
            case "page_prev", "previous_page" -> previousPage(session);
            case "page_next", "next_page" -> nextPage(session);
            case "close" -> {
                if (player != null) {
                    player.closeInventory();
                }
            }
            default -> { }
        }
    }

    @Override
    public void onClose(GuiSession session, GuiCloseContext close) {
        if (session != null && session.viewer() != null) {
            top.onViewClosed(session.viewer().getUniqueId());
        }
    }

    private void previousPage(GuiSession session) {
        int page = top.page(session);
        if (page > 0) {
            top.setPage(session, page - 1);
            top.refresh(session);
        }
    }

    private void nextPage(GuiSession session) {
        int page = top.page(session);
        if (page < top.totalPages(session) - 1) {
            top.setPage(session, page + 1);
            top.refresh(session);
        }
    }
}

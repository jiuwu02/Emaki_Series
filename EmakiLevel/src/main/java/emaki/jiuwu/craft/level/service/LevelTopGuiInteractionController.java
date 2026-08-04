package emaki.jiuwu.craft.level.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

final class LevelTopGuiInteractionController implements GuiSessionHandler {

    private final EmakiLevelPlugin plugin;
    private final LevelTopGuiService guiService;

    LevelTopGuiInteractionController(EmakiLevelPlugin plugin, LevelTopGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        if (session == null || slot == null || slot.definition() == null) {
            return;
        }
        Player player = session.viewer();
        String type = Texts.lower(slot.definition().type());
        switch (type) {
            case "page_prev", "previous_page" -> previousPage(session);
            case "page_next", "next_page" -> nextPage(session);
            case "back" -> plugin.levelGuiService().open(player, guiService.typeId(session));
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    private void previousPage(GuiSession session) {
        int page = guiService.page(session);
        if (page > 0) {
            guiService.setPage(session, page - 1);
            guiService.refresh(session);
        }
    }

    private void nextPage(GuiSession session) {
        int page = guiService.page(session);
        if (page < guiService.totalPages(session) - 1) {
            guiService.setPage(session, page + 1);
            guiService.refresh(session);
        }
    }
}

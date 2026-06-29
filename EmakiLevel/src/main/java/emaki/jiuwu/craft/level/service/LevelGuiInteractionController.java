package emaki.jiuwu.craft.level.service;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.LevelPermissions;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

final class LevelGuiInteractionController implements GuiSessionHandler {

    private final EmakiLevelPlugin plugin;
    private final LevelGuiService guiService;

    LevelGuiInteractionController(EmakiLevelPlugin plugin, LevelGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        if (session == null || click == null || slot == null || slot.definition() == null) {
            return;
        }
        Player player = session.viewer();
        String type = Texts.lower(slot.definition().type());
        switch (type) {
            case "level_type" -> handleLevelTypeClick(session, click, slot, player);
            case "page_prev", "previous_page" -> previousPage(session);
            case "page_next", "next_page" -> nextPage(session);
            case "top_button" -> plugin.levelTopGuiService().open(player, guiService.selectedType(session));
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    private void handleLevelTypeClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot, Player player) {
        LevelTypeConfig type = guiService.typeAt(session, slot);
        if (type == null) {
            return;
        }
        guiService.selectType(session, type.id());
        if (click.isRightClick()) {
            if (click.isShiftClick()) {
                levelUpMultiple(session, player, type);
            } else {
                levelUpOnce(session, player, type);
            }
            return;
        }
        sendInfo(player, type);
        guiService.refresh(session);
    }

    private void levelUpOnce(GuiSession session, Player player, LevelTypeConfig type) {
        if (!player.hasPermission(LevelPermissions.LEVELUP)) {
            plugin.messages().send(player, "command.no_permission");
            return;
        }
        LevelOperationResult result = plugin.levelService().levelUp(player.getUniqueId(), type.id(), LevelUpCause.MANUAL);
        if (result.success()) {
            plugin.messages().send(player, "gui.level.levelup_once_success", Map.of(
                    "type_display_name", type.displayName(),
                    "new_level", result.newLevel()
            ));
        } else {
            plugin.messages().send(player, "gui.level.levelup_failed", Map.of("failure_reason", failure(result.reason())));
        }
        guiService.refresh(session);
    }

    private void levelUpMultiple(GuiSession session, Player player, LevelTypeConfig type) {
        if (!player.hasPermission(LevelPermissions.LEVELUP)) {
            plugin.messages().send(player, "command.no_permission");
            return;
        }
        int successCount = 0;
        LevelOperationResult lastFailure = null;
        int maxSteps = Math.max(1, plugin.appConfig().maxAutoUpgradeSteps());
        for (int index = 0; index < maxSteps; index++) {
            LevelOperationResult result = plugin.levelService().levelUp(player.getUniqueId(), type.id(), LevelUpCause.MANUAL);
            if (!result.success()) {
                lastFailure = result;
                break;
            }
            successCount++;
        }
        if (successCount > 0) {
            plugin.messages().send(player, "gui.level.levelup_multi_success", Map.of(
                    "type_display_name", type.displayName(),
                    "count", successCount
            ));
        } else if (lastFailure != null) {
            plugin.messages().send(player, "gui.level.levelup_failed", Map.of("failure_reason", failure(lastFailure.reason())));
        }
        guiService.refresh(session);
    }

    private void sendInfo(Player player, LevelTypeConfig type) {
        PlayerLevelData data = plugin.dataStore().getOrLoad(player.getUniqueId(), plugin.typeRegistry().asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        if (entry == null) {
            return;
        }
        plugin.messages().send(player, "level.info_line", plugin.levelService().displayPlaceholders(type, entry));
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

    private String failure(String reason) {
        return plugin.messages().message("failure." + reason);
    }
}

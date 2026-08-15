package emaki.jiuwu.craft.skills.gui;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

public final class UpgradeGuiHandler implements GuiSessionHandler {

    static final String KEY_SKILL_ID = "skill_id";

    private final EmakiSkillsPlugin plugin;
    private final String targetSkillId;
    private final SkillUpgradeService upgradeService;
    private final MessageService messageService;
    private final SkillsGuiService skillsGuiService;
    private final Runnable onBack;

    private boolean upgrading;

    public UpgradeGuiHandler(EmakiSkillsPlugin plugin,
            String targetSkillId,
            SkillUpgradeService upgradeService,
            MessageService messageService,
            SkillsGuiService skillsGuiService,
            Runnable onBack) {
        this.plugin = plugin;
        this.targetSkillId = targetSkillId;
        this.upgradeService = upgradeService;
        this.messageService = messageService;
        this.skillsGuiService = skillsGuiService;
        this.onBack = onBack;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        click.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player player = session.viewer();
        String type = slot.definition().type();

        switch (type) {
            case "confirm" -> handleConfirm(session, player);
            case "back" -> handleBack(player);
            case "close" -> player.closeInventory();
            default -> {  }
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

    private void handleConfirm(GuiSession session, Player player) {
        if (upgrading) {
            return;
        }
        if (upgradeService == null) {
            messageService.send(player, "upgrade.invalid");
            return;
        }
        upgrading = true;
        try {
            SkillUpgradeService.UpgradeResult result = upgradeService.upgrade(player, targetSkillId);
            messageService.send(player, result.messageKey(), result.placeholders());

            skillsGuiService.renderUpgradeGui(session);
        } finally {
            upgrading = false;
        }
    }

    private void handleBack(Player player) {
        player.closeInventory();
        plugin.scheduling().runForEntity(plugin, player, onBack, () -> { });
    }
}

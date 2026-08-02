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

/**
 * 技能升级界面的会话处理器。
 *
 * <p>形态参照 {@link TriggerSelectGuiHandler}：持有目标技能 id 与返回回调，
 * 每次打开界面新建一个实例，因此实例状态天然按会话隔离。</p>
 */
public final class UpgradeGuiHandler implements GuiSessionHandler {

    static final String KEY_SKILL_ID = "skill_id";

    private final EmakiSkillsPlugin plugin;
    private final String targetSkillId;
    private final SkillUpgradeService upgradeService;
    private final MessageService messageService;
    private final SkillsGuiService skillsGuiService;
    private final Runnable onBack;

    /**
     * 升级结算期间的重入闸门。
     *
     * <p>{@code GuiClickThrottle} 只按时间间隔挡住过快的连点，无法保证「上一次
     * 升级已经结算完毕」；而升级会扣货币与材料，重复进入等于重复扣费。因此确认
     * 槽额外做一次 in-flight 判断。该字段只在主线程/实体线程的点击回调中读写，
     * 与会话一一对应，不需要额外同步。</p>
     */
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
            // 成功、失败与降级都要重渲染：等级、成本与参数对比在任一情形下都可能已经变了。
            skillsGuiService.renderUpgradeGui(session);
        } finally {
            upgrading = false;
        }
    }

    private void handleBack(Player player) {
        player.closeInventory();
        plugin.executionDispatcher().runEntity(plugin, player, onBack, () -> { });
    }
}

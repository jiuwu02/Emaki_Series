package emaki.jiuwu.craft.skills.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerCategory;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class TriggerSelectGuiHandler implements GuiSessionHandler {

    static final String KEY_TARGET_SLOT = "target_slot";

    private final EmakiSkillsPlugin plugin;
    private final int targetSlot;
    private final PlayerSkillStateService stateService;
    private final TriggerRegistry triggerRegistry;
    private final MessageService messageService;
    private final Runnable onBack;

    public TriggerSelectGuiHandler(EmakiSkillsPlugin plugin,
            int targetSlot,
            PlayerSkillStateService stateService,
            TriggerRegistry triggerRegistry,
            MessageService messageService,
            Runnable onBack) {
        this.plugin = plugin;
        this.targetSlot = targetSlot;
        this.stateService = stateService;
        this.triggerRegistry = triggerRegistry;
        this.messageService = messageService;
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
            case "trigger_option" -> handleTriggerOptionClick(session, slot, player);
            case "back" -> handleBack(player);
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


    private void handleTriggerOptionClick(GuiSession session, GuiTemplate.ResolvedSlot slot, Player player) {
        List<SkillTriggerDefinition> enabledTriggers = getEnabledTriggers();
        int index = slot.slotIndex();
        if (index < 0 || index >= enabledTriggers.size()) {
            return;
        }

        SkillTriggerDefinition trigger = enabledTriggers.get(index);

        String conflict = stateService.checkTriggerConflict(player, targetSlot, trigger.id());
        if (conflict != null) {
            messageService.send(player, "gui.trigger_conflict", Map.of(
                    "trigger", trigger.displayName(),
                    "reason", conflict
            ));
            return;
        }

        boolean success = stateService.bindTrigger(player, targetSlot, trigger.id());
        if (success) {
            messageService.send(player, "gui.trigger_bound", Map.of(
                    "trigger", trigger.displayName(),
                    "slot", targetSlot
            ));
        } else {
            messageService.send(player, "gui.trigger_bind_failed");
            return;
        }

        player.closeInventory();
        plugin.scheduling().runForEntity(plugin, player, onBack, () -> { });
    }

    private void handleBack(Player player) {
        player.closeInventory();
        plugin.scheduling().runForEntity(plugin, player, onBack, () -> { });
    }


    List<SkillTriggerDefinition> getEnabledTriggers() {
        List<SkillTriggerDefinition> result = new ArrayList<>();
        for (SkillTriggerDefinition def : triggerRegistry.all().values()) {
            if (def.enabled() && def.category() == TriggerCategory.ACTIVE) {
                result.add(def);
            }
        }
        return result;
    }
}

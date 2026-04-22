package emaki.jiuwu.craft.skills.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;
import emaki.jiuwu.craft.skills.trigger.SkillTriggerDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerRegistry;

public final class TriggerSelectGuiHandler implements GuiSessionHandler {

    static final String KEY_TARGET_SLOT = "target_slot";

    private final JavaPlugin plugin;
    private final int targetSlot;
    private final PlayerSkillStateService stateService;
    private final TriggerRegistry triggerRegistry;
    private final MessageService messageService;
    private final Runnable onBack;

    public TriggerSelectGuiHandler(JavaPlugin plugin,
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
    public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
        event.setCancelled(true);
        if (slot == null || slot.definition() == null || slot.definition().type() == null) {
            return;
        }
        Player player = session.viewer();
        String type = slot.definition().type();

        switch (type) {
            case "trigger_option" -> handleTriggerOptionClick(session, slot, player);
            case "back" -> handleBack(player);
            default -> { /* filler or unknown */ }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
        if (GuiSessionHandler.isBlockedTransfer(event)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDrag(GuiSession session, InventoryDragEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onClose(GuiSession session, InventoryCloseEvent event) {
        // No special cleanup needed
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private void handleTriggerOptionClick(GuiSession session, GuiTemplate.ResolvedSlot slot, Player player) {
        List<SkillTriggerDefinition> enabledTriggers = getEnabledTriggers();
        int index = slot.slotIndex();
        if (index < 0 || index >= enabledTriggers.size()) {
            return;
        }

        SkillTriggerDefinition trigger = enabledTriggers.get(index);

        // Check for conflicts
        String conflict = stateService.checkTriggerConflict(player, targetSlot, trigger.id());
        if (conflict != null) {
            messageService.send(player, "gui.trigger_conflict", Map.of(
                    "trigger", trigger.displayName(),
                    "reason", conflict
            ));
            return;
        }

        // Bind the trigger
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

        // Go back to main GUI
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, onBack);
    }

    private void handleBack(Player player) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, onBack);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    List<SkillTriggerDefinition> getEnabledTriggers() {
        List<SkillTriggerDefinition> result = new ArrayList<>();
        for (SkillTriggerDefinition def : triggerRegistry.all().values()) {
            if (def.enabled()) {
                result.add(def);
            }
        }
        return result;
    }
}

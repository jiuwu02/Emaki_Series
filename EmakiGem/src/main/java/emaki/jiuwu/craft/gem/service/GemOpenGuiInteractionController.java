package emaki.jiuwu.craft.gem.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;

final class GemOpenGuiInteractionController {

    private final EmakiGemPlugin plugin;
    private final GemGuiStateManager stateManager;
    private final GemOpenGuiRenderer renderer;
    private final GemGuiService service;

    GemOpenGuiInteractionController(EmakiGemPlugin plugin,
            GemGuiStateManager stateManager,
            GemOpenGuiRenderer renderer,
            GemGuiService service) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.renderer = renderer;
        this.service = service;
    }

    public GuiSessionHandler createSessionHandler(GemOpenGuiSession state) {
        return new OpenSessionHandler(state);
    }

    private void scheduleSwitchIfNeeded(GemOpenGuiSession state) {
        if (state == null) {
            return;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        var template = GemGuiTemplates.resolveOpenTemplate(plugin.guiTemplateLoader(), itemDefinition);
        String resolvedId = template == null ? "" : template.id();
        if (!resolvedId.equals(state.currentTemplateId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> service.switchOpenTemplate(state));
            return;
        }
        renderer.refreshGui(state);
    }

    private void handleTargetItemClick(GemOpenGuiSession state, InventoryClickEvent event) {
        ItemStack cursorItem = GemOpenGuiSession.cloneNonAir(event.getCursor());
        ItemStack currentTarget = state.targetItem();
        if (cursorItem != null && plugin.stateService().resolveItemDefinition(cursorItem) == null) {
            plugin.messageService().send(state.player(), "gui.open.invalid_target");
            return;
        }
        state.setTargetItem(cursorItem);
        event.getWhoClicked().setItemOnCursor(currentTarget);
        scheduleSwitchIfNeeded(state);
    }

    private void handleOpenerItemClick(GemOpenGuiSession state, InventoryClickEvent event) {
        ItemStack cursorItem = GemOpenGuiSession.cloneNonAir(event.getCursor());
        ItemStack currentOpener = state.openerItem();
        if (cursorItem != null && !plugin.itemMatcher().isOpenerItem(cursorItem)) {
            plugin.messageService().send(state.player(), "gui.open.invalid_opener");
            return;
        }
        state.setOpenerItem(cursorItem);
        event.getWhoClicked().setItemOnCursor(currentOpener);
        renderer.refreshGui(state);
    }

    private void handleSocketClick(GemOpenGuiSession state, int displayIndex) {
        ItemStack targetItem = state.mutableTargetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        if (itemDefinition == null) {
            plugin.messageService().send(state.player(), "gui.open.target_required");
            return;
        }
        if (displayIndex < 0 || displayIndex >= itemDefinition.slots().size()) {
            return;
        }
        if (state.mutableOpenerItem() == null) {
            plugin.messageService().send(state.player(), "gui.open.hold_opener");
            return;
        }
        var slot = itemDefinition.slots().get(displayIndex);
        state.setSelectedSlotIndex(slot.index());
        renderer.refreshGui(state);
    }

    private void handleConfirm(GemOpenGuiSession state) {
        if (state.mutableTargetItem() == null) {
            plugin.messageService().send(state.player(), "gui.open.target_required");
            return;
        }
        if (state.mutableOpenerItem() == null) {
            plugin.messageService().send(state.player(), "gui.open.hold_opener");
            return;
        }
        if (state.selectedSlotIndex() < 0) {
            plugin.messageService().send(state.player(), "gui.open.no_slot_selected");
            return;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GemItemDefinition.SocketSlot slot = itemDefinition == null ? null : itemDefinition.slot(state.selectedSlotIndex());
        if (slot == null) {
            plugin.messageService().send(state.player(), "gui.open.no_slot_selected");
            return;
        }
        var gemState = plugin.stateService().resolveState(state.mutableTargetItem(), itemDefinition);
        if (gemState.isOpened(slot.index())) {
            state.setSelectedSlotIndex(slot.index());
        }
        var opener = plugin.itemMatcher().matchOpenerItem(state.mutableOpenerItem());
        if (opener == null) {
            plugin.messageService().send(state.player(), "gui.open.hold_opener");
            return;
        }
        Player player = state.player();
        Execution execution;
        if (player == null || state.mutableTargetItem() == null || state.mutableOpenerItem() == null) {
            execution = new Execution(null, state.mutableTargetItem(), state.mutableOpenerItem());
        } else {
            var hands = InventoryItemUtil.withTemporaryHands(
                    player,
                    state.mutableTargetItem(),
                    state.mutableOpenerItem(),
                    () -> plugin.socketOpenerService().openAt(player, player, opener.id(), state.selectedSlotIndex(), false)
            );
            execution = new Execution(hands.result(), hands.updatedMainHand(), hands.updatedOffHand());
        }
        if (execution.result() != null) {
            plugin.messageService().send(state.player(), execution.result().messageKey(), execution.result().placeholders());
        }
        if (execution.result() != null && execution.result().success()) {
            if (execution.updatedTarget() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), execution.updatedTarget());
            }
            state.setTargetItem(null);
            state.setOpenerItem(execution.updatedOpener());
        } else {
            state.setTargetItem(execution.updatedTarget());
            state.setOpenerItem(execution.updatedOpener());
        }
        state.clearSelectedSlot();
        renderer.refreshGui(state);
    }

    private final class OpenSessionHandler implements GuiSessionHandler {

        private final GemOpenGuiSession state;

        private OpenSessionHandler(GemOpenGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (Texts.lower(slot.definition().type())) {
                case "target_item" -> handleTargetItemClick(state, event);
                case "opener_item" -> handleOpenerItemClick(state, event);
                case "socket_slot" -> handleSocketClick(state, slot.slotIndex());
                case "confirm" -> handleConfirm(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
            if (GuiSessionHandler.isBlockedTransfer(event)) {
                event.setCancelled(true);
                return;
            }
            renderer.refreshGui(state);
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            if (state.templateSwitching()) {
                state.setTemplateSwitching(false);
                return;
            }
            if (state.mutableTargetItem() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), state.mutableTargetItem());
                state.setTargetItem(null);
            }
            if (state.mutableOpenerItem() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), state.mutableOpenerItem());
                state.setOpenerItem(null);
            }
            stateManager.remove(state);
        }
    }

    private record Execution(SocketOpenerService.Result result, ItemStack updatedTarget, ItemStack updatedOpener) {
    }
}

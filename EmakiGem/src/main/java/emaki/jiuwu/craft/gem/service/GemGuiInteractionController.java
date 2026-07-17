package emaki.jiuwu.craft.gem.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemGuiInteractionController {

    private final EmakiGemPlugin plugin;
    private final GemGuiStateManager stateManager;
    private final GemGuiRenderer renderer;
    private final GemGuiService service;

    GemGuiInteractionController(EmakiGemPlugin plugin,
            GemGuiStateManager stateManager,
            GemGuiRenderer renderer,
            GemGuiService service) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.renderer = renderer;
        this.service = service;
    }

    public GuiSessionHandler createSessionHandler(GemGuiSession state) {
        return new GemSessionHandler(state);
    }

    private void scheduleRefresh(GemGuiSession state) {
        FoliaSchedulerAdapter.runEntityTask(plugin, state.player(), () -> renderer.refreshGui(state));
    }

    private void scheduleSwitchIfNeeded(GemGuiSession state) {
        if (state == null) {
            return;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GuiTemplate template = GemGuiTemplates.resolveGemTemplate(plugin.guiTemplateLoader(), itemDefinition);
        String resolvedId = template == null ? "" : template.id();
        if (!resolvedId.equals(state.currentTemplateId())) {
            FoliaSchedulerAdapter.runEntityTask(plugin, state.player(), () -> {
                if (!service.switchTemplate(state)) {
                    renderer.refreshGui(state);
                }
            });
            return;
        }
        renderer.refreshGui(state);
    }

    private void handleSocketClick(GemGuiSession state, GuiClickContext click, int displayIndex) {
        ItemStack targetItem = state.mutableTargetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        if (itemDefinition == null) {
            plugin.messageService().send(state.player(), "gui.gem.target_required");
            return;
        }
        if (displayIndex < 0 || displayIndex >= itemDefinition.slots().size()) {
            return;
        }
        GemState currentState = plugin.stateService().resolveState(targetItem, itemDefinition);
        GemItemDefinition.SocketSlot slot = itemDefinition.slots().get(displayIndex);
        int slotIndex = slot.index();
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        if (state.mode() == GemGuiMode.INLAY) {
            if (!currentState.isOpened(slotIndex)) {
                plugin.messageService().send(state.player(), "gui.gem.open_via_open_gui");
                return;
            }
            if (currentState.assignment(slotIndex) != null) {
                plugin.messageService().send(state.player(), "command.inlay.slot_occupied", Map.of("slot", slotIndex));
                return;
            }
            if (isPendingInlaySlot(state, slotIndex) && cursorItem == null) {
                returnPendingInputToCursor(state, click);
                renderer.refreshGui(state);
                return;
            }
            GemItemInstance instance = plugin.itemMatcher().readGemInstance(cursorItem);
            GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (gemDefinition == null) {
                plugin.messageService().send(state.player(), "gui.gem.hold_gem");
                return;
            }
            if (!itemDefinition.allowsGemType(gemDefinition.gemType())) {
                plugin.messageService().send(state.player(), "command.inlay.gem_type_blocked", Map.of("type", gemDefinition.gemType()));
                return;
            }
            if (!gemDefinition.supportsSocketType(slot.type())) {
                plugin.messageService().send(state.player(), "command.inlay.socket_incompatible", Map.of("slot", slotIndex, "type", slot.type()));
                return;
            }
            if (itemDefinition.maxSameType() > 0
                    && plugin.stateService().countAssignmentsByType(itemDefinition, currentState).getOrDefault(gemDefinition.gemType(), 0) >= itemDefinition.maxSameType()) {
                plugin.messageService().send(state.player(), "command.inlay.max_same_type", Map.of("type", gemDefinition.gemType()));
                return;
            }
            if (plugin.stateService().countAssignmentsByGemId(currentState, gemDefinition.id()) >= itemDefinition.maxSameId()) {
                plugin.messageService().send(state.player(), "command.inlay.max_same_id", Map.of("gem", gemDefinition.id()));
                return;
            }
            returnPendingInput(state);
            state.setPendingOperation(new GemGuiSession.PendingOperation(
                    GemGuiSession.PendingType.INLAY,
                    slotIndex,
                    consumeOneFromCursor(click)
            ));
            renderer.refreshGui(state);
            return;
        }
        if (currentState.assignment(slotIndex) == null) {
            plugin.messageService().send(state.player(), "command.extract.slot_empty", Map.of("slot", slotIndex));
            return;
        }
        returnPendingInput(state);
        state.setPendingOperation(new GemGuiSession.PendingOperation(GemGuiSession.PendingType.EXTRACT, slotIndex, null));
        renderer.refreshGui(state);
    }

    private void handleTargetItemClick(GemGuiSession state, GuiClickContext click) {
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        ItemStack targetItem = state.targetItem();
        if (cursorItem != null && plugin.stateService().resolveItemDefinition(cursorItem) == null) {
            plugin.messageService().send(state.player(), "gui.gem.invalid_target");
            return;
        }
        returnPendingInput(state);
        state.setTargetItem(cursorItem);
        click.setCursor(targetItem);
        scheduleSwitchIfNeeded(state);
    }

    private void handleConfirm(GemGuiSession state) {
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        if (!pendingOperation.active()) {
            plugin.messageService().send(state.player(), "gui.gem.no_pending_action");
            return;
        }
        Player player = state.player();
        ItemStack targetItem = state.mutableTargetItem();
        if (player == null || targetItem == null) {
            plugin.messageService().send(state.player(), "gui.gem.target_required");
            return;
        }
        switch (pendingOperation.type()) {
            case INLAY -> executeInlay(state, pendingOperation, player, targetItem);
            case EXTRACT -> executeExtract(state, pendingOperation, player, targetItem);
            default -> plugin.messageService().send(player, "gui.gem.no_pending_action");
        }
    }

    private void executeInlay(GemGuiSession state, GemGuiSession.PendingOperation pendingOperation, Player player, ItemStack targetItem) {
        GemInlayService.InlayResult inlayResult = plugin.inlayService().inlayDirect(
                player, targetItem, pendingOperation.inputItem(), pendingOperation.slotIndex(), false, true);
        GemInlayService.Result result = inlayResult.result();
        String messageKey = result.messageKey();
        if (Texts.isNotBlank(messageKey)) {
            plugin.messageService().send(player, messageKey, result.placeholders());
        }
        if (result.success()) {
            if (inlayResult.updatedEquipment() != null && !inlayResult.updatedEquipment().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, inlayResult.updatedEquipment());
            }
            state.setTargetItem(null);
            state.clearPendingOperation();
            inlayResult.commit();
        } else {
            state.setTargetItemPreservingPending(inlayResult.updatedEquipment());
            if (result.inputConsumed()) {
                state.clearPendingOperation();
            }
        }
        renderer.refreshGui(state);
    }

    private void executeExtract(GemGuiSession state, GemGuiSession.PendingOperation pendingOperation, Player player, ItemStack targetItem) {
        GemInlayService.ExtractDirectResult extractResult = plugin.inlayService().extractDirect(
                player, targetItem, pendingOperation.slotIndex(), false);
        GemExtractService.Result result = extractResult.result();
        String messageKey = result.messageKey();
        if (Texts.isNotBlank(messageKey)) {
            plugin.messageService().send(player, messageKey, result.placeholders());
        }
        if (result.success()) {
            if (extractResult.updatedEquipment() != null && !extractResult.updatedEquipment().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, extractResult.updatedEquipment());
            }
            state.setTargetItem(null);
            if (extractResult.returnedGem() != null && !extractResult.returnedGem().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, extractResult.returnedGem());
            }
            extractResult.commit();
        } else {
            state.setTargetItem(extractResult.updatedEquipment());
        }
        state.clearPendingOperation();
        renderer.refreshGui(state);
    }

    private void returnPendingInput(GemGuiSession state) {
        if (state == null) {
            return;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        ItemStack inputItem = pendingOperation.inputItem();
        state.clearPendingOperation();
        if (inputItem != null && !inputItem.getType().isAir()) {
            InventoryItemUtil.giveOrDrop(state.player(), inputItem);
        }
    }

    private boolean isPendingInlaySlot(GemGuiSession state, int slotIndex) {
        if (state == null) {
            return false;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        return pendingOperation.type() == GemGuiSession.PendingType.INLAY
                && pendingOperation.slotIndex() == slotIndex
                && pendingOperation.inputItem() != null;
    }

    private void returnPendingInputToCursor(GemGuiSession state, GuiClickContext click) {
        if (state == null || click == null) {
            return;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        ItemStack inputItem = pendingOperation.inputItem();
        state.clearPendingOperation();
        if (inputItem != null && !inputItem.getType().isAir()) {
            click.setCursor(inputItem);
        }
    }

    private ItemStack consumeOneFromCursor(GuiClickContext click) {
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        if (cursorItem == null) {
            return null;
        }
        ItemStack taken = cursorItem.clone();
        taken.setAmount(1);
        if (cursorItem.getAmount() <= 1) {
            click.setCursor(null);
        } else {
            cursorItem.setAmount(cursorItem.getAmount() - 1);
            click.setCursor(cursorItem);
        }
        return taken;
    }

    private final class GemSessionHandler implements GuiSessionHandler {

        private final GemGuiSession state;

        private GemSessionHandler(GemGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (Texts.lower(slot.definition().type())) {
                case "target_item" -> handleTargetItemClick(state, click);
                case "mode_inlay" -> {
                    if (state.mode() != GemGuiMode.INLAY) {
                        returnPendingInput(state);
                        state.setMode(GemGuiMode.INLAY);
                    }
                    renderer.refreshGui(state);
                }
                case "mode_extract" -> {
                    if (state.mode() != GemGuiMode.EXTRACT) {
                        returnPendingInput(state);
                        state.setMode(GemGuiMode.EXTRACT);
                    }
                    renderer.refreshGui(state);
                }
                case "socket_slot" -> handleSocketClick(state, click, slot.slotIndex());
                case "confirm" -> handleConfirm(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
                return;
            }
            scheduleRefresh(state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            if (state.templateSwitching()) {
                state.setTemplateSwitching(false);
                return;
            }
            ItemStack cursorItem = close != null && close.player() != null
                    ? InventoryItemUtil.cloneNonAir(close.player().getItemOnCursor())
                    : null;
            ItemStack pendingInput = state.pendingOperation().inputItem();
            if (cursorItem != null) {
                close.player().setItemOnCursor(null);
            }
            if (state.mutableTargetItem() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), state.mutableTargetItem());
                state.setTargetItem(null);
            }
            if (pendingInput != null) {
                InventoryItemUtil.giveOrDrop(state.player(), pendingInput);
            }
            state.clearPendingOperation();
            if (cursorItem != null) {
                InventoryItemUtil.giveOrDrop(state.player(), cursorItem);
            }
            stateManager.remove(state);
        }
    }
}

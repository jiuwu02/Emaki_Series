package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
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
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemGuiInteractionController {

    private static final int HIDDEN_ORIGINAL_MAIN_HAND_KEY = -1;
    private static final int HIDDEN_ORIGINAL_OFF_HAND_KEY = -2;

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
        plugin.getServer().getScheduler().runTask(plugin, () -> renderer.refreshGui(state));
    }

    private void scheduleSwitchIfNeeded(GemGuiSession state) {
        if (state == null) {
            return;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GuiTemplate template = GemGuiTemplates.resolveGemTemplate(plugin.guiTemplateLoader(), itemDefinition);
        String resolvedId = template == null ? "" : template.id();
        if (!resolvedId.equals(state.currentTemplateId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!service.switchTemplate(state)) {
                    renderer.refreshGui(state);
                }
            });
            return;
        }
        renderer.refreshGui(state);
    }

    private void handleSocketClick(GemGuiSession state, InventoryClickEvent event, int displayIndex) {
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
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(event.getCursor());
        if (state.mode() == GemGuiMode.INLAY) {
            if (!currentState.isOpened(slotIndex)) {
                plugin.messageService().send(state.player(), "gui.gem.open_via_open_gui");
                return;
            }
            if (currentState.assignment(slotIndex) != null) {
                plugin.messageService().send(state.player(), "command.inlay.slot_occupied", Map.of("slot", slotIndex));
                return;
            }
            GemItemInstance instance = plugin.itemMatcher().readGemInstance(cursorItem);
            if (instance == null) {
                plugin.messageService().send(state.player(), "gui.gem.hold_gem");
                return;
            }
            state.setPendingOperation(new GemGuiSession.PendingOperation(
                    GemGuiSession.PendingType.INLAY,
                    slotIndex,
                    consumeOneFromCursor(event)
            ));
            renderer.refreshGui(state);
            return;
        }
        if (currentState.assignment(slotIndex) == null) {
            plugin.messageService().send(state.player(), "command.extract.slot_empty", Map.of("slot", slotIndex));
            return;
        }
        state.setPendingOperation(new GemGuiSession.PendingOperation(GemGuiSession.PendingType.EXTRACT, slotIndex, null));
        renderer.refreshGui(state);
    }

    private void handleTargetItemClick(GemGuiSession state, InventoryClickEvent event) {
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(event.getCursor());
        ItemStack targetItem = state.targetItem();
        if (cursorItem != null && plugin.stateService().resolveItemDefinition(cursorItem) == null) {
            plugin.messageService().send(state.player(), "gui.gem.invalid_target");
            return;
        }
        state.setTargetItem(cursorItem);
        event.getWhoClicked().setItemOnCursor(targetItem);
        scheduleSwitchIfNeeded(state);
    }

    private void handleConfirm(GemGuiSession state) {
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        if (!pendingOperation.active()) {
            plugin.messageService().send(state.player(), "gui.gem.no_pending_action");
            return;
        }
        OperationExecution execution = executePending(state, pendingOperation);
        String messageKey = messageKeyOf(execution.result());
        Map<String, Object> placeholders = placeholdersOf(execution.result());
        if (Texts.isNotBlank(messageKey)) {
            plugin.messageService().send(state.player(), messageKey, placeholders);
        }
        if (execution.success()) {
            if (execution.updatedTarget() != null && !execution.updatedTarget().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(state.player(), execution.updatedTarget());
            }
            state.setTargetItem(null);
        } else {
            if (execution.updatedTarget() != null && !execution.updatedTarget().getType().isAir()) {
                state.setTargetItem(execution.updatedTarget());
            } else if (execution.updatedTarget() == null || execution.updatedTarget().getType().isAir()) {
                state.setTargetItem(null);
            }
            if (pendingOperation.inputItem() != null && !inputConsumedOf(execution.result())) {
                InventoryItemUtil.giveOrDrop(state.player(), pendingOperation.inputItem());
            }
        }
        state.clearPendingOperation();
        renderer.refreshGui(state);
    }

    private OperationExecution executePending(GemGuiSession state, GemGuiSession.PendingOperation pendingOperation) {
        Player player = state.player();
        ItemStack targetItem = state.mutableTargetItem();
        if (player == null || targetItem == null) {
            return new OperationExecution(false, null, state.targetItem());
        }
        Map<Integer, ItemStack> hiddenHeldItems = buildHiddenHeldItems(player);
        return switch (pendingOperation.type()) {
            case INLAY -> {
                var hands = InventoryItemUtil.withTemporaryHands(
                        player, targetItem, pendingOperation.inputItem(),
                        () -> plugin.inlayService().inlay(player, player, pendingOperation.slotIndex(), false, hiddenHeldItems)
                );
                yield new OperationExecution(isSuccess(hands.result()), hands.result(), hands.updatedMainHand());
            }
            case EXTRACT -> {
                var hands = InventoryItemUtil.withTemporaryHands(
                        player, targetItem, null,
                        () -> plugin.extractService().extract(player, player, pendingOperation.slotIndex(), false)
                );
                yield new OperationExecution(isSuccess(hands.result()), hands.result(), hands.updatedMainHand());
            }
            default -> new OperationExecution(false, null, state.targetItem());
        };
    }

    private Map<Integer, ItemStack> buildHiddenHeldItems(Player player) {
        Map<Integer, ItemStack> hiddenHeldItems = new LinkedHashMap<>();
        ItemStack originalMainHand = InventoryItemUtil.cloneNonAir(player.getInventory().getItemInMainHand());
        ItemStack originalOffHand = InventoryItemUtil.cloneNonAir(player.getInventory().getItemInOffHand());
        if (originalMainHand != null) {
            hiddenHeldItems.put(HIDDEN_ORIGINAL_MAIN_HAND_KEY, originalMainHand);
        }
        if (originalOffHand != null) {
            hiddenHeldItems.put(HIDDEN_ORIGINAL_OFF_HAND_KEY, originalOffHand);
        }
        return hiddenHeldItems;
    }

    private boolean isSuccess(Object result) {
        if (result instanceof GemInlayService.Result inlayResult) {
            return inlayResult.success();
        }
        if (result instanceof GemExtractService.Result extractResult) {
            return extractResult.success();
        }
        return false;
    }

    private String messageKeyOf(Object result) {
        if (result instanceof GemInlayService.Result inlayResult) {
            return inlayResult.messageKey();
        }
        if (result instanceof GemExtractService.Result extractResult) {
            return extractResult.messageKey();
        }
        return "";
    }

    private Map<String, Object> placeholdersOf(Object result) {
        if (result instanceof GemInlayService.Result inlayResult) {
            return inlayResult.placeholders();
        }
        if (result instanceof GemExtractService.Result extractResult) {
            return extractResult.placeholders();
        }
        return Map.of();
    }

    private boolean inputConsumedOf(Object result) {
        if (result instanceof GemInlayService.Result inlayResult) {
            return inlayResult.inputConsumed();
        }
        return false;
    }

    private ItemStack consumeOneFromCursor(InventoryClickEvent event) {
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(event.getCursor());
        if (cursorItem == null) {
            return null;
        }
        ItemStack taken = cursorItem.clone();
        taken.setAmount(1);
        if (cursorItem.getAmount() <= 1) {
            event.getWhoClicked().setItemOnCursor(null);
        } else {
            cursorItem.setAmount(cursorItem.getAmount() - 1);
            event.getWhoClicked().setItemOnCursor(cursorItem);
        }
        return taken;
    }

    private final class GemSessionHandler implements GuiSessionHandler {

        private final GemGuiSession state;

        private GemSessionHandler(GemGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (Texts.lower(slot.definition().type())) {
                case "target_item" -> handleTargetItemClick(state, event);
                case "mode_inlay" -> {
                    state.setMode(GemGuiMode.INLAY);
                    renderer.refreshGui(state);
                }
                case "mode_extract" -> {
                    state.setMode(GemGuiMode.EXTRACT);
                    renderer.refreshGui(state);
                }
                case "socket_slot" -> handleSocketClick(state, event, slot.slotIndex());
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
            scheduleRefresh(state);
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            if (state.templateSwitching()) {
                state.setTemplateSwitching(false);
                return;
            }
            ItemStack cursorItem = event != null && event.getPlayer() != null
                    ? InventoryItemUtil.cloneNonAir(event.getPlayer().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                event.getPlayer().setItemOnCursor(null);
            }
            if (state.mutableTargetItem() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), state.mutableTargetItem());
                state.setTargetItem(null);
            }
            if (state.pendingOperation().inputItem() != null) {
                InventoryItemUtil.giveOrDrop(state.player(), state.pendingOperation().inputItem());
                state.clearPendingOperation();
            }
            if (cursorItem != null) {
                InventoryItemUtil.giveOrDrop(state.player(), cursorItem);
            }
            stateManager.remove(state);
        }
    }

    private record OperationExecution(boolean success, Object result, ItemStack updatedTarget) {
    }
}

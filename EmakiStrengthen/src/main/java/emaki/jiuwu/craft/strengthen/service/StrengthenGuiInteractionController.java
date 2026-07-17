package emaki.jiuwu.craft.strengthen.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;

final class StrengthenGuiInteractionController {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenGuiStateManager stateManager;
    private final StrengthenAttemptService attemptService;
    private final StrengthenGuiRenderer renderer;

    StrengthenGuiInteractionController(EmakiStrengthenPlugin plugin,
            StrengthenGuiStateManager stateManager,
            StrengthenAttemptService attemptService,
            StrengthenGuiRenderer renderer) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.attemptService = attemptService;
        this.renderer = renderer;
    }

    public GuiSessionHandler createSessionHandler(StrengthenGuiSession state) {
        return new StrengthenSessionHandler(state);
    }

    private void handleShiftFromPlayerInventory(GuiClickContext click, StrengthenGuiSession state) {
        ItemStack itemStack = StrengthenGuiSession.cloneNonAir(click.currentItem());
        if (itemStack == null) {
            return;
        }
        if (state.targetItem() == null && emaki.jiuwu.craft.corelib.text.Texts.isNotBlank(attemptService.readState(itemStack).baseSource())) {
            state.setTargetItem(itemStack);
            click.clearClickedSlot();
            renderer.refreshGui(state);
            return;
        }
        int slotIndex = state.firstEmptyMaterialSlot();
        if (slotIndex < 0) {
            return;
        }
        state.setMaterialInput(slotIndex, itemStack);
        click.clearClickedSlot();
        renderer.refreshGui(state);
    }

    private void handleSlotSwap(GuiClickContext click,
            StrengthenGuiSession state,
            java.util.function.Supplier<ItemStack> getter,
            java.util.function.Consumer<ItemStack> setter) {
        ItemStack cursor = StrengthenGuiSession.cloneNonAir(click.cursorItem());
        if (cursor != null) {
            ItemStack previous = StrengthenGuiSession.cloneNonAir(getter.get());
            setter.accept(cursor);
            click.setCursor(previous);
            renderer.refreshGui(state);
            return;
        }
        ItemStack removed = StrengthenGuiSession.cloneNonAir(getter.get());
        if (removed == null) {
            return;
        }
        setter.accept(null);
        if (click.isShiftClick()) {
            giveBackToPlayer(click.viewer(), removed);
        } else {
            click.setCursor(removed);
        }
        renderer.refreshGui(state);
    }

    private void handleMaterialSlotSwap(GuiClickContext click, StrengthenGuiSession state, int index) {
        handleSlotSwap(click, state, () -> state.materialInput(index), itemStack -> state.setMaterialInput(index, itemStack));
    }

    private void handleConfirm(StrengthenGuiSession state) {
        if (state.processing()) {
            return;
        }
        if (state.completionPhase().ordinal() >= StrengthenGuiSession.CompletionPhase.RESULT_DELIVERED.ordinal()) {
            returnAttemptLeftovers(state, state.preview());
            completeDeliveredEscrow(state);
            return;
        }
        if (!attemptService.accepting()) {
            return;
        }
        if (Texts.isBlank(state.operationId())) {
            state.setOperationId(java.util.UUID.randomUUID().toString());
        }
        state.setProcessing(true);
        try {
            AttemptResult result = attemptService.attempt(state.player(), state.toAttemptContext());
            ItemStack resultItem = result.resultItem();
            if (!result.committed() || resultItem == null) {
                plugin.messageService().send(state.player(), result.errorKey(), result.replacements());
                renderer.refreshGui(state);
                return;
            }

            state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.COMMITTED);
            state.setPreview(result.preview());
            giveBackToPlayer(state.player(), resultItem);
            state.clearTargetItem();
            state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.RESULT_DELIVERED);

            returnAttemptLeftovers(state, result.preview());
            completeDeliveredEscrow(state);

            if (result.preview() != null && result.preview().recipe() != null) {
                if (result.success()) {
                    attemptService.triggerSuccessActions(state.player(), result.preview().recipe(), "gui", resultItem,
                            result.resultingStar(), result.resultingCrack(), result.operationId());
                    attemptService.broadcastFirstReach(state.player(), resultItem, result.newlyReachedStars());
                } else {
                    attemptService.triggerFailureActions(
                            state.player(),
                            result.preview().recipe(),
                            "gui",
                            resultItem,
                            result.preview().currentStar(),
                            result.resultingStar(),
                            result.resultingCrack(),
                            result.resultingStar() < result.preview().currentStar(),
                            result.preview().protectionApplied(),
                            result.operationId()
                    );
                }
            }
            if (result.success()) {
                plugin.messageService().send(state.player(), "gui.attempt_success", Map.of("star", result.resultingStar()));
            } else if (result.resultingStar() < result.preview().currentStar()) {
                plugin.messageService().send(state.player(), "gui.attempt_failed_downgrade", Map.of("star", result.resultingStar()));
            } else {
                plugin.messageService().send(state.player(), "gui.attempt_failed", Map.of("star", result.resultingStar()));
            }
        } finally {
            state.setProcessing(false);
        }
    }

    private void giveBackToPlayer(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (!InventoryItemUtil.addOrDrop(player, itemStack).isEmpty()) {
            plugin.messageService().send(player, "gui.inventory_full");
        }
    }

    private void returnItems(StrengthenGuiSession state) {
        giveBackToPlayer(state.player(), state.targetItem());
        for (ItemStack itemStack : state.materialInputs()) {
            giveBackToPlayer(state.player(), itemStack);
        }
        state.clearStoredItems();
    }

    private void returnAttemptLeftovers(StrengthenGuiSession state, AttemptPreview preview) {
        if (state == null || preview == null) {
            return;
        }
        for (int index = 0; index < state.materialInputs().size(); index++) {
            ItemStack itemStack = state.materialInput(index);
            AttemptMaterial material = index < preview.optionalMaterials().size()
                    ? preview.optionalMaterials().get(index)
                    : null;
            int consumeAmount = material == null ? 0 : material.consumedAmount();
            returnRemaining(state.player(), itemStack, consumeAmount);
            state.setMaterialInput(index, null);
        }
    }

    private void completeDeliveredEscrow(StrengthenGuiSession state) {
        state.clearStoredItems();
        state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.ESCROW_CLEARED);
        state.setCompleted(true);
        state.player().closeInventory();
    }

    private void returnRemaining(Player player, ItemStack itemStack, int consumeAmount) {
        ItemStack clone = StrengthenGuiSession.cloneNonAir(itemStack);
        if (clone == null) {
            return;
        }
        int remaining = Math.max(0, clone.getAmount() - Math.max(0, consumeAmount));
        if (remaining <= 0) {
            return;
        }
        clone.setAmount(remaining);
        giveBackToPlayer(player, clone);
    }

    private final class StrengthenSessionHandler implements GuiSessionHandler {

        private final StrengthenGuiSession state;

        private StrengthenSessionHandler(StrengthenGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            String type = Texts.lower(slot.definition().type());
            switch (type) {
                case "target_item" -> handleSlotSwap(click, state, state::targetItem, state::setTargetItem);
                case "confirm" -> handleConfirm(state);
                default -> {
                    if (type.startsWith("material_input_")) {
                        int index = parseMaterialIndex(type);
                        if (index >= 0) {
                            handleMaterialSlotSwap(click, state, index);
                        }
                    }
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (!click.isShiftClick()) {
                return;
            }
            click.setCancelled(true);
            handleShiftFromPlayerInventory(click, state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            ItemStack cursorItem = close != null && close.player() != null
                    ? StrengthenGuiSession.cloneNonAir(close.player().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                close.player().setItemOnCursor(null);
            }
            stateManager.remove(state.player());
            if (!state.completed()) {
                returnItems(state);
            }
            if (cursorItem != null) {
                giveBackToPlayer(state.player(), cursorItem);
            }
        }
    }

    private static int parseMaterialIndex(String type) {
        try {
            return Integer.parseInt(type.substring("material_input_".length())) - 1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}

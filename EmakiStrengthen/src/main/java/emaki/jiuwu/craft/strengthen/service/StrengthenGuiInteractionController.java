package emaki.jiuwu.craft.strengthen.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;

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
            returnAttemptLeftovers(state.player(), state, state.preview());
            clearDeliveredEscrow(state);
            closeGuiIfCurrent(state.player(), state);
            return;
        }
        if (!attemptService.accepting()) {
            return;
        }
        if (Texts.isBlank(state.operationId())) {
            state.setOperationId(java.util.UUID.randomUUID().toString());
        }
        state.setProcessing(true);

        AttemptResult result;
        try {
            result = attemptService.attempt(state.player(), state.toAttemptContext());
        } catch (RuntimeException | LinkageError exception) {
            state.setProcessing(false);
            throw exception;
        }
        ItemStack resultItem = result.resultItem();
        if (!result.committed() || resultItem == null) {
            state.setProcessing(false);
            plugin.messageService().send(state.player(), result.errorKey(), result.replacements());
            renderer.refreshGui(state);
            return;
        }

        state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.COMMITTED);
        state.setPreview(result.preview());
        CoreActionItemTarget itemTarget = new CoreActionItemTarget(resultItem);
        StrengthenGuiStateManager.PendingSettlement pending = stateManager.addPendingSettlement(
                state.player(),
                result.operationId(),
                player -> completeCommittedAttempt(player, state, result, itemTarget)
        );
        CompletableFuture<?> actions;
        try {
            actions = triggerResultActions(state, result, itemTarget);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Strengthen result action dispatch failed | operationId="
                    + result.operationId() + " | error=" + exception.getMessage());
            pending.markReady();
            resumePendingSettlement(pending.player());
            return;
        }
        actions.whenComplete((_, _) -> {
            pending.markReady();
            resumePendingSettlement(pending.player());
        });
    }

    private CompletableFuture<?> triggerResultActions(StrengthenGuiSession state,
            AttemptResult result,
            CoreActionItemTarget itemTarget) {
        if (result.preview() == null || result.preview().recipe() == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (result.success()) {
            return attemptService.triggerSuccessActions(
                    state.player(),
                    result.preview().recipe(),
                    "gui",
                    itemTarget,
                    result.resultingStar(),
                    result.resultingCrack(),
                    result.operationId()
            );
        }
        return attemptService.triggerFailureActions(
                state.player(),
                result.preview().recipe(),
                "gui",
                itemTarget,
                result.preview().currentStar(),
                result.resultingStar(),
                result.resultingCrack(),
                result.resultingStar() < result.preview().currentStar(),
                result.preview().protectionApplied(),
                result.operationId()
        );
    }

    public boolean resumePendingSettlement(Player player) {
        StrengthenGuiStateManager.PendingSettlement pending = stateManager.pendingSettlement(player);
        if (pending == null || !pending.ready() || !pending.trySchedule()) {
            return pending != null;
        }
        try {
            var scheduled = plugin.executionDispatcher().runEntityLater(
                    plugin,
                    pending.player(),
                    () -> settlePendingOnOwner(pending),
                    () -> {
                        pending.releaseSchedule();
                        plugin.getLogger().warning("Strengthen committed result owner retired; settlement remains pending | operationId="
                                + pending.operationId());
                    },
                    1L
            );
            if (scheduled == null) {
                pending.releaseSchedule();
                plugin.getLogger().severe("Strengthen committed result scheduling was rejected; settlement remains pending | operationId="
                        + pending.operationId());
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError exception) {
            pending.releaseSchedule();
            plugin.getLogger().severe("Strengthen committed result scheduling failed; settlement remains pending | operationId="
                    + pending.operationId() + " | error=" + exception.getMessage());
            return false;
        }
    }

    private void settlePendingOnOwner(StrengthenGuiStateManager.PendingSettlement pending) {
        boolean completed = false;
        try {
            Player player = pending.player();
            if (player != null && player.isOnline()) {
                completed = pending.settle(player);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().severe("Strengthen committed result settlement failed and will be retried | operationId="
                    + pending.operationId() + " | error=" + exception.getMessage());
        } finally {
            pending.releaseSchedule();
            if (completed) {
                Player player = pending.player();
                stateManager.completePendingSettlement(pending);
                resumePendingSettlement(player);
            }
        }
    }

    private boolean completeCommittedAttempt(Player player,
            StrengthenGuiSession state,
            AttemptResult result,
            CoreActionItemTarget itemTarget) {
        if (state.completed()) {
            return true;
        }
        ItemStack resultItem = itemTarget.itemStack();
        if (state.completionPhase().ordinal() < StrengthenGuiSession.CompletionPhase.RESULT_DELIVERED.ordinal()) {
            giveBackToPlayer(player, resultItem);
            state.clearTargetItem();
            state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.RESULT_DELIVERED);
        }
        if (state.completionPhase().ordinal() < StrengthenGuiSession.CompletionPhase.ESCROW_CLEARED.ordinal()) {
            returnAttemptLeftovers(player, state, result.preview());
            clearDeliveredEscrow(state);
        }
        state.setProcessing(false);
        closeGuiIfCurrent(player, state);

        try {
            if (result.success()) {
                attemptService.broadcastFirstReach(player, resultItem, result.newlyReachedStars());
                plugin.messageService().send(player, "gui.attempt_success", Map.of("star", result.resultingStar()));
            } else if (result.preview() != null && result.resultingStar() < result.preview().currentStar()) {
                plugin.messageService().send(player, "gui.attempt_failed_downgrade", Map.of("star", result.resultingStar()));
            } else {
                plugin.messageService().send(player, "gui.attempt_failed", Map.of("star", result.resultingStar()));
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Strengthen committed result notification failed after settlement | operationId="
                    + result.operationId() + " | error=" + exception.getMessage());
        }
        return true;
    }

    private void giveBackToPlayer(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (!InventoryItemUtil.addOrDrop(player, itemStack).isEmpty()) {
            try {
                plugin.messageService().send(player, "gui.inventory_full");
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Strengthen inventory-full notification failed | error="
                        + exception.getMessage());
            }
        }
    }

    private void returnItems(StrengthenGuiSession state) {
        giveBackToPlayer(state.player(), state.targetItem());
        for (ItemStack itemStack : state.materialInputs()) {
            giveBackToPlayer(state.player(), itemStack);
        }
        state.clearStoredItems();
    }

    private void returnAttemptLeftovers(Player player, StrengthenGuiSession state, AttemptPreview preview) {
        if (state == null || preview == null) {
            return;
        }
        for (int index = 0; index < state.materialInputs().size(); index++) {
            ItemStack itemStack = state.materialInput(index);
            AttemptMaterial material = index < preview.optionalMaterials().size()
                    ? preview.optionalMaterials().get(index)
                    : null;
            int consumeAmount = material == null ? 0 : material.consumedAmount();
            returnRemaining(player, itemStack, consumeAmount);
            state.setMaterialInput(index, null);
        }
    }

    private void clearDeliveredEscrow(StrengthenGuiSession state) {
        state.clearStoredItems();
        state.advanceCompletionPhase(StrengthenGuiSession.CompletionPhase.ESCROW_CLEARED);
        state.setCompleted(true);
    }

    private void closeGuiIfCurrent(Player player, StrengthenGuiSession state) {
        if (player != null && stateManager.isCurrent(state)) {
            player.closeInventory();
        }
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
            if (state.processing()) {
                click.setCancelled(true);
                return;
            }
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
            if (state.processing()) {
                click.setCancelled(true);
                return;
            }
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
            stateManager.remove(state);
            switch (state.completionPhase()) {
                case OPEN, PROCESSING -> returnItems(state);
                case COMMITTED -> {

                }
                case RESULT_DELIVERED -> {
                    returnAttemptLeftovers(state.player(), state, state.preview());
                    clearDeliveredEscrow(state);
                    state.setProcessing(false);
                }
                case ESCROW_CLEARED, COMPLETED -> {

                }
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

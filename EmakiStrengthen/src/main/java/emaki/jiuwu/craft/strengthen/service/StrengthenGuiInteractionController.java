package emaki.jiuwu.craft.strengthen.service;

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
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
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

    private void handleShiftFromPlayerInventory(InventoryClickEvent event, StrengthenGuiSession state) {
        ItemStack itemStack = StrengthenGuiSession.cloneNonAir(event.getCurrentItem());
        if (itemStack == null) {
            return;
        }
        if (state.targetItem() == null && attemptService.readState(itemStack).baseSource() != null) {
            state.setTargetItem(itemStack);
            event.getClickedInventory().setItem(event.getSlot(), null);
            renderer.refreshGui(state);
            return;
        }
        int slotIndex = state.firstEmptyMaterialSlot();
        if (slotIndex < 0) {
            return;
        }
        state.setMaterialInput(slotIndex, itemStack);
        event.getClickedInventory().setItem(event.getSlot(), null);
        renderer.refreshGui(state);
    }

    private void handleSlotSwap(InventoryClickEvent event,
            StrengthenGuiSession state,
            java.util.function.Supplier<ItemStack> getter,
            java.util.function.Consumer<ItemStack> setter) {
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = StrengthenGuiSession.cloneNonAir(event.getCursor());
        if (cursor != null) {
            ItemStack previous = StrengthenGuiSession.cloneNonAir(getter.get());
            setter.accept(cursor);
            player.setItemOnCursor(previous);
            renderer.refreshGui(state);
            return;
        }
        ItemStack removed = StrengthenGuiSession.cloneNonAir(getter.get());
        if (removed == null) {
            return;
        }
        setter.accept(null);
        if (event.isShiftClick()) {
            giveBackToPlayer(player, removed);
        } else {
            player.setItemOnCursor(removed);
        }
        renderer.refreshGui(state);
    }

    private void handleMaterialSlotSwap(InventoryClickEvent event, StrengthenGuiSession state, int index) {
        handleSlotSwap(event, state, () -> state.materialInput(index), itemStack -> state.setMaterialInput(index, itemStack));
    }

    private void handleConfirm(StrengthenGuiSession state) {
        if (state.processing()) {
            return;
        }
        state.setProcessing(true);
        AttemptResult result = attemptService.attempt(state.player(), state.toAttemptContext());
        state.setProcessing(false);
        if (result.resultItem() == null) {
            plugin.messageService().send(state.player(), result.errorKey(), result.replacements());
            renderer.refreshGui(state);
            return;
        }
        state.setCompleted(true);
        returnAttemptLeftovers(state, result);
        state.clearStoredItems();
        state.player().closeInventory();
        giveBackToPlayer(state.player(), result.resultItem());
        if (result.preview() != null && result.preview().recipe() != null) {
            if (result.success()) {
                attemptService.triggerSuccessActions(state.player(), result.preview().recipe(), "gui", result.resultItem(), result.resultingStar(),
                        result.resultingCrack());
                attemptService.broadcastFirstReach(state.player(), result.resultItem(), result.newlyReachedStars());
            } else {
                attemptService.triggerFailureActions(
                        state.player(),
                        result.preview().recipe(),
                        "gui",
                        result.resultItem(),
                        result.preview().currentStar(),
                        result.resultingStar(),
                        result.resultingCrack(),
                        result.resultingStar() < result.preview().currentStar(),
                        result.preview().protectionApplied()
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

    private void returnAttemptLeftovers(StrengthenGuiSession state, AttemptResult result) {
        if (state == null || result == null || result.preview() == null) {
            return;
        }
        for (int index = 0; index < state.materialInputs().size(); index++) {
            ItemStack itemStack = state.materialInput(index);
            AttemptMaterial material = index < result.preview().optionalMaterials().size()
                    ? result.preview().optionalMaterials().get(index)
                    : null;
            int consumeAmount = material == null ? 0 : material.consumedAmount();
            returnRemaining(state.player(), itemStack, consumeAmount);
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
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            String type = Texts.lower(slot.definition().type());
            switch (type) {
                case "target_item" -> handleSlotSwap(event, state, state::targetItem, state::setTargetItem);
                case "confirm" -> handleConfirm(state);
                default -> {
                    if (type.startsWith("material_input_")) {
                        int index = parseMaterialIndex(type);
                        if (index >= 0) {
                            handleMaterialSlotSwap(event, state, index);
                        }
                    }
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            handleShiftFromPlayerInventory(event, state);
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            ItemStack cursorItem = event != null && event.getPlayer() != null
                    ? StrengthenGuiSession.cloneNonAir(event.getPlayer().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                event.getPlayer().setItemOnCursor(null);
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

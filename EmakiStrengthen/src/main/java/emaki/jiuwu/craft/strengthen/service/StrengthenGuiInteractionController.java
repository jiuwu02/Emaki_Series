package emaki.jiuwu.craft.strengthen.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenMaterial;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

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
        StrengthenMaterial material = attemptService.resolveConfiguredMaterial(itemStack);
        if (material == null) {
            return;
        }
        switch (material.role()) {
            case BASE -> {
                if (state.baseMaterial() == null) {
                    state.setBaseMaterial(itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                }
            }
            case SUPPORT -> {
                if (state.supportMaterial() == null) {
                    state.setSupportMaterial(itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                }
            }
            case PROTECTION -> {
                if (state.protectionMaterial() == null) {
                    state.setProtectionMaterial(itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                }
            }
            case BREAKTHROUGH -> {
                if (state.breakthroughMaterial() == null) {
                    state.setBreakthroughMaterial(itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                }
            }
            default -> {
            }
        }
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
        if (result.success()) {
            plugin.messageService().send(state.player(), "gui.attempt_success", Map.of("star", result.resultingStar()));
        } else if (result.resultingStar() < result.preview().currentStar()) {
            plugin.messageService().send(state.player(), "gui.attempt_failed_downgrade", Map.of("star", result.resultingStar()));
        } else {
            plugin.messageService().send(state.player(), "gui.attempt_failed", Map.of("star", result.resultingStar()));
        }
    }

    private void handleCleanse(StrengthenGuiSession state) {
        if (state.targetItem() == null) {
            plugin.messageService().send(state.player(), "gui.cleanse_no_target");
            return;
        }
        StrengthenState strengthenState = attemptService.readState(state.targetItem());
        if (strengthenState.crackLevel() <= 0) {
            plugin.messageService().send(state.player(), "gui.cleanse_no_crack");
            return;
        }
        ItemStack rebuilt = attemptService.consumeCleanseMaterial(state.player(), state.targetItem());
        if (rebuilt == null) {
            plugin.messageService().send(state.player(), "gui.cleanse_missing_material");
            return;
        }
        state.setTargetItem(rebuilt);
        plugin.messageService().send(state.player(), "gui.cleanse_success");
        renderer.refreshGui(state);
    }

    private void giveBackToPlayer(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        if (!leftover.isEmpty()) {
            plugin.messageService().send(player, "gui.inventory_full");
        }
    }

    private void returnItems(StrengthenGuiSession state) {
        giveBackToPlayer(state.player(), state.targetItem());
        giveBackToPlayer(state.player(), state.baseMaterial());
        giveBackToPlayer(state.player(), state.supportMaterial());
        giveBackToPlayer(state.player(), state.protectionMaterial());
        giveBackToPlayer(state.player(), state.breakthroughMaterial());
        state.clearStoredItems();
    }

    private void returnAttemptLeftovers(StrengthenGuiSession state, AttemptResult result) {
        if (state == null || result == null || result.preview() == null) {
            return;
        }
        returnRemaining(state.player(), state.baseMaterial(), result.preview().baseMaterial() == null ? 0 : 1);
        returnRemaining(state.player(), state.supportMaterial(), result.preview().supportMaterial() == null ? 0 : 1);
        returnRemaining(state.player(), state.breakthroughMaterial(), result.preview().breakthroughMaterial() == null ? 0 : 1);
        int protectionConsume = !result.success() && result.preview().protectionApplied() ? 1 : 0;
        returnRemaining(state.player(), state.protectionMaterial(), protectionConsume);
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
                case "base_material" -> handleSlotSwap(event, state, state::baseMaterial, state::setBaseMaterial);
                case "support_material" -> handleSlotSwap(event, state, state::supportMaterial, state::setSupportMaterial);
                case "protection_material" -> handleSlotSwap(event, state, state::protectionMaterial, state::setProtectionMaterial);
                case "breakthrough_material" -> handleSlotSwap(event, state, state::breakthroughMaterial, state::setBreakthroughMaterial);
                case "confirm" -> handleConfirm(state);
                case "crack_display" -> handleCleanse(state);
                default -> {
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
            stateManager.remove(state.player());
            if (!state.completed()) {
                returnItems(state);
            }
        }
    }
}

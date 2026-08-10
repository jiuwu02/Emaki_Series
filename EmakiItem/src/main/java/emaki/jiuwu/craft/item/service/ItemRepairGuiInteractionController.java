package emaki.jiuwu.craft.item.service;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

final class ItemRepairGuiInteractionController {

    private final EmakiItemPlugin plugin;
    private final ItemRepairGuiStateManager stateManager;
    private final ItemRepairService repairService;
    private final ItemRepairGuiRenderer renderer;

    ItemRepairGuiInteractionController(EmakiItemPlugin plugin,
            ItemRepairGuiStateManager stateManager,
            ItemRepairService repairService,
            ItemRepairGuiRenderer renderer) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.repairService = repairService;
        this.renderer = renderer;
    }

    public GuiSessionHandler createSessionHandler(ItemRepairGuiSession state) {
        return new RepairSessionHandler(state);
    }

    private void handleShiftFromPlayerInventory(GuiClickContext click, ItemRepairGuiSession state) {
        ItemStack itemStack = ItemRepairGuiSession.cloneNonAir(click.currentItem());
        if (itemStack == null) {
            return;
        }
        if (state.targetItem() == null && definition(itemStack) != null) {
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
            ItemRepairGuiSession state,
            Supplier<ItemStack> getter,
            Consumer<ItemStack> setter) {
        ItemStack cursor = ItemRepairGuiSession.cloneNonAir(click.cursorItem());
        if (cursor != null) {
            ItemStack previous = ItemRepairGuiSession.cloneNonAir(getter.get());
            setter.accept(cursor);
            click.setCursor(previous);
            renderer.refreshGui(state);
            return;
        }
        ItemStack removed = ItemRepairGuiSession.cloneNonAir(getter.get());
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

    private void handleMaterialSlotSwap(GuiClickContext click, ItemRepairGuiSession state, int index) {
        handleSlotSwap(click, state, () -> state.materialInput(index), itemStack -> state.setMaterialInput(index, itemStack));
    }

    private void handleMaterialRepair(ItemRepairGuiSession state) {
        if (state.processing()) {
            return;
        }
        EmakiItemDefinition definition = definition(state.targetItem());
        if (definition == null) {
            plugin.messageService().send(state.player(), "repair.error.invalid_item");
            renderer.refreshGui(state);
            return;
        }
        state.setProcessing(true);
        Map<Integer, ItemStack> materialSnapshot = state.materialInputMap();
        ItemRepairService.RepairResult result = repairService.repairWithMaterial(state.player(), definition, state.targetItem(), materialSnapshot);
        state.setProcessing(false);
        if (!result.success()) {
            plugin.messageService().send(state.player(), result.errorKey(), result.replacements());
            renderer.refreshGui(state);
            return;
        }
        finishRepair(state, result, materialSnapshot);
    }

    private void handleEconomyRepair(ItemRepairGuiSession state) {
        if (state.processing()) {
            return;
        }
        EmakiItemDefinition definition = definition(state.targetItem());
        if (definition == null) {
            plugin.messageService().send(state.player(), "repair.error.invalid_item");
            renderer.refreshGui(state);
            return;
        }
        state.setProcessing(true);
        ItemRepairService.RepairResult result = repairService.repairWithEconomy(state.player(), definition, state.targetItem());
        state.setProcessing(false);
        if (!result.success()) {
            plugin.messageService().send(state.player(), result.errorKey(), result.replacements());
            renderer.refreshGui(state);
            return;
        }
        finishRepair(state, result, state.materialInputMap());
    }

    private void finishRepair(ItemRepairGuiSession state,
            ItemRepairService.RepairResult result,
            Map<Integer, ItemStack> remainingMaterials) {
        state.setCompleted(true);
        ItemStack resultItem = ItemRepairGuiSession.cloneNonAir(state.targetItem());
        state.clearStoredItems();
        state.player().closeInventory();
        giveBackToPlayer(state.player(), resultItem);
        if (remainingMaterials != null) {
            for (ItemStack material : remainingMaterials.values()) {
                giveBackToPlayer(state.player(), material);
            }
        }
        plugin.messageService().send(state.player(), "repair.success", Map.of("restore", result.restoreAmount()));
        plugin.updateService().updatePlayerItems(state.player(), "repair");
        plugin.setService().refreshEquippedSets(state.player(), "repair");
        plugin.scheduleAttributeEquipmentSync(state.player());
    }

    private void returnItems(ItemRepairGuiSession state) {
        giveBackToPlayer(state.player(), state.targetItem());
        for (ItemStack itemStack : state.materialInputs()) {
            giveBackToPlayer(state.player(), itemStack);
        }
        state.clearStoredItems();
    }

    private void giveBackToPlayer(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (!InventoryItemUtil.addOrDrop(player, itemStack).isEmpty()) {
            plugin.messageService().send(player, "repair.inventory_full");
        }
    }

    private EmakiItemDefinition definition(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        String id = plugin.identifier().identify(itemStack);
        if (Texts.isBlank(id)) {
            return null;
        }
        EmakiItemDefinition definition = plugin.itemLoader().get(id);
        return definition != null && definition.repair().enabled() ? definition : null;
    }

    private final class RepairSessionHandler implements GuiSessionHandler {

        private final ItemRepairGuiSession state;

        private RepairSessionHandler(ItemRepairGuiSession state) {
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
                case "material_repair", "confirm" -> handleMaterialRepair(state);
                case "economy_repair" -> handleEconomyRepair(state);
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
                    ? ItemRepairGuiSession.cloneNonAir(close.player().getItemOnCursor())
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

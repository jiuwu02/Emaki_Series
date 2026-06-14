package emaki.jiuwu.craft.item.service;

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

    private void handleShiftFromPlayerInventory(InventoryClickEvent event, ItemRepairGuiSession state) {
        ItemStack itemStack = ItemRepairGuiSession.cloneNonAir(event.getCurrentItem());
        if (itemStack == null) {
            return;
        }
        if (state.targetItem() == null && definition(itemStack) != null) {
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
            ItemRepairGuiSession state,
            java.util.function.Supplier<ItemStack> getter,
            java.util.function.Consumer<ItemStack> setter) {
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = ItemRepairGuiSession.cloneNonAir(event.getCursor());
        if (cursor != null) {
            ItemStack previous = ItemRepairGuiSession.cloneNonAir(getter.get());
            setter.accept(cursor);
            player.setItemOnCursor(previous);
            renderer.refreshGui(state);
            return;
        }
        ItemStack removed = ItemRepairGuiSession.cloneNonAir(getter.get());
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

    private void handleMaterialSlotSwap(InventoryClickEvent event, ItemRepairGuiSession state, int index) {
        handleSlotSwap(event, state, () -> state.materialInput(index), itemStack -> state.setMaterialInput(index, itemStack));
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
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            String type = Texts.lower(slot.definition().type());
            switch (type) {
                case "target_item" -> handleSlotSwap(event, state, state::targetItem, state::setTargetItem);
                case "material_repair", "confirm" -> handleMaterialRepair(state);
                case "economy_repair" -> handleEconomyRepair(state);
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
                    ? ItemRepairGuiSession.cloneNonAir(event.getPlayer().getItemOnCursor())
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

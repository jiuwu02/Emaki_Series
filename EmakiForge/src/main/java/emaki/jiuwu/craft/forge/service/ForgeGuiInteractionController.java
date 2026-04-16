package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;

final class ForgeGuiInteractionController {

    private final EmakiForgePlugin plugin;
    private final GuiStateManager stateManager;
    private final ForgeGuiStateSupport stateSupport;
    private final ForgeGuiRenderer renderer;

    ForgeGuiInteractionController(EmakiForgePlugin plugin,
            GuiStateManager stateManager,
            ForgeGuiStateSupport stateSupport,
            ForgeGuiRenderer renderer) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.stateSupport = stateSupport;
        this.renderer = renderer;
    }

    public GuiSessionHandler createSessionHandler(ForgeGuiSession state) {
        return new ForgeSessionHandler(state);
    }

    private void handleShiftFromPlayerInventory(InventoryClickEvent event, ForgeGuiSession state) {
        ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(event.getCurrentItem());
        if (itemStack == null) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        if (source == null) {
            return;
        }
        if (stateSupport.findBlueprintRequirementBySource(source) != null) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "blueprint_inputs"), state.blueprintItems());
            if (slot >= 0) {
                state.blueprintItems().put(slot, itemStack);
                event.getClickedInventory().setItem(event.getSlot(), null);
                renderer.refreshGui(state);
            }
            return;
        }
        ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
        String materialId = materialKey(source);
        if (rules.requiredIds().contains(materialId)) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "required_materials"), state.requiredMaterialItems());
            if (slot >= 0) {
                state.requiredMaterialItems().put(slot, itemStack);
                event.getClickedInventory().setItem(event.getSlot(), null);
                renderer.refreshGui(state);
            }
            return;
        }
        if (stateSupport.canPlaceOptionalMaterial(materialId, rules, state.optionalMaterialItems().size())) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "optional_materials"), state.optionalMaterialItems());
            if (slot >= 0) {
                state.optionalMaterialItems().put(slot, itemStack);
                event.getClickedInventory().setItem(event.getSlot(), null);
                renderer.refreshGui(state);
            }
        }
    }

    private void handleBlueprintClick(InventoryClickEvent event, ForgeGuiSession state, int slot) {
        handleMappedSlotClick(
                event,
                state,
                slot,
                state.blueprintItems(),
                itemStack -> stateSupport.findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null
        );
    }

    private void handleMaterialClick(InventoryClickEvent event, ForgeGuiSession state, int slot, boolean required) {
        ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
        int optionalOccupied = state.optionalMaterialItems().size() - (state.optionalMaterialItems().containsKey(slot) ? 1 : 0);
        handleMappedSlotClick(
                event,
                state,
                slot,
                required ? state.requiredMaterialItems() : state.optionalMaterialItems(),
                itemStack -> {
                    ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
                    String materialId = materialKey(source);
                    if (Texts.isBlank(materialId)) {
                        return false;
                    }
                    return required
                            ? rules.requiredIds().contains(materialId)
                            : stateSupport.canPlaceOptionalMaterial(materialId, rules, Math.max(0, optionalOccupied));
                }
        );
    }

    private void handleMappedSlotClick(InventoryClickEvent event,
            ForgeGuiSession state,
            int slot,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (isUnsupportedKeyboardClick(event)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack heldItem = resolveHeldItem(player, event);
        if (heldItem != null) {
            if (validator != null && !validator.test(heldItem)) {
                return;
            }
            ItemStack previous = ForgeGuiStateSupport.cloneNonAir(items.put(slot, heldItem));
            setHeldItem(player, event, previous);
            renderer.refreshGui(state);
            return;
        }
        ItemStack removed = ForgeGuiStateSupport.cloneNonAir(items.remove(slot));
        if (removed == null) {
            return;
        }
        if (event.isShiftClick()) {
            stateSupport.giveBackToPlayer(player, removed);
        } else {
            setHeldItem(player, event, removed);
        }
        renderer.refreshGui(state);
    }

    private void handleConfirmClick(ForgeGuiSession state) {
        if (state.processing()) {
            return;
        }
        stateSupport.refreshDerivedValues(state);
        if (state.maxCapacity() > 0 && state.currentCapacity() > state.maxCapacity()) {
            plugin.messageService().send(
                    state.player(),
                    "forge.error.capacity_exceeded",
                    Map.of("current", state.currentCapacity(), "max", state.maxCapacity())
            );
            return;
        }
        Recipe activeRecipe = state.recipe() != null ? state.recipe() : state.previewRecipe();
        if (activeRecipe == null) {
            RecipeMatch match = plugin.forgeService().findMatchingRecipe(state.player(), state.toGuiItems());
            if (match.recipe() == null) {
                plugin.messageService().send(state.player(), match.errorKey(), match.replacements());
                return;
            }
            activeRecipe = match.recipe();
        }
        Recipe finalRecipe = activeRecipe;
        GuiItems snapshot = state.toGuiItems();
        ForgeService.PreparedForge preparedForge = state.preparedForge();
        if (preparedForge == null) {
            preparedForge = plugin.forgeService().prepareForge(
                    state.player(),
                    finalRecipe,
                    snapshot,
                    state.previewSeed(),
                    state.previewForgedAt()
            );
            state.setPreparedForge(preparedForge);
        }
        if (preparedForge == null || preparedForge.request() == null) {
            plugin.messageService().send(state.player(), "forge.error.item_create");
            return;
        }
        boolean firstCraft = !plugin.playerDataStore().hasCrafted(state.player().getUniqueId(), activeRecipe.id());
        state.setProcessing(true);
        state.setRecipe(finalRecipe);
        state.setPreviewRecipe(finalRecipe);
        state.player().closeInventory();
        plugin.forgeService().executeForgeAsync(
                state.player(),
                finalRecipe,
                snapshot,
                preparedForge
        ).whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(
                plugin,
                () -> completeForgeAttempt(state, finalRecipe, firstCraft, result, throwable)
        ));
    }

    private void completeForgeAttempt(ForgeGuiSession state,
            Recipe activeRecipe,
            boolean firstCraft,
            ForgeResult result,
            Throwable throwable) {
        state.setProcessing(false);
        stateManager.remove(state);
        if (throwable != null) {
            plugin.messageService().warning("console.forge_execution_failed", Map.of(
                    "recipe", activeRecipe.id(),
                    "error", String.valueOf(throwable.getMessage())
            ));
            returnFailedAttempt(state, "forge.error.action_failed", Map.of("reason", Texts.toStringSafe(throwable.getMessage())));
            return;
        }
        if (result == null || !result.success()) {
            String errorKey = result == null || Texts.isBlank(result.errorKey()) ? "forge.error.action_failed" : result.errorKey();
            Map<String, Object> replacements = result == null || result.replacements() == null ? Map.of() : result.replacements();
            returnFailedAttempt(state, errorKey, replacements);
            return;
        }
        state.setForgeCompleted(true);
        stateSupport.returnUnusedInputs(state, activeRecipe);
        state.clearStoredItems();
        if (Texts.isNotBlank(result.quality())) {
            plugin.messageService().send(
                    state.player(),
                    "forge.success.quality",
                    Map.of("quality", result.quality(), "multiplier", result.multiplier())
            );
        }
        if (firstCraft) {
            plugin.messageService().sendRaw(state.player(), "<green>首次完成该配方锻造!</green>");
        }
    }

    private void handleSingleSlotDrag(InventoryDragEvent event, ForgeGuiSession state) {
        if (event == null || state == null || state.guiSession() == null) {
            return;
        }
        if (event.getNewItems().isEmpty() || event.getRawSlots().isEmpty()) {
            return;
        }
        int topSize = state.guiSession().getInventory().getSize();
        Integer rawSlot = event.getRawSlots().stream()
                .filter(slot -> slot != null && slot >= 0 && slot < topSize)
                .findFirst()
                .orElse(null);
        if (rawSlot == null || event.getRawSlots().size() != 1 || event.getNewItems().size() != 1) {
            return;
        }
        GuiTemplate.ResolvedSlot slot = state.guiSession().template().resolvedSlotAt(rawSlot);
        if (slot == null || slot.definition() == null) {
            return;
        }
        ItemStack placedItem = ForgeGuiStateSupport.cloneNonAir(event.getNewItems().get(rawSlot));
        if (placedItem == null) {
            return;
        }
        switch (stateSupport.normalizedType(slot.definition())) {
            case "blueprint_inputs" ->
                handleDragPlacement(event, state, rawSlot, placedItem, state.blueprintItems(),
                        itemStack -> stateSupport.findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null);
            case "required_materials" ->
                handleDragPlacement(event, state, rawSlot, placedItem, state.requiredMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            String materialId = materialKey(plugin.itemIdentifierService().identifyItem(itemStack));
                            return Texts.isNotBlank(materialId) && rules.requiredIds().contains(materialId);
                        });
            case "optional_materials" ->
                handleDragPlacement(event, state, rawSlot, placedItem, state.optionalMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            int occupied = state.optionalMaterialItems().containsKey(rawSlot)
                                    ? state.optionalMaterialItems().size() - 1
                                    : state.optionalMaterialItems().size();
                            String materialId = materialKey(plugin.itemIdentifierService().identifyItem(itemStack));
                            return stateSupport.canPlaceOptionalMaterial(materialId, rules, Math.max(0, occupied));
                        });
            default -> {
            }
        }
    }

    private void handleDragPlacement(InventoryDragEvent event,
            ForgeGuiSession state,
            int slot,
            ItemStack placedItem,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (event == null || state == null || placedItem == null || items == null) {
            return;
        }
        if (items.containsKey(slot)) {
            return;
        }
        if (validator != null && !validator.test(placedItem)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        items.put(slot, placedItem);
        ItemStack cursor = ForgeGuiStateSupport.cloneNonAir(event.getOldCursor());
        if (cursor != null) {
            int remaining = Math.max(0, cursor.getAmount() - placedItem.getAmount());
            if (remaining <= 0) {
                player.setItemOnCursor(null);
            } else {
                cursor.setAmount(remaining);
                player.setItemOnCursor(cursor);
            }
        }
        renderer.refreshGui(state);
    }

    private void returnFailedAttempt(ForgeGuiSession state, String errorKey, Map<String, ?> replacements) {
        plugin.messageService().send(state.player(), errorKey, replacements == null ? Map.of() : replacements);
        stateSupport.returnItems(state);
    }

    private String materialKey(ItemSource source) {
        return source == null || plugin.forgeService() == null || plugin.forgeService().findMaterialBySource(source) == null
                ? ""
                : plugin.forgeService().findMaterialBySource(source).key();
    }

    private boolean isUnsupportedKeyboardClick(InventoryClickEvent event) {
        if (event == null || !event.getClick().isKeyboardClick()) {
            return false;
        }
        return event.getClick() != ClickType.NUMBER_KEY && event.getClick() != ClickType.SWAP_OFFHAND;
    }

    private ItemStack resolveHeldItem(Player player, InventoryClickEvent event) {
        if (player == null || event == null) {
            return null;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton < 0 || hotbarButton >= 9) {
                return null;
            }
            return ForgeGuiStateSupport.cloneNonAir(player.getInventory().getItem(hotbarButton));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return ForgeGuiStateSupport.cloneNonAir(player.getInventory().getItemInOffHand());
        }
        return ForgeGuiStateSupport.cloneNonAir(event.getCursor());
    }

    private void setHeldItem(Player player, InventoryClickEvent event, ItemStack itemStack) {
        if (player == null || event == null) {
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < 9) {
                player.getInventory().setItem(hotbarButton, ForgeGuiStateSupport.cloneNonAir(itemStack));
            }
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            player.getInventory().setItemInOffHand(ForgeGuiStateSupport.cloneNonAir(itemStack));
            return;
        }
        player.setItemOnCursor(ForgeGuiStateSupport.cloneNonAir(itemStack));
    }

    private boolean shouldBlockPlayerInventoryTransfer(InventoryClickEvent event) {
        if (event == null) {
            return false;
        }
        return event.isShiftClick()
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == ClickType.DOUBLE_CLICK;
    }

    private final class ForgeSessionHandler implements GuiSessionHandler {

        private final ForgeGuiSession state;

        private ForgeSessionHandler(ForgeGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (state.processing()) {
                event.setCancelled(true);
                return;
            }
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (stateSupport.normalizedType(slot.definition())) {
                case "blueprint_inputs" ->
                    handleBlueprintClick(event, state, slot.inventorySlot());
                case "required_materials" ->
                    handleMaterialClick(event, state, slot.inventorySlot(), true);
                case "optional_materials" ->
                    handleMaterialClick(event, state, slot.inventorySlot(), false);
                case "confirm" ->
                    handleConfirmClick(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
            if (state.processing()) {
                event.setCancelled(true);
                return;
            }
            if (!shouldBlockPlayerInventoryTransfer(event)) {
                return;
            }
            event.setCancelled(true);
            if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                handleShiftFromPlayerInventory(event, state);
            }
        }

        @Override
        public void onDrag(GuiSession session, InventoryDragEvent event) {
            if (state.processing()) {
                return;
            }
            handleSingleSlotDrag(event, state);
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            if (state.processing()) {
                return;
            }
            stateManager.remove(state);
            if (!state.forgeCompleted()) {
                stateSupport.returnItems(state);
            }
        }
    }
}

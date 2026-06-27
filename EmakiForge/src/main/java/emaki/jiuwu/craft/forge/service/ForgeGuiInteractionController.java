package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.event.ForgeCompletedEvent;
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

    private void handleShiftFromPlayerInventory(GuiClickContext click, ForgeGuiSession state) {
        ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(click.currentItem());
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
                click.clearClickedSlot();
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
                click.clearClickedSlot();
                renderer.refreshGui(state);
            }
            return;
        }
        if (stateSupport.canPlaceOptionalMaterial(materialId, rules, state.optionalMaterialItems().size())) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "optional_materials"), state.optionalMaterialItems());
            if (slot >= 0) {
                state.optionalMaterialItems().put(slot, itemStack);
                click.clearClickedSlot();
                renderer.refreshGui(state);
            }
        }
    }

    private void handleBlueprintClick(GuiClickContext click, ForgeGuiSession state, int slot) {
        handleMappedSlotClick(
                click,
                state,
                slot,
                state.blueprintItems(),
                itemStack -> stateSupport.findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null
        );
    }

    private void handleMaterialClick(GuiClickContext click, ForgeGuiSession state, int slot, boolean required) {
        ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
        int optionalOccupied = state.optionalMaterialItems().size() - (state.optionalMaterialItems().containsKey(slot) ? 1 : 0);
        handleMappedSlotClick(
                click,
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

    private void handleMappedSlotClick(GuiClickContext click,
            ForgeGuiSession state,
            int slot,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (click.isUnsupportedKeyboardClick()) {
            return;
        }
        ItemStack heldItem = ForgeGuiStateSupport.cloneNonAir(click.heldItem());
        if (heldItem != null) {
            if (validator != null && !validator.test(heldItem)) {
                return;
            }
            ItemStack previous = ForgeGuiStateSupport.cloneNonAir(items.put(slot, heldItem));
            click.setHeldItem(previous);
            renderer.refreshGui(state);
            return;
        }
        if (click.isShiftClick()) {
            return;
        }
        ItemStack removed = ForgeGuiStateSupport.cloneNonAir(items.remove(slot));
        if (removed == null) {
            return;
        }
        click.setHeldItem(removed);
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
        ).whenComplete((result, throwable) -> FoliaSchedulerAdapter.runEntityTask(
                plugin,
                state.player(),
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
            fireForgeCompleted(state.player(), activeRecipe, result, false);
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
            plugin.messageService().send(state.player(), "forge.success.first_craft");
        }
        fireForgeCompleted(state.player(), activeRecipe, result, true);
    }

    private void fireForgeCompleted(Player player, Recipe recipe, ForgeResult result, boolean success) {
        if (player == null || recipe == null || !org.bukkit.Bukkit.isPrimaryThread()) {
            return;
        }
        org.bukkit.Bukkit.getPluginManager().callEvent(new ForgeCompletedEvent(
                player,
                recipe.id(),
                success,
                result == null ? null : result.resultItem(),
                result == null ? null : result.quality(),
                result == null ? 1D : result.multiplier()));
    }

    private void handleSingleSlotDrag(GuiDragContext drag, ForgeGuiSession state) {
        if (drag == null || state == null || state.guiSession() == null) {
            return;
        }
        if (drag.newItems().isEmpty() || drag.rawSlots().isEmpty()) {
            return;
        }
        int topSize = state.guiSession().getInventory().getSize();
        Integer rawSlot = drag.rawSlots().stream()
                .filter(slot -> slot != null && slot >= 0 && slot < topSize)
                .findFirst()
                .orElse(null);
        if (rawSlot == null || drag.rawSlots().size() != 1 || drag.newItems().size() != 1) {
            return;
        }
        GuiTemplate.ResolvedSlot slot = state.guiSession().template().resolvedSlotAt(rawSlot);
        if (slot == null || slot.definition() == null) {
            return;
        }
        ItemStack placedItem = ForgeGuiStateSupport.cloneNonAir(drag.newItems().get(rawSlot));
        if (placedItem == null) {
            return;
        }
        switch (stateSupport.normalizedType(slot.definition())) {
            case "blueprint_inputs" ->
                handleDragPlacement(drag, state, rawSlot, placedItem, state.blueprintItems(),
                        itemStack -> stateSupport.findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null);
            case "required_materials" ->
                handleDragPlacement(drag, state, rawSlot, placedItem, state.requiredMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            String materialId = materialKey(plugin.itemIdentifierService().identifyItem(itemStack));
                            return Texts.isNotBlank(materialId) && rules.requiredIds().contains(materialId);
                        });
            case "optional_materials" ->
                handleDragPlacement(drag, state, rawSlot, placedItem, state.optionalMaterialItems(),
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

    private void handleDragPlacement(GuiDragContext drag,
            ForgeGuiSession state,
            int slot,
            ItemStack placedItem,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (drag == null || state == null || placedItem == null || items == null) {
            return;
        }
        if (items.containsKey(slot)) {
            return;
        }
        if (validator != null && !validator.test(placedItem)) {
            return;
        }
        items.put(slot, placedItem);
        ItemStack cursor = ForgeGuiStateSupport.cloneNonAir(drag.oldCursor());
        if (cursor != null) {
            int remaining = Math.max(0, cursor.getAmount() - placedItem.getAmount());
            if (remaining <= 0) {
                drag.setCursor(null);
            } else {
                cursor.setAmount(remaining);
                drag.setCursor(cursor);
            }
        }
        renderer.refreshGui(state);
    }

    private void returnFailedAttempt(ForgeGuiSession state, String errorKey, Map<String, ?> replacements) {
        plugin.messageService().send(state.player(), errorKey, replacements == null ? Map.of() : replacements);
        stateSupport.returnItems(state);
    }

    private String materialKey(ItemSource source) {
        if (source == null || plugin.forgeService() == null) {
            return "";
        }
        var material = plugin.forgeService().findMaterialBySource(source);
        return material == null ? "" : material.key();
    }

    private final class ForgeSessionHandler implements GuiSessionHandler {

        private final ForgeGuiSession state;

        private ForgeSessionHandler(ForgeGuiSession state) {
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
            switch (stateSupport.normalizedType(slot.definition())) {
                case "blueprint_inputs" ->
                    handleBlueprintClick(click, state, slot.inventorySlot());
                case "required_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), true);
                case "optional_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), false);
                case "confirm" ->
                    handleConfirmClick(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (state.processing()) {
                click.setCancelled(true);
                return;
            }
            if (!click.isBlockedTransfer()) {
                return;
            }
            click.setCancelled(true);
            if (click.isMoveToOtherInventory()) {
                handleShiftFromPlayerInventory(click, state);
            }
        }

        @Override
        public void onDrag(GuiSession session, GuiDragContext drag) {
            if (state.processing()) {
                return;
            }
            handleSingleSlotDrag(drag, state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            if (state.processing()) {
                return;
            }
            ItemStack cursorItem = close != null && close.player() != null
                    ? ForgeGuiStateSupport.cloneNonAir(close.player().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                close.player().setItemOnCursor(null);
            }
            stateManager.remove(state);
            if (!state.forgeCompleted()) {
                stateSupport.returnItems(state);
            }
            if (cursorItem != null) {
                stateSupport.giveBackToPlayer(state.player(), cursorItem);
            }
        }
    }
}

package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiDebugSupport;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.event.ForgeCompletedEvent;
import emaki.jiuwu.craft.forge.api.event.ForgeStartEvent;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;

final class ForgeGuiInteractionController {

    private final EmakiForgePlugin plugin;
    private final GuiStateManager stateManager;
    private final ForgeGuiStateSupport stateSupport;
    private final ForgeGuiRenderer renderer;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;

    ForgeGuiInteractionController(EmakiForgePlugin plugin,
            GuiStateManager stateManager,
            ForgeGuiStateSupport stateSupport,
            ForgeGuiRenderer renderer,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.stateSupport = stateSupport;
        this.renderer = renderer;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }

    public GuiSessionHandler createSessionHandler(ForgeGuiSession state) {
        return new ForgeSessionHandler(state);
    }

    private void handleShiftFromPlayerInventory(GuiClickContext click, ForgeGuiSession state) {
        ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(click.currentItem());
        if (itemStack == null) {
            debug(state.player(), "shift transfer ignored: current=empty");
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        if (source == null) {
            debug(state.player(), "shift transfer rejected: source=unidentified item=" + describe(itemStack));
            return;
        }
        if (stateSupport.findBlueprintRequirementBySource(source) != null) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "blueprint_inputs"), state.blueprintItems());
            if (slot >= 0) {
                state.blueprintItems().put(slot, itemStack);
                click.clearClickedSlot();
                renderer.refreshGui(state);
                debug(state.player(), "shift transfer accepted: slotType=blueprint_inputs slot=" + slot + " item=" + describe(itemStack));
            } else {
                debug(state.player(), "shift transfer rejected: slotType=blueprint_inputs reason=no_free_slot item=" + describe(itemStack));
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
                debug(state.player(), "shift transfer accepted: slotType=required_materials slot=" + slot
                        + " material=" + materialId + " item=" + describe(itemStack));
            } else {
                debug(state.player(), "shift transfer rejected: slotType=required_materials reason=no_free_slot material="
                        + materialId + " item=" + describe(itemStack));
            }
            return;
        }
        if (stateSupport.canPlaceOptionalMaterial(materialId, rules, state.optionalMaterialItems().size())) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "optional_materials"), state.optionalMaterialItems());
            if (slot >= 0) {
                state.optionalMaterialItems().put(slot, itemStack);
                click.clearClickedSlot();
                renderer.refreshGui(state);
                debug(state.player(), "shift transfer accepted: slotType=optional_materials slot=" + slot
                        + " material=" + materialId + " item=" + describe(itemStack));
            } else {
                debug(state.player(), "shift transfer rejected: slotType=optional_materials reason=no_free_slot material="
                        + materialId + " item=" + describe(itemStack));
            }
            return;
        }
        debug(state.player(), "shift transfer rejected: reason=validation material=" + materialId + " item=" + describe(itemStack));
    }

    private void handleBlueprintClick(GuiClickContext click, ForgeGuiSession state, int slot) {
        handleMappedSlotClick(
                click,
                state,
                slot,
                "blueprint_inputs",
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
                required ? "required_materials" : "optional_materials",
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
            String slotType,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (click.isUnsupportedKeyboardClick()) {
            debug(state.player(), "slot click rejected: slotType=" + slotType + " slot=" + slot
                    + " reason=unsupported_keyboard click=" + click.clickType());
            return;
        }
        ItemStack heldItem = ForgeGuiStateSupport.cloneNonAir(click.heldItem());
        if (heldItem != null) {
            if (validator != null && !validator.test(heldItem)) {
                debug(state.player(), "slot placement rejected: slotType=" + slotType + " slot=" + slot
                        + " reason=validation item=" + describe(heldItem));
                return;
            }
            ItemStack previous = ForgeGuiStateSupport.cloneNonAir(items.put(slot, heldItem));
            click.setHeldItem(previous);
            renderer.refreshGui(state);
            debug(state.player(), "slot placement accepted: slotType=" + slotType + " slot=" + slot
                    + " item=" + describe(heldItem) + " replaced=" + describe(previous));
            return;
        }
        if (click.isShiftClick()) {
            debug(state.player(), "slot removal ignored: slotType=" + slotType + " slot=" + slot + " reason=shift_click");
            return;
        }
        ItemStack removed = ForgeGuiStateSupport.cloneNonAir(items.remove(slot));
        if (removed == null) {
            debug(state.player(), "slot removal ignored: slotType=" + slotType + " slot=" + slot + " reason=empty");
            return;
        }
        click.setHeldItem(removed);
        renderer.refreshGui(state);
        debug(state.player(), "slot removal completed: slotType=" + slotType + " slot=" + slot + " item=" + describe(removed));
    }

    private void handleConfirmClick(ForgeGuiSession state) {
        if (state.processing()) {
            debug(state.player(), "confirm rejected: reason=processing");
            return;
        }
        stateSupport.refreshDerivedValues(state);
        debug(state.player(), "confirm requested: capacity=" + state.currentCapacity() + "/" + state.maxCapacity()
                + " blueprints=" + state.blueprintItems().size() + " required=" + state.requiredMaterialItems().size()
                + " optional=" + state.optionalMaterialItems().size());
        if (state.maxCapacity() > 0 && state.currentCapacity() > state.maxCapacity()) {
            debug(state.player(), "confirm rejected: reason=capacity_exceeded current=" + state.currentCapacity()
                    + " max=" + state.maxCapacity());
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
                debug(state.player(), "confirm rejected: reason=recipe_mismatch errorKey=" + match.errorKey());
                plugin.messageService().send(state.player(), match.errorKey(), match.replacements());
                return;
            }
            activeRecipe = match.recipe();
            debug(state.player(), "confirm recipe resolved: recipe=" + activeRecipe.id());
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
            debug(state.player(), "confirm rejected: reason=prepare_failed recipe=" + finalRecipe.id());
            plugin.messageService().send(state.player(), "forge.error.item_create");
            return;
        }
        boolean firstCraft = !plugin.playerDataStore().hasCrafted(state.player().getUniqueId(), activeRecipe.id());
        if (threadOwnership == null || !threadOwnership.isEntityOwned(state.player())) {
            debug(state.player(), "confirm rejected: reason=player_owner_unavailable recipe=" + finalRecipe.id());
            plugin.messageService().send(state.player(), "forge.error.action_failed", Map.of("reason", "player owner is unavailable"));
            return;
        }
        ForgeStartEvent startEvent = new ForgeStartEvent(state.player(), finalRecipe.id(), firstCraft, finalRecipe.successRate());
        org.bukkit.Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            debug(state.player(), "confirm rejected: reason=start_event_cancelled recipe=" + finalRecipe.id());
            return;
        }
        debug(state.player(), "processing started: recipe=" + finalRecipe.id() + " firstCraft=" + firstCraft
                + " successRate=" + finalRecipe.successRate());
        state.setProcessing(true);
        state.setRecipe(finalRecipe);
        state.setPreviewRecipe(finalRecipe);
        state.player().closeInventory();
        plugin.forgeService().executeForgeAsync(
                state.player(),
                finalRecipe,
                snapshot,
                preparedForge
        ).whenComplete((result, throwable) -> completeForgeAttemptOnOwner(
                state,
                finalRecipe,
                firstCraft,
                result,
                throwable
        ));
    }

    private void completeForgeAttemptOnOwner(ForgeGuiSession state,
            Recipe activeRecipe,
            boolean firstCraft,
            ForgeResult result,
            Throwable throwable) {
        if (state == null || state.player() == null || executionDispatcher == null) {
            if (state != null && state.player() != null) {
                debug(state.player(), "processing completion abandoned: reason=dispatcher_unavailable recipe="
                        + (activeRecipe == null ? "unknown" : activeRecipe.id()));
            }
            cleanupRetiredAttempt(state);
            return;
        }
        Runnable completion = () -> completeForgeAttempt(state, activeRecipe, firstCraft, result, throwable);
        if (threadOwnership != null && threadOwnership.isEntityOwned(state.player())) {
            completion.run();
            return;
        }
        try {
            var scheduled = executionDispatcher.runEntity(
                    plugin,
                    state.player(),
                    completion,
                    () -> cleanupRetiredAttempt(state));
            if (scheduled == null) {
                completeForgeAttempt(state, activeRecipe, firstCraft, result,
                        new RejectedExecutionException("Forge GUI completion scheduling was rejected."));
            }
        } catch (Throwable schedulingFailure) {
            completeForgeAttempt(state, activeRecipe, firstCraft, result, schedulingFailure);
        }
    }

    private void cleanupRetiredAttempt(ForgeGuiSession state) {
        if (state == null) {
            return;
        }
        state.setProcessing(false);
        stateManager.remove(state);
    }

    private void completeForgeAttempt(ForgeGuiSession state,
            Recipe activeRecipe,
            boolean firstCraft,
            ForgeResult result,
            Throwable throwable) {
        state.setProcessing(false);
        stateManager.remove(state);
        if (throwable != null) {
            debug(state.player(), "processing completed: recipe=" + activeRecipe.id() + " outcome=exception reason="
                    + Texts.toStringSafe(throwable.getMessage()));
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
            debug(state.player(), "processing completed: recipe=" + activeRecipe.id() + " outcome=failed errorKey=" + errorKey
                    + " resultItem=" + describe(result == null ? null : result.resultItem()));
            returnFailedAttempt(state, errorKey, replacements);
            fireForgeCompleted(state.player(), activeRecipe, result, false);
            return;
        }
        state.setForgeCompleted(true);
        debug(state.player(), "processing completed: recipe=" + activeRecipe.id() + " outcome=success quality="
                + Texts.toStringSafe(result.quality()) + " multiplier=" + result.multiplier()
                + " resultItem=" + describe(result.resultItem()));
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
        if (player == null || recipe == null || threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
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
            debug(state.player(), "drag ignored: reason=no_items rawSlots=" + drag.rawSlots());
            return;
        }
        int topSize = state.guiSession().getInventory().getSize();
        Integer rawSlot = drag.rawSlots().stream()
                .filter(slot -> slot != null && slot >= 0 && slot < topSize)
                .findFirst()
                .orElse(null);
        if (rawSlot == null || drag.rawSlots().size() != 1 || drag.newItems().size() != 1) {
            debug(state.player(), "drag ignored: reason=not_single_top_slot rawSlots=" + drag.rawSlots());
            return;
        }
        GuiTemplate.ResolvedSlot slot = state.guiSession().template().resolvedSlotAt(rawSlot);
        if (slot == null || slot.definition() == null) {
            debug(state.player(), "drag ignored: reason=unresolved_slot rawSlot=" + rawSlot);
            return;
        }
        ItemStack placedItem = ForgeGuiStateSupport.cloneNonAir(drag.newItems().get(rawSlot));
        if (placedItem == null) {
            debug(state.player(), "drag ignored: reason=empty_item rawSlot=" + rawSlot);
            return;
        }
        String slotType = stateSupport.normalizedType(slot.definition());
        debug(state.player(), "drag evaluated: slotType=" + slotType + " rawSlot=" + rawSlot
                + " item=" + describe(placedItem) + " oldCursor=" + describe(drag.oldCursor()));
        switch (slotType) {
            case "blueprint_inputs" ->
                handleDragPlacement(drag, state, rawSlot, "blueprint_inputs", placedItem, state.blueprintItems(),
                        itemStack -> stateSupport.findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null);
            case "required_materials" ->
                handleDragPlacement(drag, state, rawSlot, "required_materials", placedItem, state.requiredMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            String materialId = materialKey(plugin.itemIdentifierService().identifyItem(itemStack));
                            return Texts.isNotBlank(materialId) && rules.requiredIds().contains(materialId);
                        });
            case "optional_materials" ->
                handleDragPlacement(drag, state, rawSlot, "optional_materials", placedItem, state.optionalMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            int occupied = state.optionalMaterialItems().containsKey(rawSlot)
                                    ? state.optionalMaterialItems().size() - 1
                                    : state.optionalMaterialItems().size();
                            String materialId = materialKey(plugin.itemIdentifierService().identifyItem(itemStack));
                            return stateSupport.canPlaceOptionalMaterial(materialId, rules, Math.max(0, occupied));
                        });
            default -> debug(state.player(), "drag ignored: reason=unsupported_slot_type slotType=" + slotType
                    + " rawSlot=" + rawSlot);
        }
    }

    private void handleDragPlacement(GuiDragContext drag,
            ForgeGuiSession state,
            int slot,
            String slotType,
            ItemStack placedItem,
            Map<Integer, ItemStack> items,
            Predicate<ItemStack> validator) {
        if (drag == null || state == null || placedItem == null || items == null) {
            return;
        }
        if (items.containsKey(slot)) {
            debug(state.player(), "drag placement rejected: slotType=" + slotType + " slot=" + slot
                    + " reason=occupied item=" + describe(placedItem));
            return;
        }
        if (validator != null && !validator.test(placedItem)) {
            debug(state.player(), "drag placement rejected: slotType=" + slotType + " slot=" + slot
                    + " reason=validation item=" + describe(placedItem));
            return;
        }
        items.put(slot, placedItem);
        ItemStack cursor = ForgeGuiStateSupport.cloneNonAir(drag.oldCursor());
        ItemStack cursorAfter = cursor;
        if (cursor != null) {
            int remaining = Math.max(0, cursor.getAmount() - placedItem.getAmount());
            if (remaining <= 0) {
                cursorAfter = null;
                drag.setCursor(null);
            } else {
                cursor.setAmount(remaining);
                drag.setCursor(cursor);
            }
        }
        renderer.refreshGui(state);
        debug(state.player(), "drag placement accepted: slotType=" + slotType + " slot=" + slot
                + " item=" + describe(placedItem) + " cursorNow=" + describe(cursorAfter));
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

    private void debug(Player player, String message) {
        GuiDebugSupport.log(plugin, player, "forge: " + message);
    }

    private String describe(ItemStack itemStack) {
        return GuiDebugSupport.describeItem(itemStack);
    }

    private final class ForgeSessionHandler implements GuiSessionHandler {

        private final ForgeGuiSession state;

        private ForgeSessionHandler(ForgeGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (state.processing()) {
                debug(state.player(), "slot click blocked: reason=processing click=" + click.clickType()
                        + " current=" + describe(click.currentItem()) + " cursor=" + describe(click.cursorItem()));
                click.setCancelled(true);
                return;
            }
            if (slot == null || slot.definition() == null) {
                debug(state.player(), "slot click ignored: reason=unresolved_slot click=" + click.clickType());
                return;
            }
            String slotType = stateSupport.normalizedType(slot.definition());
            debug(state.player(), "slot click evaluated: slotType=" + slotType + " slot=" + slot.inventorySlot()
                    + " click=" + click.clickType() + " shift=" + click.isShiftClick()
                    + " current=" + describe(click.currentItem()) + " cursor=" + describe(click.cursorItem()));
            switch (slotType) {
                case "blueprint_inputs" ->
                    handleBlueprintClick(click, state, slot.inventorySlot());
                case "required_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), true);
                case "optional_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), false);
                case "confirm" ->
                    handleConfirmClick(state);
                default -> debug(state.player(), "slot click ignored: reason=unsupported_slot_type slotType=" + slotType
                        + " slot=" + slot.inventorySlot());
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (state.processing()) {
                debug(state.player(), "player inventory click blocked: reason=processing click=" + click.clickType()
                        + " current=" + describe(click.currentItem()) + " cursor=" + describe(click.cursorItem()));
                click.setCancelled(true);
                return;
            }
            debug(state.player(), "player inventory click evaluated: click=" + click.clickType()
                    + " shift=" + click.isShiftClick() + " blockedTransfer=" + click.isBlockedTransfer()
                    + " moveToOther=" + click.isMoveToOtherInventory() + " current=" + describe(click.currentItem())
                    + " cursor=" + describe(click.cursorItem()));
            if (!click.isBlockedTransfer()) {
                return;
            }
            click.setCancelled(true);
            if (click.isMoveToOtherInventory()) {
                handleShiftFromPlayerInventory(click, state);
            } else {
                debug(state.player(), "player inventory transfer blocked: reason=unsupported_transfer click=" + click.clickType());
            }
        }

        @Override
        public void onDrag(GuiSession session, GuiDragContext drag) {
            if (state.processing()) {
                debug(state.player(), "drag blocked: reason=processing rawSlots=" + (drag == null ? "[]" : drag.rawSlots()));
                return;
            }
            handleSingleSlotDrag(drag, state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            if (state.processing()) {
                debug(state.player(), "close ignored: reason=processing");
                return;
            }
            ItemStack cursorItem = close != null && close.player() != null
                    ? ForgeGuiStateSupport.cloneNonAir(close.player().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                close.player().setItemOnCursor(null);
            }
            debug(state.player(), "close handled: completed=" + state.forgeCompleted() + " cursor=" + describe(cursorItem)
                    + " blueprints=" + state.blueprintItems().size() + " required=" + state.requiredMaterialItems().size()
                    + " optional=" + state.optionalMaterialItems().size());
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

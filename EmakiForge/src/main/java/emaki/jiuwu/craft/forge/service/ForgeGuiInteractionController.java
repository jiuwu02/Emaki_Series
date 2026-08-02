package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
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
            debug(state.player(), "forge.gui.shift_transfer.ignored_empty", null);
            return;
        }
        ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
        ItemSourceRef source = state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack);
        String materialId = materialKey(state, source);

        if (rules.requiredIds().contains(materialId)) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "required_materials"), state.requiredMaterialItems());
            if (slot >= 0) {
                state.requiredMaterialItems().put(slot, itemStack);
                click.clearClickedSlot();
                renderer.refreshGui(state);
                debug(state.player(), "forge.gui.shift_transfer.accepted", replacements(
                        "slot_type", "required_materials",
                        "slot", slot,
                        "material", materialId,
                        "item", describe(itemStack)));
            } else {
                debug(state.player(), "forge.gui.shift_transfer.rejected_no_free_slot", replacements(
                        "slot_type", "required_materials",
                        "material", materialId,
                        "item", describe(itemStack)));
            }
            return;
        }
        if (stateSupport.canPlaceOptionalMaterial(materialId, rules, state.optionalMaterialItems().size())) {
            int slot = stateSupport.firstFreeSlot(stateSupport.slotsForType(state, "optional_materials"), state.optionalMaterialItems());
            if (slot >= 0) {
                state.optionalMaterialItems().put(slot, itemStack);
                click.clearClickedSlot();
                renderer.refreshGui(state);
                debug(state.player(), "forge.gui.shift_transfer.accepted", replacements(
                        "slot_type", "optional_materials",
                        "slot", slot,
                        "material", materialId,
                        "item", describe(itemStack)));
            } else {
                debug(state.player(), "forge.gui.shift_transfer.rejected_no_free_slot", replacements(
                        "slot_type", "optional_materials",
                        "material", materialId,
                        "item", describe(itemStack)));
            }
            return;
        }
        debug(state.player(), "forge.gui.shift_transfer.rejected_validation", replacements(
                "material", materialId,
                "item", describe(itemStack)));
    }

    private void handleBlueprintClick(GuiClickContext click, ForgeGuiSession state, int slot) {
        handleMappedSlotClick(
                click,
                state,
                slot,
                "blueprint_inputs",
                state.blueprintItems(),
                itemStack -> stateSupport.findBlueprintRequirementBySource(
                        state,
                        state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack)) != null
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
        ItemSourceRef source = state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack);

                    String materialId = materialKey(state, source);
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
            debug(state.player(), "forge.gui.slot_click.rejected_unsupported_keyboard", replacements(
                    "slot_type", slotType,
                    "slot", slot,
                    "click", click.clickType()));
            return;
        }
        ItemStack heldItem = ForgeGuiStateSupport.cloneNonAir(click.heldItem());
        if (heldItem != null) {
            if (validator != null && !validator.test(heldItem)) {
                debug(state.player(), "forge.gui.slot_placement.rejected_validation", replacements(
                        "slot_type", slotType,
                        "slot", slot,
                        "item", describe(heldItem)));
                return;
            }
            ItemStack previous = ForgeGuiStateSupport.cloneNonAir(items.put(slot, heldItem));
            click.setHeldItem(previous);
            renderer.refreshGui(state);
            debug(state.player(), "forge.gui.slot_placement.accepted", replacements(
                    "slot_type", slotType,
                    "slot", slot,
                    "item", describe(heldItem),
                    "replaced", describe(previous)));
            return;
        }
        if (click.isShiftClick()) {
            debug(state.player(), "forge.gui.slot_removal.ignored_shift_click", replacements(
                    "slot_type", slotType,
                    "slot", slot));
            return;
        }
        ItemStack removed = ForgeGuiStateSupport.cloneNonAir(items.remove(slot));
        if (removed == null) {
            debug(state.player(), "forge.gui.slot_removal.ignored_empty", replacements(
                    "slot_type", slotType,
                    "slot", slot));
            return;
        }
        click.setHeldItem(removed);
        renderer.refreshGui(state);
        debug(state.player(), "forge.gui.slot_removal.completed", replacements(
                "slot_type", slotType,
                "slot", slot,
                "item", describe(removed)));
    }

    private void handleConfirmClick(ForgeGuiSession state) {
        if (state.processing() || state.shutdownRetiring()) {
            debug(state.player(), "forge.gui.confirm.rejected_state", replacements(
                    "reason", state.processing() ? "processing" : "shutdown_retiring"));
            return;
        }
        stateSupport.refreshDerivedValues(state);
        debug(state.player(), "forge.gui.confirm.requested", replacements(
                "current_capacity", state.currentCapacity(),
                "max_capacity", state.maxCapacity(),
                "blueprints", state.blueprintItems().size(),
                "required", state.requiredMaterialItems().size(),
                "optional", state.optionalMaterialItems().size()));
        if (state.maxCapacity() > 0 && state.currentCapacity() > state.maxCapacity()) {
            debug(state.player(), "forge.gui.confirm.rejected_capacity_exceeded", replacements(
                    "current", state.currentCapacity(),
                    "max", state.maxCapacity()));
            state.runtimeSnapshot().messageService().send(
                    state.player(),
                    "forge.error.capacity_exceeded",
                    Map.of("current", state.currentCapacity(), "max", state.maxCapacity())
            );
            return;
        }
        Recipe activeRecipe = state.recipe() != null ? state.recipe() : state.previewRecipe();
        if (activeRecipe == null) {
            RecipeMatch match = state.runtimeSnapshot().forgeService().findMatchingRecipe(state.player(), state.toGuiItems());
            if (match.recipe() == null) {
                debug(state.player(), "forge.gui.confirm.rejected_recipe_mismatch", replacements(
                        "error_key", match.errorKey()));
                state.runtimeSnapshot().messageService().send(state.player(), match.errorKey(), match.replacements());
                return;
            }
            activeRecipe = match.recipe();
            debug(state.player(), "forge.gui.confirm.recipe_resolved", replacements(
                    "recipe", activeRecipe.id()));
        }
        Recipe finalRecipe = activeRecipe;
        GuiItems snapshot = state.toGuiItems();
        ForgeService.PreparedForge preparedForge = state.preparedForge();
        if (preparedForge == null) {
            preparedForge = state.runtimeSnapshot().forgeService().prepareForge(
                    state.player(),
                    finalRecipe,
                    snapshot,
                    state.previewSeed(),
                    state.previewForgedAt()
            );
            state.setPreparedForge(preparedForge);
        }
        if (preparedForge == null || preparedForge.request() == null) {
            debug(state.player(), "forge.gui.confirm.rejected_prepare_failed", replacements(
                    "recipe", finalRecipe.id()));
            state.runtimeSnapshot().messageService().send(state.player(), "forge.error.item_create");
            return;
        }
        boolean firstCraft = !state.runtimeSnapshot().playerDataStore().hasCrafted(state.player().getUniqueId(), activeRecipe.id());
        if (threadOwnership == null || !threadOwnership.isEntityOwned(state.player())) {
            debug(state.player(), "forge.gui.confirm.rejected_player_owner_unavailable", replacements(
                    "recipe", finalRecipe.id()));
            state.runtimeSnapshot().messageService().send(state.player(), "forge.error.action_failed", Map.of("reason", "player owner is unavailable"));
            return;
        }
        if (!ensureCurrentGeneration(state)) {
            debug(state.player(), "forge.gui.confirm.rejected_runtime_generation_changed", replacements(
                    "recipe", finalRecipe.id()));
            return;
        }
        ForgeStartEvent startEvent = new ForgeStartEvent(state.player(), finalRecipe.id(), firstCraft, finalRecipe.successRate());
        org.bukkit.Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            debug(state.player(), "forge.gui.confirm.rejected_start_event_cancelled", replacements(
                    "recipe", finalRecipe.id()));
            return;
        }
        debug(state.player(), "forge.gui.processing.started", replacements(
                "recipe", finalRecipe.id(),
                "first_craft", firstCraft,
                "success_rate", startEvent.getSuccessRate()));
        state.setProcessing(true);
        state.setRecipe(finalRecipe);
        state.setPreviewRecipe(finalRecipe);
        CompletableFuture<Void> ownerCompletion = new CompletableFuture<>();
        if (!state.runtimeSnapshot().forgeService().trackCompletion(state.runtimeGeneration(), ownerCompletion)) {
            if (state.claimSettlement()) {
                runTerminalSettlement(
                        state,
                        () -> returnFailedAttempt(state, "forge.error.runtime_unavailable", Map.of(
                                "reason", "forge completion could not be tracked")),
                        "forge completion tracking rejection settlement failed");
            }
            state.player().closeInventory();
            return;
        }
        state.player().closeInventory();
        try {
            state.runtimeSnapshot().forgeService().executeForgeAsync(
                    state.player(),
                    finalRecipe,
                    snapshot,
                    preparedForge,
                    state.runtimeGeneration(),
                    startEvent.getSuccessRate(),
                    state::claimResultDelivery,
                    state::releaseResultDelivery,
                    state::markResultCommitted
            ).whenComplete((result, throwable) -> completeForgeAttemptOnOwner(
                    state,
                    finalRecipe,
                    firstCraft,
                    result,
                    throwable,
                    ownerCompletion
            ));
        } catch (Throwable throwable) {
            completeForgeAttemptOnOwner(state, finalRecipe, firstCraft, null, throwable, ownerCompletion);
        }
    }

    private void completeForgeAttemptOnOwner(ForgeGuiSession state,
            Recipe activeRecipe,
            boolean firstCraft,
            ForgeResult result,
            Throwable throwable,
            CompletableFuture<Void> ownerCompletion) {
        if (state == null || state.player() == null || executionDispatcher == null) {
            cleanupRetiredAttempt(state, activeRecipe, result, "completion owner is unavailable");
            ownerCompletion.complete(null);
            return;
        }
        Runnable completion = () -> {
            try {
                if (!plugin.isGenerationActive(state.runtimeGeneration()) || !stateManager.isCurrent(state)) {
                    completeStaleAttempt(state, activeRecipe, result);
                    return;
                }
                completeForgeAttempt(state, activeRecipe, firstCraft, result, throwable);
            } finally {
                ownerCompletion.complete(null);
            }
        };
        if (threadOwnership != null && threadOwnership.isEntityOwned(state.player())) {
            completion.run();
            return;
        }
        try {
            var scheduled = executionDispatcher.runEntity(
                    plugin.coreLib(),
                    state.player(),
                    completion,
                    () -> {
                        cleanupRetiredAttempt(state, activeRecipe, result,
                                "completion owner retired before execution");
                        ownerCompletion.completeExceptionally(new RejectedExecutionException(
                                "Forge GUI completion owner retired before execution."));
                    });
            if (scheduled == null) {
                cleanupRetiredAttempt(state, activeRecipe, result,
                        "completion scheduling was rejected");
                ownerCompletion.completeExceptionally(new RejectedExecutionException(
                        "Forge GUI completion scheduling was rejected."));
            }
        } catch (Throwable schedulingFailure) {
            cleanupRetiredAttempt(state, activeRecipe, result,
                    "completion scheduling failed: " + Texts.toStringSafe(schedulingFailure.getMessage()));
            ownerCompletion.completeExceptionally(schedulingFailure);
        }
    }

    private void completeStaleAttempt(ForgeGuiSession state, Recipe activeRecipe, ForgeResult result) {
        if (state == null) {
            return;
        }
        boolean committedDelivery = state.resultCommitted() && state.resultDeliveryClaimed();
        if (committedDelivery ? !state.claimResultDeliverySettlement() : !state.claimSettlement()) {
            return;
        }
        runTerminalSettlement(
                state,
                () -> {
                    if (committedDelivery) {
                        settleCommittedDelivery(state, activeRecipe);
                    } else {
                        settleInputs(state, activeRecipe, result);
                    }
                },
                "stale forge completion settlement failed");
    }

    private void cleanupRetiredAttempt(ForgeGuiSession state,
            Recipe activeRecipe,
            ForgeResult result,
            String reason) {
        if (state == null) {
            return;
        }
        if (state.player() == null || threadOwnership == null || !threadOwnership.isEntityOwned(state.player())) {
            state.setProcessing(false);
            recordSettlementFailure(state, reason);
            return;
        }
        boolean committedDelivery = state.resultCommitted() && state.resultDeliveryClaimed();
        if (committedDelivery ? !state.claimResultDeliverySettlement() : !state.claimSettlement()) {
            return;
        }
        runTerminalSettlement(
                state,
                () -> {
                    if (committedDelivery) {
                        settleCommittedDelivery(state, activeRecipe);
                    } else {
                        settleInputs(state, activeRecipe, result);
                    }
                },
                "retired forge completion settlement failed");
    }

    private void completeForgeAttempt(ForgeGuiSession state,
            Recipe activeRecipe,
            boolean firstCraft,
            ForgeResult result,
            Throwable throwable) {
        boolean committedDelivery = state.resultCommitted() && state.resultDeliveryClaimed();
        if (committedDelivery ? !state.claimResultDeliverySettlement() : !state.claimSettlement()) {
            return;
        }
        if (throwable != null) {
            debug(state.player(), "forge.gui.processing.completed_exception", replacements(
                    "recipe", activeRecipe.id(),
                    "reason", Texts.toStringSafe(throwable.getMessage())));
            try {
                state.runtimeSnapshot().messageService().warning("console.forge_execution_failed", Map.of(
                        "recipe", activeRecipe.id(),
                        "error", String.valueOf(throwable.getMessage())
                ));
            } catch (Throwable messageFailure) {
                plugin.getLogger().warning("Forge execution failure logging failed: "
                        + Texts.toStringSafe(messageFailure.getMessage()));
            }
            if (committedDelivery) {
                state.setForgeCompleted(true);
                runTerminalSettlement(
                        state,
                        () -> settleCommittedDelivery(state, activeRecipe),
                        "committed exceptional forge settlement failed");
                return;
            }
            runTerminalSettlement(
                    state,
                    () -> returnFailedAttempt(state, "forge.error.action_failed", Map.of(
                            "reason", Texts.toStringSafe(throwable.getMessage()))),
                    "exceptional forge input settlement failed");
            return;
        }
        if (result == null || !result.success()) {
            String errorKey = result == null || Texts.isBlank(result.errorKey()) ? "forge.error.action_failed" : result.errorKey();
            Map<String, Object> replacements = result == null || result.replacements() == null ? Map.of() : result.replacements();
            debug(state.player(), "forge.gui.processing.completed_failed", replacements(
                    "recipe", activeRecipe.id(),
                    "error_key", errorKey,
                    "result_item", describe(result == null ? null : result.resultItem())));
            runTerminalSettlement(
                    state,
                    () -> returnFailedAttempt(state, errorKey, replacements),
                    "failed forge input settlement failed");
            fireForgeCompleted(state.player(), activeRecipe, result, false);
            return;
        }
        state.setForgeCompleted(true);
        debug(state.player(), "forge.gui.processing.completed_success", replacements(
                "recipe", activeRecipe.id(),
                "quality", Texts.toStringSafe(result.quality()),
                "multiplier", result.multiplier(),
                "result_item", describe(result.resultItem())));
        runTerminalSettlement(
                state,
                () -> settleCommittedDelivery(state, activeRecipe),
                "successful forge settlement failed");
        if (Texts.isNotBlank(result.quality())) {
            state.runtimeSnapshot().messageService().send(
                    state.player(),
                    "forge.success.quality",
                    Map.of("quality", result.quality(), "multiplier", result.multiplier())
            );
        }
        if (firstCraft) {
            state.runtimeSnapshot().messageService().send(state.player(), "forge.success.first_craft");
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
            debug(state.player(), "forge.gui.drag.ignored_no_items", replacements(
                    "raw_slots", drag.rawSlots()));
            return;
        }
        int topSize = state.guiSession().getInventory().getSize();
        Integer rawSlot = drag.rawSlots().stream()
                .filter(slot -> slot != null && slot >= 0 && slot < topSize)
                .findFirst()
                .orElse(null);
        if (rawSlot == null || drag.rawSlots().size() != 1 || drag.newItems().size() != 1) {
            debug(state.player(), "forge.gui.drag.ignored_not_single_top_slot", replacements(
                    "raw_slots", drag.rawSlots()));
            return;
        }
        GuiTemplate.ResolvedSlot slot = state.guiSession().template().resolvedSlotAt(rawSlot);
        if (slot == null || slot.definition() == null) {
            debug(state.player(), "forge.gui.drag.ignored_unresolved_slot", replacements(
                    "raw_slot", rawSlot));
            return;
        }
        ItemStack placedItem = ForgeGuiStateSupport.cloneNonAir(drag.newItems().get(rawSlot));
        if (placedItem == null) {
            debug(state.player(), "forge.gui.drag.ignored_empty_item", replacements(
                    "raw_slot", rawSlot));
            return;
        }
        String slotType = stateSupport.normalizedType(slot.definition());
        debug(state.player(), "forge.gui.drag.evaluated", replacements(
                "slot_type", slotType,
                "raw_slot", rawSlot,
                "item", describe(placedItem),
                "old_cursor", describe(drag.oldCursor())));
        switch (slotType) {
            case "blueprint_inputs" ->
                handleDragPlacement(drag, state, rawSlot, "blueprint_inputs", placedItem, state.blueprintItems(),
                        itemStack -> stateSupport.findBlueprintRequirementBySource(
                                state,
                                state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack)) != null);
            case "required_materials" ->
                handleDragPlacement(drag, state, rawSlot, "required_materials", placedItem, state.requiredMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            String materialId = materialKey(state, state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack));
                            return Texts.isNotBlank(materialId) && rules.requiredIds().contains(materialId);
                        });
            case "optional_materials" ->
                handleDragPlacement(drag, state, rawSlot, "optional_materials", placedItem, state.optionalMaterialItems(),
                        itemStack -> {
                            ForgeGuiStateSupport.MaterialSlotRules rules = stateSupport.resolveMaterialSlotRules(state);
                            int occupied = state.optionalMaterialItems().containsKey(rawSlot)
                                    ? state.optionalMaterialItems().size() - 1
                                    : state.optionalMaterialItems().size();
                            String materialId = materialKey(state, state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack));
                            return stateSupport.canPlaceOptionalMaterial(materialId, rules, Math.max(0, occupied));
                        });
            default -> debug(state.player(), "forge.gui.drag.ignored_unsupported_slot_type", replacements(
                    "slot_type", slotType,
                    "raw_slot", rawSlot));
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
            debug(state.player(), "forge.gui.drag_placement.rejected_occupied", replacements(
                    "slot_type", slotType,
                    "slot", slot,
                    "item", describe(placedItem)));
            return;
        }
        if (validator != null && !validator.test(placedItem)) {
            debug(state.player(), "forge.gui.drag_placement.rejected_validation", replacements(
                    "slot_type", slotType,
                    "slot", slot,
                    "item", describe(placedItem)));
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
        debug(state.player(), "forge.gui.drag_placement.accepted", replacements(
                "slot_type", slotType,
                "slot", slot,
                "item", describe(placedItem),
                "cursor_now", describe(cursorAfter)));
    }

    void settleShutdownSessionOnOwner(ForgeGuiSession state) {
        if (state == null || state.player() == null || threadOwnership == null
                || !threadOwnership.isEntityOwned(state.player())) {
            recordSettlementFailure(state, "shutdown settlement did not run on the player owner");
            return;
        }
        boolean processing = state.processing();
        boolean committedDelivery = state.resultCommitted() && state.resultDeliveryClaimed();
        if (processing && !committedDelivery) {
            return;
        }
        if (committedDelivery ? !state.claimResultDeliverySettlement() : !state.claimSettlement()) {
            return;
        }
        if (processing) {
            runTerminalSettlement(
                    state,
                    () -> settleCommittedDelivery(state, state.recipe()),
                    "shutdown committed delivery settlement failed");
            return;
        }
        runTerminalSettlement(
                state,
                () -> {
                    ItemStack cursorItem = ForgeGuiStateSupport.cloneNonAir(state.player().getItemOnCursor());
                    if (cursorItem != null) {
                        state.player().setItemOnCursor(null);
                    }
                    stateSupport.returnItems(state, cursorItem);
                },
                "shutdown input settlement failed");
    }

    void handleShutdownClosureFailure(ForgeGuiSession state, String reason) {
        if (state == null) {
            return;
        }
        if (state.processing()) {
            recordSettlementFailure(state, reason + "; tracked processing completion retains settlement ownership");
            return;
        }
        abandonRetiredSession(state, reason);
    }

    void abandonRetiredSession(ForgeGuiSession state, String reason) {
        if (state == null || state.settlementCommitted()) {
            return;
        }
        recordSettlementFailure(state, state.processing()
                ? reason + "; processing completion still owns unresolved settlement"
                : reason + "; unresolved items remain reserved for an owner-thread retry");
    }

    private void runTerminalSettlement(ForgeGuiSession state, Runnable settlement, String failureReason) {
        boolean committed = false;
        try {
            if (settlement != null) {
                settlement.run();
            }
            if (state == null || !state.commitSettlement()) {
                throw new IllegalStateException("Forge settlement reservation could not be committed.");
            }
            committed = true;
        } catch (Throwable throwable) {
            if (state != null) {
                state.releaseSettlement();
            }
            try {
                recordSettlementFailure(state, failureReason + ": " + Texts.toStringSafe(throwable.getMessage()));
            } catch (Throwable ignored) {
                // Terminal cleanup must not be prevented by diagnostic failures.
            }
        } finally {
            if (state != null) {
                state.setProcessing(false);
                if (committed) {
                    stateManager.remove(state);
                }
            }
        }
    }

    private void settleCommittedDelivery(ForgeGuiSession state, Recipe activeRecipe) {
        if (state == null) {
            return;
        }
        if (activeRecipe != null) {
            stateSupport.returnUnusedInputs(state, activeRecipe);
        }
        state.clearStoredItems();
    }

    private void settleInputs(ForgeGuiSession state, Recipe activeRecipe, ForgeResult result) {
        if (result != null && result.success() && activeRecipe != null) {
            stateSupport.returnUnusedInputs(state, activeRecipe);
            state.clearStoredItems();
            return;
        }
        stateSupport.returnItems(state);
    }

    private void recordSettlementFailure(ForgeGuiSession state, String reason) {
        plugin.runtimeMetrics().recordGuiSettlementFailure();
        String playerId = state == null || state.playerId() == null
                ? "unknown"
                : state.playerId().toString();
        plugin.getLogger().warning("Forge GUI settlement could not run on the player owner: player="
                + playerId + " reason=" + Texts.toStringSafe(reason));
    }

    private void returnFailedAttempt(ForgeGuiSession state, String errorKey, Map<String, ?> replacements) {
        try {
            state.runtimeSnapshot().messageService().send(
                    state.player(),
                    errorKey,
                    replacements == null ? Map.of() : replacements);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Forge failure message dispatch failed: "
                    + Texts.toStringSafe(throwable.getMessage()));
        }
        stateSupport.returnItems(state);
    }

    private String materialKey(ForgeGuiSession state, ItemSourceRef source) {
        if (state == null || source == null || state.runtimeSnapshot().forgeService() == null) {
            return "";
        }
        var material = state.runtimeSnapshot().forgeService().findMaterialBySource(source);
        return material == null ? "" : material.key();
    }

    private boolean ensureCurrentGeneration(ForgeGuiSession state) {
        if (state != null && state.shutdownRetiring()) {
            return false;
        }
        if (state != null && plugin.isGenerationActive(state.runtimeGeneration())) {
            return true;
        }
        plugin.runtimeMetrics().recordGuiStale();
        if (state != null && state.player() != null) {
            state.runtimeSnapshot().messageService().send(state.player(), "forge.error.runtime.stale_session");
            state.player().closeInventory();
        }
        return false;
    }

    private void debug(Player player, String langKey, Map<String, ?> replacements) {
        try {
            GuiDebugSupport.log(plugin, player, langKey, replacements == null ? Map.of() : replacements);
        } catch (Throwable ignored) {
            // Debug diagnostics must not interrupt GUI ownership or settlement.
        }
    }

    private Map<String, Object> replacements(Object... entries) {
        return GuiDebugSupport.replacements(entries);
    }

    private String describe(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "air";
        }
        return itemStack.getType().getKey() + "x" + itemStack.getAmount();
    }

    private final class ForgeSessionHandler implements GuiSessionHandler {

        private final ForgeGuiSession state;

        private ForgeSessionHandler(ForgeGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (!ensureCurrentGeneration(state)) {
                click.setCancelled(true);
                return;
            }
            if (state.processing()) {
                debug(state.player(), "forge.gui.slot_click.blocked_processing", replacements(
                        "click", click.clickType(),
                        "current", describe(click.currentItem()),
                        "cursor", describe(click.cursorItem())));
                click.setCancelled(true);
                return;
            }
            if (slot == null || slot.definition() == null) {
                debug(state.player(), "forge.gui.slot_click.ignored_unresolved_slot", replacements(
                        "click", click.clickType()));
                return;
            }
            String slotType = stateSupport.normalizedType(slot.definition());
            debug(state.player(), "forge.gui.slot_click.evaluated", replacements(
                    "slot_type", slotType,
                    "slot", slot.inventorySlot(),
                    "click", click.clickType(),
                    "shift", click.isShiftClick(),
                    "current", describe(click.currentItem()),
                    "cursor", describe(click.cursorItem())));
            switch (slotType) {
                case "blueprint_inputs" ->
                    handleBlueprintClick(click, state, slot.inventorySlot());
                case "required_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), true);
                case "optional_materials" ->
                    handleMaterialClick(click, state, slot.inventorySlot(), false);
                case "confirm" ->
                    handleConfirmClick(state);
                default -> debug(state.player(), "forge.gui.slot_click.ignored_unsupported_slot_type", replacements(
                        "slot_type", slotType,
                        "slot", slot.inventorySlot()));
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (!ensureCurrentGeneration(state)) {
                click.setCancelled(true);
                return;
            }
            if (state.processing()) {
                debug(state.player(), "forge.gui.player_inventory_click.blocked_processing", replacements(
                        "click", click.clickType(),
                        "current", describe(click.currentItem()),
                        "cursor", describe(click.cursorItem())));
                click.setCancelled(true);
                return;
            }
            debug(state.player(), "forge.gui.player_inventory_click.evaluated", replacements(
                    "click", click.clickType(),
                    "shift", click.isShiftClick(),
                    "blocked_transfer", click.isBlockedTransfer(),
                    "move_to_other", click.isMoveToOtherInventory(),
                    "current", describe(click.currentItem()),
                    "cursor", describe(click.cursorItem())));
            if (!click.isBlockedTransfer()) {
                return;
            }
            click.setCancelled(true);
            if (click.isMoveToOtherInventory()) {
                handleShiftFromPlayerInventory(click, state);
            } else {
                debug(state.player(), "forge.gui.player_inventory_transfer.blocked_unsupported", replacements(
                        "click", click.clickType()));
            }
        }

        @Override
        public void onDrag(GuiSession session, GuiDragContext drag) {
            if (!ensureCurrentGeneration(state)) {
                return;
            }
            if (state.processing()) {
                debug(state.player(), "forge.gui.drag.blocked_processing", replacements(
                        "raw_slots", drag == null ? "[]" : drag.rawSlots()));
                return;
            }
            handleSingleSlotDrag(drag, state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            if (state.processing()) {
                debug(state.player(), "forge.gui.close.ignored_processing", null);
                return;
            }
            if (!state.claimSettlement()) {
                debug(state.player(), "forge.gui.close.ignored_settlement_already_claimed", null);
                return;
            }
            runTerminalSettlement(
                    state,
                    () -> {
                        ItemStack cursorItem = close != null && close.player() != null
                                ? ForgeGuiStateSupport.cloneNonAir(close.player().getItemOnCursor())
                                : null;
                        if (cursorItem != null) {
                            close.player().setItemOnCursor(null);
                        }
                        debug(state.player(), "forge.gui.close.handled", replacements(
                                "completed", state.forgeCompleted(),
                                "cursor", describe(cursorItem),
                                "blueprints", state.blueprintItems().size(),
                                "required", state.requiredMaterialItems().size(),
                                "optional", state.optionalMaterialItems().size()));
                        if (!state.forgeCompleted()) {
                            stateSupport.returnItems(state, cursorItem);
                        } else if (cursorItem != null) {
                            stateSupport.giveBackToPlayer(state.player(), cursorItem);
                        }
                    },
                    "GUI close input settlement failed");
        }
    }
}

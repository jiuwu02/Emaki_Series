package emaki.jiuwu.craft.gem.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptOutcome;

final class GemGuiInteractionController {

    private final EmakiGemPlugin plugin;
    private final GemGuiStateManager stateManager;
    private final GemGuiRenderer renderer;
    private final GemGuiService service;

    GemGuiInteractionController(EmakiGemPlugin plugin,
            GemGuiStateManager stateManager,
            GemGuiRenderer renderer,
            GemGuiService service) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.renderer = renderer;
        this.service = service;
    }

    public GuiSessionHandler createSessionHandler(GemGuiSession state) {
        return new GemSessionHandler(state);
    }

    private void scheduleRefresh(GemGuiSession state) {
        plugin.scheduling().runForEntity(plugin, state.player(), () -> renderer.refreshGui(state), null);
    }

    private void scheduleSwitchIfNeeded(GemGuiSession state) {
        if (state == null) {
            return;
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GuiTemplate template = GemGuiTemplates.resolveGemTemplate(plugin.guiTemplateLoader(), itemDefinition);
        String resolvedId = template == null ? "" : template.id();
        if (!resolvedId.equals(state.currentTemplateId())) {
            plugin.scheduling().runForEntity(plugin, state.player(), () -> {
                if (!service.switchTemplate(state)) {
                    renderer.refreshGui(state);
                }
            }, null);
            return;
        }
        renderer.refreshGui(state);
    }

    private void handleSocketClick(GemGuiSession state, GuiClickContext click, int displayIndex) {
        if (state.mode() == GemGuiMode.UPGRADE) {
            handleUpgradeMaterialClick(state, click, displayIndex);
            return;
        }
        if (state.rerollMode()) {
            return;
        }
        ItemStack targetItem = state.mutableTargetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        if (itemDefinition == null) {
            plugin.messageService().send(state.player(), "gui.gem.target_required");
            return;
        }
        if (displayIndex < 0 || displayIndex >= itemDefinition.slots().size()) {
            return;
        }
        GemState currentState = plugin.stateService().resolveState(targetItem, itemDefinition);
        GemItemDefinition.SocketSlot slot = itemDefinition.slots().get(displayIndex);
        int slotIndex = slot.index();
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        if (state.mode() == GemGuiMode.INLAY) {
            if (!currentState.isOpened(slotIndex)) {
                plugin.messageService().send(state.player(), "gui.gem.open_via_open_gui");
                return;
            }
            if (currentState.assignment(slotIndex) != null) {
                plugin.messageService().send(state.player(), "command.inlay.slot_occupied", Map.of("slot", slotIndex));
                return;
            }
            if (isPendingInlaySlot(state, slotIndex) && cursorItem == null) {
                returnPendingInputToCursor(state, click);
                renderer.refreshGui(state);
                return;
            }
            GemItemInstance instance = plugin.itemMatcher().readGemInstance(cursorItem);
            GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (gemDefinition == null) {
                plugin.messageService().send(state.player(), "gui.gem.hold_gem");
                return;
            }
            if (!itemDefinition.allowsGemType(gemDefinition.gemType())) {
                plugin.messageService().send(state.player(), "command.inlay.gem_type_blocked", Map.of("type", gemDefinition.gemType()));
                return;
            }
            if (!gemDefinition.supportsSocketType(slot.type())) {
                plugin.messageService().send(state.player(), "command.inlay.socket_incompatible", Map.of("slot", slotIndex, "type", slot.type()));
                return;
            }
            if (itemDefinition.maxSameType() > 0
                    && plugin.stateService().countAssignmentsByType(itemDefinition, currentState).getOrDefault(gemDefinition.gemType(), 0) >= itemDefinition.maxSameType()) {
                plugin.messageService().send(state.player(), "command.inlay.max_same_type", Map.of("type", gemDefinition.gemType()));
                return;
            }
            if (plugin.stateService().countAssignmentsByGemId(currentState, gemDefinition.id()) >= itemDefinition.maxSameId()) {
                plugin.messageService().send(state.player(), "command.inlay.max_same_id", Map.of("gem", gemDefinition.id()));
                return;
            }
            GemStateService.RelationshipCheck relationshipCheck = plugin.stateService().validateInlayRelationships(currentState, gemDefinition);
            if (!relationshipCheck.allowed()) {
                plugin.messageService().send(state.player(), relationshipCheck.messageKey(), relationshipCheck.placeholders());
                return;
            }
            returnPendingInput(state);
            state.setPendingOperation(new GemGuiSession.PendingOperation(
                    GemGuiSession.PendingType.INLAY,
                    slotIndex,
                    consumeOneFromCursor(click)
            ));
            renderer.refreshGui(state);
            return;
        }
        if (currentState.assignment(slotIndex) == null) {
            plugin.messageService().send(state.player(), "command.extract.slot_empty", Map.of("slot", slotIndex));
            return;
        }
        GemStateService.RelationshipCheck relationshipCheck = plugin.stateService().validateExtractionRelationships(currentState, slotIndex);
        if (!relationshipCheck.allowed()) {
            plugin.messageService().send(state.player(), relationshipCheck.messageKey(), relationshipCheck.placeholders());
            return;
        }
        returnPendingInput(state);
        state.setPendingOperation(new GemGuiSession.PendingOperation(GemGuiSession.PendingType.EXTRACT, slotIndex, null));
        renderer.refreshGui(state);
    }

    private void handleUpgradeMaterialClick(GemGuiSession state, GuiClickContext click, int displayIndex) {
        if (state == null || click == null || displayIndex < 0 || state.processing()) {
            return;
        }
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        ItemStack currentItem = state.upgradeMaterial(displayIndex);
        state.setUpgradeMaterial(displayIndex, cursorItem);
        click.setCursor(currentItem);
        renderer.refreshGui(state);
    }

    private void handleTargetItemClick(GemGuiSession state, GuiClickContext click) {
        if (state.rerollMode()) {
            plugin.messageService().send(state.player(), "gem.reroll.gem_required");
            return;
        }
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        ItemStack targetItem = state.targetItem();
        if (state.mode() == GemGuiMode.UPGRADE) {
            GemItemInstance instance = plugin.itemMatcher().readGemInstance(cursorItem);
            GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (cursorItem != null && (definition == null || !definition.stages().enabled())) {
                plugin.messageService().send(state.player(), "gui.gem.upgrade_invalid_target");
                return;
            }
            returnPendingInput(state);
            state.setTargetItem(cursorItem);
            click.setCursor(targetItem);
            renderer.refreshGui(state);
            return;
        }
        if (cursorItem != null && plugin.stateService().resolveItemDefinition(cursorItem) == null) {
            plugin.messageService().send(state.player(), "gui.gem.invalid_target");
            return;
        }
        returnPendingInput(state);
        state.setTargetItem(cursorItem);
        click.setCursor(targetItem);
        scheduleSwitchIfNeeded(state);
    }

    private void handleConfirm(GemGuiSession state) {
        if (state.rerollMode()) {
            executeRerollConfirm(state);
            return;
        }
        if (state.mode() == GemGuiMode.UPGRADE) {
            executeUpgrade(state);
            return;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        if (!pendingOperation.active()) {
            plugin.messageService().send(state.player(), "gui.gem.no_pending_action");
            return;
        }
        Player player = state.player();
        ItemStack targetItem = state.mutableTargetItem();
        if (player == null || targetItem == null) {
            plugin.messageService().send(state.player(), "gui.gem.target_required");
            return;
        }
        switch (pendingOperation.type()) {
            case INLAY -> executeInlay(state, pendingOperation, player, targetItem);
            case EXTRACT -> executeExtract(state, pendingOperation, player, targetItem);
            default -> plugin.messageService().send(player, "gui.gem.no_pending_action");
        }
    }

    private void executeRerollConfirm(GemGuiSession state) {
        if (state.processing()) {
            return;
        }
        if (plugin.rerollSessionService() == null) {
            plugin.messageService().send(state.player(), "gui.gem.reroll_unavailable");
            return;
        }
        if (plugin.rerollSessionService().session(state.player().getUniqueId()).isEmpty()) {
            if (state.rerollCompletedOnce() && !state.rerollRestartAcknowledged()) {
                state.setRerollRestartAcknowledged(true);
                plugin.messageService().send(state.player(), "gui.gem.reroll_restart_confirm");
                renderer.refreshGui(state);
                return;
            }
            state.setRerollRestartAcknowledged(false);
            openRerollCandidate(state);
            return;
        }
        state.setProcessing(true);
        try {
            GemRerollSessionService.ActionResult result = plugin.rerollSessionService()
                    .confirm(state.player());
            if (result.success() && result.session() != null) {
                state.setTargetItem(null);
                state.setReturnTargetOnClose(false);
                state.markRerollCompleted();
                plugin.messageService().send(state.player(), "gui.gem.reroll_confirmed", Map.of(
                        "operation_id", result.session().operationId()
                ));
                plugin.scheduling().runForEntity(plugin, state.player(), () -> {
                    if (state.player() != null && state.player().isOnline()) {
                        state.player().closeInventory();
                    }
                }, null);
            } else {
                String errorKey = result.errorKey();
                plugin.messageService().send(state.player(),
                        errorKey == null || errorKey.isBlank() ? "gui.gem.reroll_failed" : errorKey);
            }
        } finally {
            state.setProcessing(false);
        }
    }

    private void openRerollCandidate(GemGuiSession state) {
        GemRerollSessionService.OperationType type = state.mode() == GemGuiMode.REROLL_VALUE
                ? GemRerollSessionService.OperationType.VALUE
                : GemRerollSessionService.OperationType.FULL;
        state.setProcessing(true);
        try {
            GemRerollSessionService.OpenResult result = plugin.rerollSessionService().open(state.player(), type);
            if (!result.success()) {
                String errorKey = result.errorKey();
                plugin.messageService().send(state.player(),
                        errorKey == null || errorKey.isBlank() ? "gui.gem.reroll_failed" : errorKey);
            }
        } finally {
            state.setProcessing(false);
            renderer.refreshGui(state);
        }
    }

    private void executeUpgrade(GemGuiSession state) {
        if (state.processing()) {
            return;
        }
        Player player = state.player();
        ItemStack targetItem = state.mutableTargetItem();
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(targetItem);
        GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (player == null || targetItem == null || definition == null || !definition.stages().enabled()) {
            plugin.messageService().send(player, "gui.gem.upgrade_target_required");
            return;
        }
        int nextLevel = instance.level() + 1;
        if (instance.level() >= definition.stages().maxLevel() || definition.stage(nextLevel) == null) {
            plugin.messageService().send(player, "gui.gem.upgrade_max_level", Map.of(
                    "level", instance.level(),
                    "max_level", definition.stages().maxLevel()
            ));
            return;
        }
        if (plugin.strengthenIntegration() == null || !plugin.strengthenIntegration().available()) {
            plugin.messageService().send(player, "gui.gem.upgrade_unavailable");
            return;
        }

        state.setProcessing(true);
        try {
            EmakiResult<EnhancementAttemptOutcome> result = plugin.strengthenIntegration().attemptUpgrade(
                    player,
                    definition.id(),
                    targetItem,
                    state.upgradeMaterials(),
                    "gem-upgrade:" + player.getUniqueId() + ":" + UUID.randomUUID()
            );
            if (result instanceof EmakiResult.Success<?> success
                    && success.value() instanceof EnhancementAttemptOutcome outcome) {
                applyUpgradeOutcome(state, player, outcome);
            } else if (result instanceof EmakiResult.Partial<?> partial
                    && partial.value() instanceof EnhancementAttemptOutcome outcome) {
                state.setTargetItem(outcome.resultItem());
                state.setUpgradeMaterials(outcome.materialInputs());
                plugin.messageService().send(player, "gui.gem.upgrade_compensation_pending", Map.of(
                        "operation_id", outcome.operationId()
                ));
            } else if (result instanceof EmakiResult.Failure<?> failure) {
                sendUpgradeFailure(player, failure.reasonKey(), failure.placeholders());
            } else {
                plugin.messageService().send(player, "gui.gem.upgrade_failed", Map.of("reason", "unknown_result"));
            }
        } finally {
            state.setProcessing(false);
            renderer.refreshGui(state);
        }
    }

    private void applyUpgradeOutcome(GemGuiSession state, Player player, EnhancementAttemptOutcome outcome) {
        state.setTargetItem(outcome.resultItem());
        state.setUpgradeMaterials(outcome.materialInputs());
        plugin.messageService().send(player,
                outcome.success() ? "gui.gem.upgrade_success" : "gui.gem.upgrade_roll_failed",
                Map.of(
                        "previous_level", outcome.previousLevel(),
                        "resulting_level", outcome.resultingLevel(),
                        "success_rate", outcome.successRate(),
                        "pity_counter", outcome.pityCounter(),
                        "operation_id", outcome.operationId()
                ));
    }

    private void sendUpgradeFailure(Player player, String reasonKey, Map<String, Object> placeholders) {
        String messageKey = switch (Texts.toStringSafe(reasonKey)) {
            case "emaki.api.unavailable", "strengthen.enhancement.provider_not_found" -> "gui.gem.upgrade_unavailable";
            case "strengthen.error.no_recipe", "strengthen.enhancement.recipe_not_found" -> "gui.gem.upgrade_recipe_missing";
            case "strengthen.error.material_missing" -> "gui.gem.upgrade_materials_invalid";
            case "strengthen.error.insufficient_funds" -> "gui.gem.upgrade_insufficient_funds";
            case "strengthen.error.economy_provider_unavailable" -> "gui.gem.upgrade_economy_unavailable";
            case "strengthen.error.rebuild_failed" -> "gui.gem.upgrade_write_failed";
            case "strengthen.error.no_target", "strengthen.enhancement.invalid_request" -> "gui.gem.upgrade_target_required";
            case "strengthen.error.compensation_pending" -> "gui.gem.upgrade_compensation_pending";
            default -> "gui.gem.upgrade_failed";
        };
        Map<String, Object> values = new java.util.LinkedHashMap<>(placeholders == null ? Map.of() : placeholders);
        values.put("reason", Texts.toStringSafe(reasonKey));
        plugin.messageService().send(player, messageKey, values);
    }

    private void executeInlay(GemGuiSession state, GemGuiSession.PendingOperation pendingOperation, Player player, ItemStack targetItem) {
        GemInlayService.InlayResult inlayResult = plugin.inlayService().inlayDirect(
                player, targetItem, pendingOperation.inputItem(), pendingOperation.slotIndex(), false, true);
        GemInlayService.Result result = inlayResult.result();
        String messageKey = result.messageKey();
        if (Texts.isNotBlank(messageKey)) {
            plugin.messageService().send(player, messageKey, result.placeholders());
        }
        if (result.success()) {
            if (inlayResult.updatedEquipment() != null && !inlayResult.updatedEquipment().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, inlayResult.updatedEquipment());
            }
            state.setTargetItem(null);
            state.clearPendingOperation();
            inlayResult.commit();
        } else {
            state.setTargetItemPreservingPending(inlayResult.updatedEquipment());
            if (result.inputConsumed()) {
                state.clearPendingOperation();
            }
        }
        renderer.refreshGui(state);
    }

    private void executeExtract(GemGuiSession state, GemGuiSession.PendingOperation pendingOperation, Player player, ItemStack targetItem) {
        GemInlayService.ExtractDirectResult extractResult = plugin.inlayService().extractDirect(
                player, targetItem, pendingOperation.slotIndex(), false);
        GemExtractService.Result result = extractResult.result();
        String messageKey = result.messageKey();
        if (Texts.isNotBlank(messageKey)) {
            plugin.messageService().send(player, messageKey, result.placeholders());
        }
        if (result.success()) {
            if (extractResult.updatedEquipment() != null && !extractResult.updatedEquipment().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, extractResult.updatedEquipment());
            }
            state.setTargetItem(null);
            if (extractResult.returnedGem() != null && !extractResult.returnedGem().getType().isAir()) {
                InventoryItemUtil.giveOrDrop(player, extractResult.returnedGem());
            }
            extractResult.commit();
        } else {
            state.setTargetItem(extractResult.updatedEquipment());
        }
        state.clearPendingOperation();
        renderer.refreshGui(state);
    }

    private void returnPendingInput(GemGuiSession state) {
        if (state == null) {
            return;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        ItemStack inputItem = pendingOperation.inputItem();
        state.clearPendingOperation();
        if (inputItem != null && !inputItem.getType().isAir()) {
            InventoryItemUtil.giveOrDrop(state.player(), inputItem);
        }
    }

    private boolean isPendingInlaySlot(GemGuiSession state, int slotIndex) {
        if (state == null) {
            return false;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        return pendingOperation.type() == GemGuiSession.PendingType.INLAY
                && pendingOperation.slotIndex() == slotIndex
                && pendingOperation.inputItem() != null;
    }

    private void returnPendingInputToCursor(GemGuiSession state, GuiClickContext click) {
        if (state == null || click == null) {
            return;
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        ItemStack inputItem = pendingOperation.inputItem();
        state.clearPendingOperation();
        if (inputItem != null && !inputItem.getType().isAir()) {
            click.setCursor(inputItem);
        }
    }

    private void switchMode(GemGuiSession state, GemGuiMode mode) {
        if (state.mode() == mode) {
            renderer.refreshGui(state);
            return;
        }
        returnPendingInput(state);
        if (state.mode() == GemGuiMode.UPGRADE && mode != GemGuiMode.UPGRADE) {
            returnUpgradeMaterials(state);
        }
        if (state.rerollMode() && plugin.rerollSessionService() != null) {
            plugin.rerollSessionService().abandon(state.player().getUniqueId(),
                    GemRerollSessionService.TerminationReason.USER_CANCEL);
        }
        state.setMode(mode);
        renderer.refreshGui(state);
    }

    private void returnUpgradeMaterials(GemGuiSession state) {
        if (state == null) {
            return;
        }
        for (ItemStack material : state.takeUpgradeMaterials()) {
            if (material != null && !material.getType().isAir()) {
                InventoryItemUtil.giveOrDrop(state.player(), material);
            }
        }
    }

    private ItemStack consumeOneFromCursor(GuiClickContext click) {
        ItemStack cursorItem = InventoryItemUtil.cloneNonAir(click.cursorItem());
        if (cursorItem == null) {
            return null;
        }
        ItemStack taken = cursorItem.clone();
        taken.setAmount(1);
        if (cursorItem.getAmount() <= 1) {
            click.setCursor(null);
        } else {
            cursorItem.setAmount(cursorItem.getAmount() - 1);
            click.setCursor(cursorItem);
        }
        return taken;
    }

    private final class GemSessionHandler implements GuiSessionHandler {

        private final GemGuiSession state;

        private GemSessionHandler(GemGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (Texts.lower(slot.definition().type())) {
                case "target_item" -> handleTargetItemClick(state, click);
                case "mode_inlay" -> switchMode(state, GemGuiMode.INLAY);
                case "mode_upgrade" -> switchMode(state, GemGuiMode.UPGRADE);
                case "mode_extract" -> switchMode(state, GemGuiMode.EXTRACT);
                case "mode_reroll_full" -> switchMode(state, GemGuiMode.REROLL_FULL);
                case "mode_reroll_value" -> switchMode(state, GemGuiMode.REROLL_VALUE);
                case "socket_slot" -> handleSocketClick(state, click, slot.slotIndex());
                case "confirm" -> handleConfirm(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
            if (click.isBlockedTransfer()) {
                click.setCancelled(true);
                return;
            }
            scheduleRefresh(state);
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            if (state.templateSwitching()) {
                state.setTemplateSwitching(false);
                return;
            }
            if (state.rerollMode() && plugin.rerollSessionService() != null) {
                plugin.rerollSessionService().abandon(state.player().getUniqueId(),
                        GemRerollSessionService.TerminationReason.GUI_CLOSE);
            }
            ItemStack cursorItem = close != null && close.player() != null
                    ? InventoryItemUtil.cloneNonAir(close.player().getItemOnCursor())
                    : null;
            ItemStack pendingInput = state.pendingOperation().inputItem();
            if (cursorItem != null) {
                close.player().setItemOnCursor(null);
            }
            if (state.mutableTargetItem() != null && state.returnTargetOnClose()) {
                InventoryItemUtil.giveOrDrop(state.player(), state.mutableTargetItem());
                state.setTargetItem(null);
            }
            if (pendingInput != null) {
                InventoryItemUtil.giveOrDrop(state.player(), pendingInput);
            }
            returnUpgradeMaterials(state);
            state.clearPendingOperation();
            if (cursorItem != null) {
                InventoryItemUtil.giveOrDrop(state.player(), cursorItem);
            }
            stateManager.remove(state);
        }
    }
}

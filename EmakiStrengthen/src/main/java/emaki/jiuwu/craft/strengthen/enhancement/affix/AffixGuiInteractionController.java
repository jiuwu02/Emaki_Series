package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptPreview;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptResult;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementPreviewSession;

final class AffixGuiInteractionController {

    private final EmakiStrengthenPlugin plugin;
    private final AffixGuiRenderer renderer;
    private final AffixSelectionService selectionService;
    private final AffixLayerCodec layerCodec;

    AffixGuiInteractionController(EmakiStrengthenPlugin plugin,
            AffixGuiRenderer renderer,
            AffixSelectionService selectionService,
            AffixLayerCodec layerCodec) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.selectionService = selectionService;
        this.layerCodec = layerCodec;
    }

    GuiSessionHandler createSessionHandler(AffixGuiSession state, Runnable onClose) {
        return new GuiSessionHandler() {

            @Override
            public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
                if (slot == null || slot.definition() == null || state.processing()) {
                    return;
                }
                String type = Texts.lower(slot.definition().type());
                switch (type) {
                    case "target_item" -> handleTargetSwap(click, state);
                    case "affix_select" -> handleAffixCycle(click, state);
                    case "confirm" -> handleConfirm(state);
                    default -> {
                        if (type.startsWith("material_input_")) {
                            int index = parseMaterialIndex(type);
                            if (index >= 0) {
                                handleMaterialSwap(click, state, index);
                            }
                        }
                    }
                }
            }

            @Override
            public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
                if (click != null && click.isShiftClick() && !state.processing()) {
                    handleShiftFromPlayerInventory(click, state);
                }
            }

            @Override
            public void onClose(GuiSession session, GuiCloseContext close) {
                ItemStack cursorItem = close != null && close.player() != null
                        ? AffixGuiSession.cloneNonAir(close.player().getItemOnCursor())
                        : null;
                if (cursorItem != null) {
                    close.player().setItemOnCursor(null);
                }
                returnStoredItems(state);
                if (cursorItem != null) {
                    giveBack(state.player(), cursorItem);
                }
                selectionService.clear(state.player() == null ? null : state.player().getUniqueId());
                if (onClose != null) {
                    onClose.run();
                }
            }
        };
    }

    private void handleAffixCycle(GuiClickContext click, AffixGuiSession state) {
        if (click == null || state.targetItem() == null) {
            return;
        }
        List<String> candidates = state.candidates();
        if (candidates.isEmpty()) {
            return;
        }
        UUID playerId = state.player().getUniqueId();
        if (click.isLeftClick()) {
            selectionService.selectNext(playerId, candidates);
        } else if (click.isRightClick()) {
            selectionService.selectPrevious(playerId, candidates);
        } else {
            return;
        }
        refresh(state);
    }

    private void handleTargetSwap(GuiClickContext click, AffixGuiSession state) {
        ItemStack cursor = AffixGuiSession.cloneNonAir(click.cursorItem());
        if (cursor != null) {
            ItemStack previous = AffixGuiSession.cloneNonAir(state.targetItem());
            state.setTargetItem(cursor);
            click.setCursor(previous);
            refresh(state);
            return;
        }
        ItemStack removed = AffixGuiSession.cloneNonAir(state.targetItem());
        if (removed == null) {
            return;
        }
        state.setTargetItem(null);
        if (click.isShiftClick()) {
            giveBack(state.player(), removed);
        } else {
            click.setCursor(removed);
        }
        refresh(state);
    }

    private void handleMaterialSwap(GuiClickContext click, AffixGuiSession state, int index) {
        ItemStack cursor = AffixGuiSession.cloneNonAir(click.cursorItem());
        if (cursor != null) {
            ItemStack previous = AffixGuiSession.cloneNonAir(state.materialInput(index));
            state.setMaterialInput(index, cursor);
            click.setCursor(previous);
            refresh(state);
            return;
        }
        ItemStack removed = AffixGuiSession.cloneNonAir(state.materialInput(index));
        if (removed == null) {
            return;
        }
        state.setMaterialInput(index, null);
        if (click.isShiftClick()) {
            giveBack(state.player(), removed);
        } else {
            click.setCursor(removed);
        }
        refresh(state);
    }

    private void handleShiftFromPlayerInventory(GuiClickContext click, AffixGuiSession state) {
        ItemStack itemStack = AffixGuiSession.cloneNonAir(click.currentItem());
        if (itemStack == null) {
            return;
        }
        if (state.targetItem() == null && hasAffixCandidates(itemStack)) {
            state.setTargetItem(itemStack);
            click.clearClickedSlot();
            refresh(state);
            return;
        }
        int slotIndex = state.firstEmptyMaterialSlot();
        if (slotIndex < 0) {
            return;
        }
        state.setMaterialInput(slotIndex, itemStack);
        click.clearClickedSlot();
        refresh(state);
    }

    private void handleConfirm(AffixGuiSession state) {
        if (state.processing() || state.targetItem() == null || plugin.enhancementAttemptService() == null) {
            return;
        }
        EnhancementAttemptPreview preview = state.preview();
        if (preview == null || !preview.valid()) {
            return;
        }
        if (Texts.isBlank(state.operationId())) {
            state.setOperationId(UUID.randomUUID().toString());
        }
        state.setProcessing(true);
        EnhancementAttemptResult result;
        try {
            ItemStack target = state.targetItem();
            result = plugin.enhancementAttemptService().attemptWithPreview(
                    state.player(), state.recipe(), target, state.suppliedMaterials(), state.operationId(),
                    state.previewSession());
            if (result.committed()) {
                state.setTargetItem(target);
                consumeSuppliedMaterials(state);
            }
        } finally {
            state.setProcessing(false);
        }
        if (!"strengthen.error.compensation_pending".equals(result.errorKey())) {
            state.clearOperationId();
        }
        if (!result.committed()) {
            plugin.messageService().send(state.player(), result.errorKey(), result.placeholders());
        } else {
            plugin.messageService().send(state.player(), result.success()
                    ? "strengthen.affix.success"
                    : "strengthen.affix.failure", result.toPlaceholders());
        }
        refresh(state);
    }

    private void consumeSuppliedMaterials(AffixGuiSession state) {
        for (int index = 0; index < state.materialInputs().size(); index++) {
            ItemStack current = state.materialInput(index);
            if (current != null && current.getAmount() <= 0) {
                state.setMaterialInput(index, null);
            }
        }
    }

    private boolean hasAffixCandidates(ItemStack itemStack) {
        int maxLevel = plugin.appConfig() == null ? 0 : plugin.appConfig().affixMaxLevel();
        return !selectionService.enhanceableAffixes(itemStack, maxLevel).isEmpty();
    }

    void refresh(AffixGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        recomputeState(state);
        state.guiSession().refresh();
    }

    void recomputeState(AffixGuiSession state) {
        ItemStack target = state.targetItem();
        if (target == null) {
            state.setCandidates(List.of());
            state.setSelectedAffix("");
            state.setCapacity(0, 0);
            state.setPreview(null);
            state.setPreviewSession(null);
            return;
        }
        int maxLevel = plugin.appConfig() == null ? 0 : plugin.appConfig().affixMaxLevel();
        int capacityMax = plugin.appConfig() == null ? 0 : plugin.appConfig().affixCapacityMax();
        List<String> candidates = selectionService.enhanceableAffixes(target, maxLevel);
        state.setCandidates(candidates);
        UUID playerId = state.player().getUniqueId();
        state.setSelectedAffix(selectionService.selected(playerId, candidates));
        AffixLayer layer = layerCodec.readOrEmpty(target, capacityMax);
        state.setCapacity(layer.capacityUsed(), layer.capacityMax());
        if (plugin.enhancementAttemptService() == null) {
            state.setPreview(null);
            state.setPreviewSession(null);
            return;
        }
        EnhancementPreviewSession session = plugin.enhancementAttemptService().previewSession(
                state.player(), state.recipe(), target, state.suppliedMaterials());
        state.setPreview(session.preview());
        state.setPreviewSession(session);
    }

    private void returnStoredItems(AffixGuiSession state) {
        Player player = state.player();
        ItemStack target = state.targetItem();
        if (target != null) {
            giveBack(player, target);
        }
        for (ItemStack material : state.materialInputs()) {
            if (material != null) {
                giveBack(player, material);
            }
        }
        state.clearStoredItems();
    }

    private void giveBack(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        leftover.values().forEach(remaining
                -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
    }

    private static int parseMaterialIndex(String type) {
        try {
            return Integer.parseInt(type.substring("material_input_".length())) - 1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}

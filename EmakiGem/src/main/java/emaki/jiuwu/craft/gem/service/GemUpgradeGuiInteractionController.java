package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;

final class GemUpgradeGuiInteractionController {

    private final EmakiGemPlugin plugin;
    private final GemGuiStateManager stateManager;
    private final GemUpgradeGuiRenderer renderer;
    private final GemGuiService service;

    GemUpgradeGuiInteractionController(EmakiGemPlugin plugin,
            GemGuiStateManager stateManager,
            GemUpgradeGuiRenderer renderer,
            GemGuiService service) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.renderer = renderer;
        this.service = service;
    }

    public GuiSessionHandler createSessionHandler(GemUpgradeGuiSession state) {
        return new GemUpgradeSessionHandler(state);
    }

    private void scheduleSwitchIfNeeded(GemUpgradeGuiSession state) {
        if (state == null) {
            return;
        }
        reconcileMaterialItems(state);
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(state.mutableTargetGem());
        GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        GuiTemplate template = GemGuiTemplates.resolveUpgradeTemplate(plugin.guiTemplateLoader(), definition);
        String resolvedId = template == null ? "" : template.id();
        if (!resolvedId.equals(state.currentTemplateId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!service.switchUpgradeTemplate(state)) {
                    renderer.refreshGui(state);
                }
            });
            return;
        }
        renderer.refreshGui(state);
    }

    private void handleTargetGemClick(GemUpgradeGuiSession state, InventoryClickEvent event) {
        if (!isCursorExchangeClick(event)) {
            return;
        }
        ItemStack cursorItem = GemUpgradeGuiSession.cloneNonAir(event.getCursor());
        ItemStack currentGem = state.targetGem();
        if (cursorItem != null && plugin.itemMatcher().readGemInstance(cursorItem) == null) {
            plugin.messageService().send(state.player(), "gui.upgrade.invalid_gem");
            return;
        }
        returnAllMaterialItems(state);
        state.setTargetGem(cursorItem);
        event.getWhoClicked().setItemOnCursor(currentGem);
        scheduleSwitchIfNeeded(state);
    }

    private boolean isCursorExchangeClick(InventoryClickEvent event) {
        if (event == null) {
            return false;
        }
        ClickType click = event.getClick();
        return click == ClickType.LEFT
                || click == ClickType.RIGHT;
    }

    private void handleMaterialSlotClick(GemUpgradeGuiSession state, InventoryClickEvent event, int displayIndex) {
        if (!isCursorExchangeClick(event)) {
            return;
        }
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible() || displayIndex < 0 || displayIndex >= preview.upgradeLevel().materials().size()) {
            return;
        }
        GemDefinition.MaterialCost requiredMaterial = preview.upgradeLevel().materials().get(displayIndex);
        ItemStack currentItem = state.materialItem(displayIndex);
        ItemStack cursorItem = GemUpgradeGuiSession.cloneNonAir(event.getCursor());
        if (cursorItem == null) {
            if (currentItem == null) {
                return;
            }
            state.clearMaterialItem(displayIndex);
            event.getWhoClicked().setItemOnCursor(currentItem);
            renderer.refreshGui(state);
            return;
        }
        if (!matchesRequiredMaterial(requiredMaterial, cursorItem)) {
            plugin.messageService().send(state.player(), "gui.upgrade.invalid_material", Map.of(
                    "material", materialLabel(requiredMaterial)
            ));
            return;
        }
        if (currentItem == null) {
            state.setMaterialItem(displayIndex, cursorItem);
            event.getWhoClicked().setItemOnCursor(null);
            renderer.refreshGui(state);
            return;
        }
        if (currentItem.isSimilar(cursorItem)) {
            int maxStackSize = Math.max(1, currentItem.getMaxStackSize());
            int mergedAmount = Math.min(maxStackSize, currentItem.getAmount() + cursorItem.getAmount());
            int remainder = currentItem.getAmount() + cursorItem.getAmount() - mergedAmount;
            currentItem.setAmount(mergedAmount);
            state.setMaterialItem(displayIndex, currentItem);
            if (remainder <= 0) {
                event.getWhoClicked().setItemOnCursor(null);
            } else {
                cursorItem.setAmount(remainder);
                event.getWhoClicked().setItemOnCursor(cursorItem);
            }
            renderer.refreshGui(state);
            return;
        }
        state.setMaterialItem(displayIndex, cursorItem);
        event.getWhoClicked().setItemOnCursor(currentItem);
        renderer.refreshGui(state);
    }

    private void handleConfirm(GemUpgradeGuiSession state) {
        if (state.mutableTargetGem() == null) {
            plugin.messageService().send(state.player(), "command.upgrade.hold_gem");
            return;
        }
        Player player = state.player();
        ItemStack currentOffHand = player.getInventory().getItemInOffHand();
        var hands = InventoryItemUtil.withTemporaryHands(
                player,
                state.targetGem(),
                currentOffHand,
                () -> {
                    GemUpgradeService.Result r = plugin.upgradeService().upgradeHeldGemWithGuiMaterials(
                            player, false, state.mutableMaterialItems()
                    );
                    state.mutableMaterialItems().entrySet().removeIf(
                            entry -> entry.getValue() == null || entry.getValue().getType().isAir()
                    );
                    return r;
                }
        );
        GemUpgradeService.Result result = hands.result();
        ItemStack updatedGem = hands.updatedMainHand();
        plugin.messageService().send(player, result.messageKey(), result.placeholders());
        if (result.success() && updatedGem != null) {
            InventoryItemUtil.giveOrDrop(player, updatedGem);
            state.setTargetGem(null);
        } else {
            state.setTargetGem(updatedGem);
        }
        scheduleSwitchIfNeeded(state);
    }

    private void reconcileMaterialItems(GemUpgradeGuiSession state) {
        if (state == null) {
            return;
        }
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible()) {
            returnAllMaterialItems(state);
            return;
        }
        List<GemDefinition.MaterialCost> materials = preview.upgradeLevel().materials();
        List<Integer> invalidIndexes = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> entry : state.mutableMaterialItems().entrySet()) {
            int index = entry.getKey();
            ItemStack itemStack = entry.getValue();
            if (itemStack == null || itemStack.getType().isAir()) {
                invalidIndexes.add(index);
                continue;
            }
            if (index < 0 || index >= materials.size() || !matchesRequiredMaterial(materials.get(index), itemStack)) {
                InventoryItemUtil.giveOrDrop(state.player(), itemStack);
                invalidIndexes.add(index);
            }
        }
        invalidIndexes.forEach(state::clearMaterialItem);
    }

    private void returnAllMaterialItems(GemUpgradeGuiSession state) {
        if (state == null || state.mutableMaterialItems().isEmpty()) {
            return;
        }
        for (ItemStack itemStack : new ArrayList<>(state.mutableMaterialItems().values())) {
            InventoryItemUtil.giveOrDrop(state.player(), itemStack);
        }
        state.clearMaterialItems();
    }

    private GemUpgradeService.UpgradePreview preview(GemUpgradeGuiSession state) {
        return state == null || state.mutableTargetGem() == null
                ? GemUpgradeService.UpgradePreview.failure("command.upgrade.hold_gem")
                : plugin.upgradeService().preview(state.mutableTargetGem());
    }

    private boolean matchesRequiredMaterial(GemDefinition.MaterialCost materialCost, ItemStack itemStack) {
        if (materialCost == null || materialCost.itemSource() == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        if (plugin.coreItemSourceService() == null) {
            return false;
        }
        ItemSource identified = plugin.coreItemSourceService().identifyItem(itemStack);
        return ItemSourceUtil.matches(materialCost.itemSource(), identified);
    }

    private String materialLabel(GemDefinition.MaterialCost materialCost) {
        if (materialCost == null || materialCost.itemSource() == null) {
            return "unknown";
        }
        if (plugin.coreItemSourceService() != null) {
            String displayName = plugin.coreItemSourceService().displayName(materialCost.itemSource());
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        String shorthand = ItemSourceUtil.toShorthand(materialCost.itemSource());
        return Texts.isBlank(shorthand) ? materialCost.itemSource().getIdentifier() : shorthand;
    }

    private ItemStack resolveClosingTargetGem(GemUpgradeGuiSession state, GuiSession session, InventoryCloseEvent event) {
        ItemStack inventoryTarget = cloneTargetGemFromInventory(session, event);
        if (inventoryTarget != null) {
            return inventoryTarget;
        }
        return GemUpgradeGuiSession.cloneNonAir(state == null ? null : state.mutableTargetGem());
    }

    private ItemStack cloneTargetGemFromInventory(GuiSession session, InventoryCloseEvent event) {
        Inventory inventory = event == null ? null : event.getInventory();
        if (inventory == null || session == null || session.template() == null) {
            return null;
        }
        for (var slot : session.template().slotsByType("target_gem")) {
            if (slot == null || slot.slots() == null) {
                continue;
            }
            for (Integer inventorySlot : slot.slots()) {
                if (inventorySlot == null || inventorySlot < 0 || inventorySlot >= inventory.getSize()) {
                    continue;
                }
                ItemStack candidate = GemUpgradeGuiSession.cloneNonAir(inventory.getItem(inventorySlot));
                if (candidate == null || plugin.itemMatcher().readGemInstance(candidate) == null) {
                    continue;
                }
                return candidate;
            }
        }
        return null;
    }

    private final class GemUpgradeSessionHandler implements GuiSessionHandler {

        private final GemUpgradeGuiSession state;

        private GemUpgradeSessionHandler(GemUpgradeGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (Texts.lower(slot.definition().type())) {
                case "target_gem" -> handleTargetGemClick(state, event);
                case "material_slot" -> handleMaterialSlotClick(state, event, slot.slotIndex());
                case "confirm" -> handleConfirm(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
            if (GuiSessionHandler.isBlockedTransfer(event)) {
                event.setCancelled(true);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> renderer.refreshGui(state));
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            if (state.templateSwitching()) {
                state.setTemplateSwitching(false);
                return;
            }
            ItemStack targetGem = resolveClosingTargetGem(state, session, event);
            Map<Integer, ItemStack> materialItems = new LinkedHashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : state.mutableMaterialItems().entrySet()) {
                ItemStack cloned = GemUpgradeGuiSession.cloneNonAir(entry.getValue());
                if (cloned != null) {
                    materialItems.put(entry.getKey(), cloned);
                }
            }
            ItemStack cursorItem = event != null && event.getPlayer() != null
                    ? GemUpgradeGuiSession.cloneNonAir(event.getPlayer().getItemOnCursor())
                    : null;
            if (cursorItem != null) {
                event.getPlayer().setItemOnCursor(null);
            }
            state.setTargetGem(null);
            state.clearMaterialItems();
            stateManager.remove(state);
            if (targetGem == null && materialItems.isEmpty() && cursorItem == null) {
                return;
            }
            Player player = state.player();
            returnItemsImmediately(player, targetGem, materialItems, cursorItem);
        }
    }

    private void returnItemsImmediately(Player player, ItemStack targetGem, Map<Integer, ItemStack> materialItems, ItemStack cursorItem) {
        if (targetGem != null) {
            InventoryItemUtil.giveOrDrop(player, targetGem);
        }
        for (ItemStack itemStack : materialItems.values()) {
            InventoryItemUtil.giveOrDrop(player, itemStack);
        }
        if (cursorItem != null) {
            InventoryItemUtil.giveOrDrop(player, cursorItem);
        }
    }
}

package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.service.ForgeGuiService.ForgeGuiSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

final class ForgeGuiRenderer {

    private final EmakiForgePlugin plugin;
    private final ForgeGuiStateSupport stateSupport;

    ForgeGuiRenderer(EmakiForgePlugin plugin, ForgeGuiStateSupport stateSupport) {
        this.plugin = plugin;
        this.stateSupport = stateSupport;
    }

    public ItemStack renderSlot(ForgeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = stateSupport.normalizedType(slot);
        ItemStack dynamic = switch (type) {
            case "blueprint_inputs" -> ForgeGuiStateSupport.cloneNonAir(state.blueprintItems().get(resolvedSlot.inventorySlot()));
            case "target_item" -> ForgeGuiStateSupport.cloneNonAir(state.targetItem());
            case "required_materials" -> ForgeGuiStateSupport.cloneNonAir(state.requiredMaterialItems().get(resolvedSlot.inventorySlot()));
            case "optional_materials" -> ForgeGuiStateSupport.cloneNonAir(state.optionalMaterialItems().get(resolvedSlot.inventorySlot()));
            case "capacity_display" -> buildCapacityDisplayItem(slot, state);
            case "confirm" -> buildConfirmItem(slot, state);
            case "result_preview" -> buildResultPreview(state);
            default -> null;
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    public Map<String, Object> titleReplacements(ForgeGuiSession state) {
        return Map.of("recipe", state.recipe() == null ? "通用锻造" : Texts.stripMiniTags(state.recipe().displayName()));
    }

    public void refreshGui(ForgeGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        stateSupport.refreshDerivedValues(state);
        state.guiSession().refresh();
    }

    private ItemStack buildCapacityDisplayItem(GuiSlot slot, ForgeGuiSession state) {
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    private ItemStack buildConfirmItem(GuiSlot slot, ForgeGuiSession state) {
        if (state.maxCapacity() > 0 && state.currentCapacity() > state.maxCapacity()) {
            return GuiItemBuilder.build(
                "BARRIER",
                new ItemComponentParser.ItemComponents(
                    "<red>无法锻造</red>",
                    true,
                    List.of(
                        "<gray>当前容量: <yellow>{current}/{max}</yellow></gray>",
                        "<red>可选材料容量已超出上限</red>",
                        "<gray>减少材料后再试一次</gray>"
                    ),
                    null,
                    null,
                    Map.of(),
                    List.of()
                ),
                1,
                slotReplacements(state),
                plugin.itemIdentifierService()::createItem
            );
        }
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    private ItemStack buildResultPreview(ForgeGuiSession state) {
        Recipe preview = state.previewRecipe();
        if (preview == null || preview.result() == null || preview.result().outputItem() == null) {
            return null;
        }
        ForgeService.PreparedForge preparedForge = state.preparedForge();
        if (preparedForge == null) {
            preparedForge = plugin.forgeService().prepareForge(
                state.player(),
                preview,
                state.toGuiItems(),
                state.previewSeed(),
                state.previewForgedAt()
            );
            state.setPreparedForge(preparedForge);
        }
        if (preparedForge == null || preparedForge.previewItem() == null) {
            return null;
        }
        return preparedForge.previewItem().clone();
    }

    private Map<String, Object> slotReplacements(ForgeGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("recipe", state.recipe() == null ? "通用锻造" : Texts.stripMiniTags(state.recipe().displayName()));
        replacements.put("current", state.currentCapacity());
        replacements.put("max", state.maxCapacity() <= 0 ? "?" : state.maxCapacity());
        replacements.put("capacity_state", capacityStateText(state));
        return replacements;
    }

    private String capacityStateText(ForgeGuiSession state) {
        if (state.maxCapacity() <= 0) {
            return "<gray>等待图纸</gray>";
        }
        if (state.currentCapacity() > state.maxCapacity()) {
            return "<red>已超限</red>";
        }
        if (state.currentCapacity() >= Math.max(1, (int) Math.ceil(state.maxCapacity() * 0.8D))) {
            return "<gold>接近上限</gold>";
        }
        return "<green>正常</green>";
    }
}

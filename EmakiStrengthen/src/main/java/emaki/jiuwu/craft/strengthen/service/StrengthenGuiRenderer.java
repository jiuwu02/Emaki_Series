package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

final class StrengthenGuiRenderer {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;

    StrengthenGuiRenderer(EmakiStrengthenPlugin plugin, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.attemptService = attemptService;
    }

    public ItemStack renderSlot(StrengthenGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        ItemStack dynamic = switch (type) {
            case "target_item" -> StrengthenGuiSession.cloneNonAir(state.targetItem());
            case "material_input_1" -> StrengthenGuiSession.cloneNonAir(state.materialInput(0));
            case "material_input_2" -> StrengthenGuiSession.cloneNonAir(state.materialInput(1));
            case "material_input_3" -> StrengthenGuiSession.cloneNonAir(state.materialInput(2));
            case "material_input_4" -> StrengthenGuiSession.cloneNonAir(state.materialInput(3));
            case "preview_display" -> buildPreviewItem(state);
            case "temper_display" -> buildTemperItem(state);
            case "confirm" -> buildConfirmItem(state);
            default -> null;
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, Map.of(), (source, amount) -> plugin.coreItemFactory().create(source, amount));
    }

    public void refreshGui(StrengthenGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.setPreview(attemptService.preview(state.player(), state.toAttemptContext()));
        state.guiSession().refresh();
    }

    private ItemStack buildPreviewItem(StrengthenGuiSession state) {
        AttemptPreview preview = state.preview();
        if (state.targetItem() == null) {
            return buildItem("BOOK", "<gold>强化预览</gold>", List.of(
                    "<gray>放入待强化装备后显示成功率、材料与花费</gray>",
                    "<gray>固定材料会直接从背包中扣除</gray>"
            ));
        }
        if (preview == null || !preview.eligible()) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>当前无法强化</gray>");
            if (preview != null && Texts.isNotBlank(preview.errorKey())) {
                lore.add("<red>" + plugin.messageService().message(preview.errorKey(), Map.of()) + "</red>");
            }
            appendMaterialLines(lore, preview);
            return buildItem("BOOK", "<red>强化条件未满足</red>", lore);
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>当前星级: <gold>+" + preview.currentStar() + "</gold></gray>");
        lore.add("<gray>目标星级: <yellow>+" + preview.targetStar() + "</yellow></gray>");
        lore.add("<gray>成功率: <green>" + Numbers.formatNumber(preview.successRate(), "0.##") + "%</green></gray>");
        if (preview.costs().isEmpty()) {
            lore.add("<gray>花费: <green>免费</green></gray>");
        } else {
            lore.add("<gray>花费:</gray>");
            for (AttemptCost cost : preview.costs()) {
                lore.add("<gold> - " + cost.amount() + " " + cost.displayName() + "</gold>");
            }
        }
        lore.add(preview.protectionApplied()
                ? plugin.messageService().message("strengthen.preview.failure_drop_protected")
                : (preview.failureStar() < preview.currentStar()
                ? plugin.messageService().message("strengthen.preview.failure_drop")
                : plugin.messageService().message("strengthen.preview.failure_keep")));
        appendMaterialLines(lore, preview);
        if (!preview.successDeltaStats().isEmpty()) {
            lore.add("<gray>强化提升:</gray>");
            preview.successDeltaStats().forEach((id, value)
                    -> lore.add("<gold> - " + id + " +" + Numbers.formatNumber(value, "0.##") + "</gold>"));
        }
        return buildItem("BOOK", "<gold>强化预览</gold>", lore);
    }

    private void appendMaterialLines(List<String> lore, AttemptPreview preview) {
        if (lore == null || preview == null) {
            return;
        }
        if (!preview.requiredMaterials().isEmpty()) {
            lore.add("<gray>固定材料:</gray>");
            for (AttemptMaterial material : preview.requiredMaterials()) {
                String color = material.satisfied() ? "<green>" : "<red>";
                lore.add(color + " - " + materialDisplayName(material.item()) + " x" + material.requiredAmount()
                        + " <gray>(" + material.availableAmount() + "/" + material.requiredAmount() + ")</gray>");
            }
        }
        boolean hasOptional = preview.optionalMaterials().stream().anyMatch(material -> material != null && Texts.isNotBlank(material.item()));
        if (hasOptional) {
            lore.add("<gray>已放入可选材料:</gray>");
            for (AttemptMaterial material : preview.optionalMaterials()) {
                if (material == null || Texts.isBlank(material.item())) {
                    continue;
                }
                lore.add("<aqua> - " + materialDisplayName(material.item()) + " x" + material.availableAmount() + "</aqua>");
            }
        }
    }

    private String materialDisplayName(String item) {
        if (Texts.isBlank(item)) {
            return "未知材料";
        }
        ItemSource source = ItemSourceUtil.parseShorthand(item);
        if (source == null || plugin.coreItemSourceService() == null) {
            return item;
        }
        String displayName = plugin.coreItemSourceService().displayName(source);
        return Texts.isBlank(displayName) ? item : displayName;
    }

    private ItemStack buildTemperItem(StrengthenGuiSession state) {
        StrengthenState strengthenState = state.preview() == null ? null : state.preview().state();
        int temper = strengthenState == null ? 0 : strengthenState.temperLevel();
        int maxTemper = state.preview() == null || state.preview().recipe() == null ? 0 : state.preview().recipe().limits().maxTemper();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>当前锻印: <gold>" + temper + "/" + maxTemper + "</gold></gray>");
        if (state.preview() != null && state.preview().appliedTemperBonus() > 0) {
            lore.add("<gray>本次额外锻印加成: <aqua>+" + state.preview().appliedTemperBonus() + "</aqua></gray>");
        }
        lore.add("<gray>失败后会累积锻印，提高后续成功率</gray>");
        return buildItem("INK_SAC", "<gold>锻印状态</gold>", lore);
    }

    private ItemStack buildConfirmItem(StrengthenGuiSession state) {
        AttemptPreview preview = state.preview();
        if (preview == null || !preview.eligible()) {
            return buildItem("BARRIER", "<red>无法强化</red>", List.of("<gray>请先满足所有强化条件</gray>"));
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>成功率: <green>" + Numbers.formatNumber(preview.successRate(), "0.##") + "%</green></gray>");
        if (preview.costs().isEmpty()) {
            lore.add("<gray>花费: <green>免费</green></gray>");
        } else {
            for (AttemptCost cost : preview.costs()) {
                lore.add("<gray>花费: <gold>" + cost.amount() + " " + cost.displayName() + "</gold></gray>");
            }
        }
        return buildItem("ANVIL", "<green>确认强化</green>", lore);
    }

    private ItemStack buildItem(String item, String name, List<String> lore) {
        return GuiItemBuilder.build(
                item,
                new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of()),
                1,
                Map.of(),
                (source, amount) -> plugin.coreItemFactory().create(source, amount)
        );
    }
}

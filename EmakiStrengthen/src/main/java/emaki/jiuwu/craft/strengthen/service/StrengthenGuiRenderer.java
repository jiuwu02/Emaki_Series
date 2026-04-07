package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;

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
            case "base_material" -> StrengthenGuiSession.cloneNonAir(state.baseMaterial());
            case "support_material" -> StrengthenGuiSession.cloneNonAir(state.supportMaterial());
            case "protection_material" -> StrengthenGuiSession.cloneNonAir(state.protectionMaterial());
            case "breakthrough_material" -> StrengthenGuiSession.cloneNonAir(state.breakthroughMaterial());
            case "preview_display" -> buildPreviewItem(state);
            case "crack_display" -> buildCrackItem(state);
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
        if (preview == null || !preview.eligible()) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>当前无法强化</gray>");
            if (preview != null && Texts.isNotBlank(preview.errorKey())) {
                lore.add("<red>" + plugin.messageService().message(preview.errorKey(), Map.of()) + "</red>");
            }
            return buildItem("BOOK", "<red>强化条件未满足</red>", lore);
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>当前星级: <gold>+" + preview.currentStar() + "</gold></gray>");
        lore.add("<gray>目标星级: <yellow>+" + preview.targetStar() + "</yellow></gray>");
        lore.add("<gray>成功率: <green>" + preview.successRate() + "%</green></gray>");
        lore.add("<gray>花费: <gold>" + preview.currencyCost() + " " + plugin.appConfig().economyCurrencyName() + "</gold></gray>");
        lore.add(preview.protectionApplied()
                ? plugin.messageService().message("strengthen.preview.failure_drop_protected")
                : (preview.failureStar() < preview.currentStar()
                ? plugin.messageService().message("strengthen.preview.failure_drop")
                : plugin.messageService().message("strengthen.preview.failure_keep")));
        if (!preview.successDeltaStats().isEmpty()) {
            lore.add("<gray>新增属性:</gray>");
            preview.successDeltaStats().forEach((id, value)
                    -> lore.add("<gold> - " + id + " +" + value + "</gold>"));
        }
        if (!preview.unlockingMilestones().isEmpty()) {
            lore.add("<gray>将解锁里程碑: <yellow>" + preview.unlockingMilestones() + "</yellow></gray>");
        }
        return buildItem("BOOK", "<gold>强化预览</gold>", lore);
    }

    private ItemStack buildCrackItem(StrengthenGuiSession state) {
        int crack = state.preview() == null || state.preview().state() == null ? 0 : state.preview().state().crackLevel();
        List<String> lore = List.of(
                "<gray>当前裂痕: <red>" + crack + "/" + plugin.appConfig().maxCrack() + "</red></gray>",
                "<gray>点击尝试使用净洗材料消除 1 层裂痕</gray>"
        );
        return buildItem("INK_SAC", "<gray>裂痕处理</gray>", lore);
    }

    private ItemStack buildConfirmItem(StrengthenGuiSession state) {
        AttemptPreview preview = state.preview();
        if (preview == null || !preview.eligible()) {
            return buildItem("BARRIER", "<red>无法强化</red>", List.of("<gray>请先满足所有强化条件</gray>"));
        }
        return buildItem("ANVIL", "<green>确认强化</green>", List.of(
                "<gray>成功率: <green>" + preview.successRate() + "%</green></gray>",
                "<gray>花费: <gold>" + preview.currencyCost() + " " + plugin.appConfig().economyCurrencyName() + "</gold></gray>"
        ));
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

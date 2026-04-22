package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

final class GemUpgradeGuiRenderer {

    private final EmakiGemPlugin plugin;

    GemUpgradeGuiRenderer(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack renderSlot(GemUpgradeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        return switch (Texts.lower(resolvedSlot.definition().type())) {
            case "target_gem" -> renderTargetGem(state, resolvedSlot);
            case "level_info" -> renderLevelInfo(state);
            case "material_slot" -> renderMaterialSlot(state, resolvedSlot.slotIndex());
            case "preview" -> renderPreview(state);
            case "success_rate" -> renderSuccessRate(state);
            case "confirm" -> renderConfirm(state);
            default -> null;
        };
    }

    public void refreshGui(GemUpgradeGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetGem(GemUpgradeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        ItemStack targetGem = state.targetGem();
        if (targetGem == null) {
            String item = resolvedSlot == null || resolvedSlot.definition() == null || Texts.isBlank(resolvedSlot.definition().item())
                    ? Material.RED_STAINED_GLASS_PANE.name()
                    : resolvedSlot.definition().item();
            return buildItem(item, "<red>放入宝石</red>", List.of(
                    "<gray>将未镶嵌的宝石物品放入此槽</gray>",
                    "<gray>支持从光标放入，也可点击取回</gray>"
            ));
        }
        return targetGem.clone();
    }

    private ItemStack renderLevelInfo(GemUpgradeGuiSession state) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add("<gray>请先放入可升级宝石</gray>");
            return buildItem(Material.BOOK, "<gold>等级信息</gold>", lore);
        }
        lore.add("<gray>宝石: <yellow>"
                + plugin.itemFactory().resolveGemDisplayName(preview.definition(), preview.instance().level())
                + "</yellow></gray>");
        lore.add("<gray>当前等级: <yellow>" + preview.instance().level() + "</yellow></gray>");
        lore.add("<gray>目标等级: <gold>" + preview.targetLevel() + "</gold></gray>");
        lore.add("<gray>最高等级: <aqua>" + preview.definition().upgrade().maxLevel() + "</aqua></gray>");
        return buildItem(Material.BOOK, "<gold>等级信息</gold>", lore);
    }

    private ItemStack renderMaterialSlot(GemUpgradeGuiSession state, int displayIndex) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible() || displayIndex >= preview.upgradeLevel().materials().size()) {
            return buildItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>材料槽</dark_gray>", List.of("<dark_gray>当前无材料预览</dark_gray>"));
        }
        GemDefinition.MaterialCost material = preview.upgradeLevel().materials().get(displayIndex);
        String itemName = materialDisplayName(material.itemSource());
        ItemStack placedItem = state.materialItem(displayIndex);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>材料: <yellow>" + itemName + "</yellow></gray>");
        lore.add("<gray>需要数量: <gold>x" + material.amount() + "</gold></gray>");
        if (placedItem != null) {
            return placedItem.clone();
        }
        lore.add("<gray>请放入对应物品源的材料</gray>");
        lore.add("<dark_gray>只有放入本界面的材料才会参与升级扣除</dark_gray>");
        ItemStack previewItem = material.itemSource() == null || plugin.coreItemSourceService() == null
                ? null
                : plugin.coreItemSourceService().createItem(material.itemSource(), 1);
        if (previewItem != null) {
            return GuiItemBuilder.apply(previewItem, new ItemComponentParser.ItemComponents(
                    "<aqua>升级材料</aqua>",
                    true,
                    lore,
                    null,
                    null,
                    Map.of(),
                    List.of()
            ), Map.of());
        }
        return buildItem(Material.BLAZE_POWDER, "<aqua>升级材料</aqua>", lore);
    }

    private ItemStack renderPreview(GemUpgradeGuiSession state) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add("<gray>这里会展示升级预览</gray>");
            lore.add("<gray>放入宝石后可查看材料与结果</gray>");
            return buildItem(Material.WRITABLE_BOOK, "<gold>升级预览</gold>", lore);
        }
        lore.add("<gray>升级后物品名: <yellow>"
                + plugin.itemFactory().resolveGemDisplayName(preview.definition(), preview.targetLevel())
                + "</yellow></gray>");
        List<GemDefinition.CurrencyCost> currencies = !preview.upgradeLevel().currencies().isEmpty()
                ? preview.upgradeLevel().currencies()
                : preview.definition().upgrade().currencies();
        if (!currencies.isEmpty()) {
            lore.add("<gray>经济消耗:</gray>");
            int currentLevel = preview.instance().level();
            for (GemDefinition.CurrencyCost currency : currencies) {
                double amount = currency.resolveAmount(Map.of(
                        "tier", preview.definition().tier(),
                        "current_level", currentLevel,
                        "target_level", preview.targetLevel()
                ));
                lore.add("<gold> - " + currency.provider() + ": " + amount + "</gold>");
            }
        }
        lore.add("<gray>升级材料必须全部放入本界面的材料槽</gray>");
        lore.add("<green>确认后将直接更新该宝石物品</green>");
        return buildItem(Material.WRITABLE_BOOK, "<gold>升级预览</gold>", lore);
    }

    private ItemStack renderSuccessRate(GemUpgradeGuiSession state) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add("<gray>成功率将在放入宝石后显示</gray>");
            return buildItem(Material.EXPERIENCE_BOTTLE, "<gold>成功率</gold>", lore);
        }
        double successRate = plugin.upgradeService().effectiveSuccessChance(preview.definition(), preview.targetLevel(), preview.upgradeLevel().successChance());
        lore.add("<gray>基础成功率: <green>" + successRate + "%</green></gray>");
        String failurePenalty = !preview.upgradeLevel().failurePenalty().isBlank()
                ? preview.upgradeLevel().failurePenalty()
                : !preview.definition().upgrade().failurePenalty().isBlank()
                        ? preview.definition().upgrade().failurePenalty()
                        : plugin.appConfig().upgrade().globalFailurePenalty();
        lore.add("<gray>失败惩罚: <yellow>" + failurePenalty + "</yellow></gray>");
        return buildItem(Material.EXPERIENCE_BOTTLE, "<gold>成功率</gold>", lore);
    }

    private ItemStack renderConfirm(GemUpgradeGuiSession state) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible()) {
            return buildItem(Material.BARRIER, "<red>无法升级</red>", List.of("<gray>请先满足升级条件</gray>"));
        }
        return buildItem(Material.LIME_STAINED_GLASS_PANE, "<green>确认升级</green>", List.of(
                "<gray>点击后仅消耗本界面材料槽中的材料并尝试升级</gray>",
                "<gray>目标等级: <gold>" + preview.targetLevel() + "</gold></gray>"
        ));
    }

    private GemUpgradeService.UpgradePreview preview(GemUpgradeGuiSession state) {
        return state == null || state.mutableTargetGem() == null
                ? GemUpgradeService.UpgradePreview.failure("command.upgrade.hold_gem")
                : plugin.upgradeService().preview(state.mutableTargetGem());
    }

    private String materialDisplayName(ItemSource source) {
        if (source == null) {
            return "未知材料";
        }
        if (plugin.coreItemSourceService() != null) {
            String displayName = plugin.coreItemSourceService().displayName(source);
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        return Texts.isBlank(shorthand) ? source.getIdentifier() : shorthand;
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        return buildItem(material.name(), name, lore);
    }

    private ItemStack buildItem(String item, String name, List<String> lore) {
        return GuiItemBuilder.build(
                item,
                new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of()),
                1,
                Map.of(),
                (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount)
        );
    }
}

package emaki.jiuwu.craft.gem.service;

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
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;

final class GemOpenGuiRenderer {

    private final EmakiGemPlugin plugin;

    GemOpenGuiRenderer(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack renderSlot(GemOpenGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        String type = Texts.lower(resolvedSlot.definition().type());
        return switch (type) {
            case "target_item" -> renderTargetItem(state);
            case "socket_info" -> renderSocketInfo(state);
            case "opener_item" -> renderOpenerItem(state);
            case "socket_slot" -> renderSocketSlot(state, resolvedSlot.slotIndex());
            case "preview_display" -> renderPreview(state);
            case "confirm" -> renderConfirm(state);
            default -> null;
        };
    }

    public void refreshGui(GemOpenGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetItem(GemOpenGuiSession state) {
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            return buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua>放入装备</aqua>", List.of(
                    "<gray>将需要开孔的装备放入此槽</gray>",
                    "<gray>支持从光标放入，也可点击取回</gray>"
            ));
        }
        return targetItem.clone();
    }

    private ItemStack renderSocketInfo(GemOpenGuiSession state) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add("<red>尚未放入有效装备</red>");
            lore.add("<gray>请先放入可开孔装备</gray>");
            return buildItem(Material.BOOK, "<gold>开孔信息</gold>", lore);
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        lore.add("<gray>装备定义: <gold>" + itemDefinition.id() + "</gold></gray>");
        lore.add("<gray>已开孔: <green>" + opened + "</green>/<yellow>" + total + "</yellow></gray>");
        lore.add("<gray>未开孔: <yellow>" + Math.max(0, total - opened) + "</yellow></gray>");
        lore.add("<gray>先放入开孔器，再点击下方锁定槽位</gray>");
        return buildItem(Material.BOOK, "<gold>开孔信息</gold>", lore);
    }

    private ItemStack renderOpenerItem(GemOpenGuiSession state) {
        ItemStack openerItem = state.openerItem();
        if (openerItem == null) {
            return buildItem(Material.AMETHYST_SHARD, "<light_purple>放入开孔器</light_purple>", List.of(
                    "<gray>将开孔器放入此槽</gray>",
                    "<gray>支持从光标放入，也可点击取回</gray>"
            ));
        }
        return openerItem.clone();
    }

    private ItemStack renderSocketSlot(GemOpenGuiSession state, int displayIndex) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            return buildItem(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>未使用插槽</dark_gray>", List.of("<dark_gray>当前装备没有这个槽位</dark_gray>"));
        }
        GemItemDefinition.SocketSlot slot = itemDefinition.slots().get(displayIndex);
        int slotIndex = slot.index();
        boolean selected = state.selectedSlotIndex() == slotIndex;
        boolean hasOpenerItem = plugin.itemMatcher().isOpenerItem(state.mutableOpenerItem());
        SocketOpenerConfig opener = plugin.itemMatcher().matchOpenerForType(state.mutableOpenerItem(), slot.type());
        if (gemState.isOpened(slotIndex)) {
            return buildItem(baseSocketMaterial(slot.type()), slotTitle(slot, slotIndex, "已开孔"), List.of(
                    "<gray>该槽位已经开启</gray>",
                    "<dark_gray>请在未开孔槽位上执行开孔</dark_gray>"
            ));
        }
        List<String> lore = new ArrayList<>();
        lore.add("<gray>槽位类型: <yellow>" + slot.displayName() + "</yellow></gray>");
        lore.add("<red>当前尚未开孔</red>");
        if (!hasOpenerItem) {
            lore.add("<gray>请先放入开孔器</gray>");
        } else if (opener == null) {
            lore.add("<red>当前开孔器无法开启此类型槽位</red>");
        } else {
            lore.add("<gray>点击可选择该槽位进行开孔</gray>");
        }
        if (selected) {
            lore.add("<green>已选择该槽位</green>");
        }
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, slotTitle(slot, slotIndex, "锁定"), lore);
    }

    private ItemStack renderPreview(GemOpenGuiSession state) {
        List<String> lore = new ArrayList<>();
        if (state.mutableTargetItem() == null) {
            lore.add("<gray>这里会显示待开孔装备与目标槽位预览</gray>");
            return buildItem(Material.WRITABLE_BOOK, "<gold>开孔预览</gold>", lore);
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GemItemDefinition.SocketSlot slot = itemDefinition == null ? null : itemDefinition.slot(state.selectedSlotIndex());
        lore.add("<gray>装备: <yellow>" + (itemDefinition == null ? "未识别" : itemDefinition.id()) + "</yellow></gray>");
        SocketOpenerConfig opener = slot == null
                ? plugin.itemMatcher().matchOpenerItem(state.mutableOpenerItem())
                : plugin.itemMatcher().matchOpenerForType(state.mutableOpenerItem(), slot.type());
        lore.add("<gray>开孔器: <yellow>" + openerText(state, opener) + "</yellow></gray>");
        lore.add("<gray>目标槽位: <yellow>" + (slot == null ? "未选择" : "#" + slot.index() + " " + slot.displayName()) + "</yellow></gray>");
        lore.add("<gray>确认后会为该锁定槽位执行一次开孔</gray>");
        return buildItem(Material.WRITABLE_BOOK, "<gold>开孔预览</gold>", lore);
    }

    private ItemStack renderConfirm(GemOpenGuiSession state) {
        if (state.mutableTargetItem() == null || state.mutableOpenerItem() == null || state.selectedSlotIndex() < 0) {
            return buildItem(Material.GRAY_STAINED_GLASS_PANE, "<gray>确认开孔</gray>", List.of(
                    "<dark_gray>请先放入装备、开孔器，并选择一个锁定槽位</dark_gray>"
            ));
        }
        return buildItem(Material.LIME_STAINED_GLASS_PANE, "<green>确认开孔</green>", List.of(
                "<gray>点击执行当前预览中的开孔操作</gray>"
        ));
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        return GuiItemBuilder.build(
                material.name(),
                new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of()),
                1,
                Map.of(),
                (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount)
        );
    }

    private Material baseSocketMaterial(String type) {
        return switch (Texts.lower(type)) {
            case "attack" -> Material.RED_STAINED_GLASS_PANE;
            case "defense" -> Material.BLUE_STAINED_GLASS_PANE;
            case "utility" -> Material.GREEN_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    private ItemStack hiddenSlot() {
        return new ItemStack(Material.AIR);
    }

    private String slotTitle(GemItemDefinition.SocketSlot slot, int slotIndex, String stateText) {
        return "<white>" + slot.displayName() + " <gray>(#" + slotIndex + " " + stateText + ")</gray></white>";
    }

    private String openerText(GemOpenGuiSession state, SocketOpenerConfig opener) {
        if (state.mutableOpenerItem() == null) {
            return "未放入";
        }
        return opener == null ? "已放入，待选择槽位" : opener.id();
    }
}

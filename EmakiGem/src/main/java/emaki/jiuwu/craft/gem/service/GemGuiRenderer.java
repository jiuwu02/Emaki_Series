package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemGuiRenderer {

    private final EmakiGemPlugin plugin;

    GemGuiRenderer(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack renderSlot(GemGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        return switch (type) {
            case "target_item" -> renderTargetItem(state);
            case "socket_info" -> renderSocketInfo(state);
            case "socket_summary" -> renderSocketSummary(state);
            case "socket_slot" -> renderSocketSlot(state, resolvedSlot.slotIndex());
            case "preview_display" -> renderPreviewDisplay(state);
            case "mode_inlay" -> buildModeButton(state.mode() == GemGuiMode.INLAY, "镶嵌模式", "拖着宝石点击已开孔的空槽进入镶嵌预览", Material.GREEN_STAINED_GLASS_PANE);
            case "mode_extract" -> buildModeButton(state.mode() == GemGuiMode.EXTRACT, "取出模式", "点击已镶嵌的宝石槽进入取出确认", Material.YELLOW_STAINED_GLASS_PANE);
            case "confirm" -> renderConfirm(state);
            default -> null;
        };
    }

    public void refreshGui(GemGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetItem(GemGuiSession state) {
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            return buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua>放入装备</aqua>", List.of(
                    "<gray>将可镶嵌宝石的装备放入此槽</gray>",
                    "<gray>支持从光标放入，也可点击取回</gray>"
            ));
        }
        return targetItem.clone();
    }

    private ItemStack renderSocketInfo(GemGuiSession state) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>当前模式: <yellow>" + modeText(state.mode()) + "</yellow></gray>");
        if (itemDefinition == null || gemState == null) {
            lore.add("<red>尚未放入有效装备</red>");
            lore.add("<gray>请先将装备放入左侧槽位</gray>");
            return buildItem(Material.BOOK, "<gold>操作说明</gold>", lore);
        }
        lore.add("<gray>装备定义: <gold>" + itemDefinition.id() + "</gold></gray>");
        lore.add(switch (state.mode()) {
            case INLAY -> "<gray>拖着宝石点击已开孔空槽，可进入镶嵌预览</gray>";
            case EXTRACT -> "<gray>点击已镶嵌宝石槽，可进入取出确认</gray>";
            case OPEN_SOCKET, UPGRADE -> "<gray>当前界面使用装备宝石操作模式</gray>";
        });
        lore.add("<gray>未开孔槽位请使用独立开孔 GUI 处理</gray>");
        return buildItem(Material.BOOK, "<gold>操作说明</gold>", lore);
    }

    private ItemStack renderSocketSummary(GemGuiSession state) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add("<gray>这里会展示当前装备的宝石槽统计</gray>");
            lore.add("<gray>放入装备后可查看总槽位与已镶嵌数量</gray>");
            return buildItem(Material.COMPASS, "<gold>宝石槽统计</gold>", lore);
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        int embedded = gemState.socketAssignments().size();
        lore.add("<gray>总宝石孔数: <yellow>" + total + "</yellow></gray>");
        lore.add("<gray>已开孔数: <green>" + opened + "</green></gray>");
        lore.add("<gray>已镶嵌数: <aqua>" + embedded + "</aqua></gray>");
        lore.add("<gray>空余已开孔: <gold>" + Math.max(0, opened - embedded) + "</gold></gray>");
        lore.add("<gray>未开孔数: <red>" + Math.max(0, total - opened) + "</red></gray>");
        return buildItem(Material.COMPASS, "<gold>宝石槽统计</gold>", lore);
    }

    private ItemStack renderSocketSlot(GemGuiSession state, int displayIndex) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            return buildItem(Material.WHITE_STAINED_GLASS_PANE, "<white>宝石插槽</white>", List.of(
                    "<gray>放入装备后将按实际宝石孔数量展示</gray>"
            ));
        }
        GemItemDefinition.SocketSlot socketSlot = itemDefinition.slots().get(displayIndex);
        int socketIndex = socketSlot.index();
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        boolean selected = pendingOperation.active() && pendingOperation.slotIndex() == socketIndex;
        if (!gemState.isOpened(socketIndex)) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>槽位类型: <yellow>" + socketSlot.displayName() + "</yellow></gray>");
            lore.add("<red>当前尚未开孔</red>");
            lore.add("<gray>请使用独立开孔 GUI 进行开孔</gray>");
            if (selected) {
                lore.add("<green>已加入待确认操作</green>");
            }
            return buildItem(Material.GRAY_STAINED_GLASS_PANE, slotTitle(socketSlot, socketIndex, "锁定"), lore);
        }
        GemItemInstance assigned = gemState.assignment(socketIndex);
        if (assigned == null) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>槽位类型: <yellow>" + socketSlot.displayName() + "</yellow></gray>");
            lore.add("<green>当前为空槽</green>");
            lore.add(state.mode() == GemGuiMode.INLAY
                    ? "<gray>拖着宝石点击此槽进入镶嵌预览</gray>"
                    : "<dark_gray>空槽无法取出</dark_gray>");
            if (selected) {
                lore.add("<green>已加入待确认操作</green>");
            }
            return buildItem(baseSocketMaterial(socketSlot.type()), slotTitle(socketSlot, socketIndex, "空槽"), lore);
        }
        GemDefinition definition = plugin.gemLoader().get(assigned.gemId());
        ItemStack gemItem = plugin.itemFactory().recreateGemItem(assigned, 1);
        List<String> extraLore = new ArrayList<>();
        extraLore.add("<gray>插槽位置: <gold>#" + socketIndex + "</gold></gray>");
        extraLore.add("<gray>槽位类型: <yellow>" + socketSlot.displayName() + "</yellow></gray>");
        extraLore.add("<gray>宝石等级: <yellow>Lv." + assigned.level() + "</yellow></gray>");
        if (definition != null) {
            extraLore.add("<gray>宝石类型: <yellow>" + definition.gemType() + "</yellow></gray>");
            extraLore.add("<gray>宝石品阶: <gold>T" + definition.tier() + "</gold></gray>");
        }
        extraLore.add(state.mode() == GemGuiMode.EXTRACT
                ? "<gray>点击进入取出确认</gray>"
                : "<dark_gray>该槽已有宝石</dark_gray>");
        if (selected) {
            extraLore.add("<green>已加入待确认操作</green>");
        }
        if (gemItem != null) {
            return appendLore(gemItem, extraLore);
        }
        return buildItem(Material.RED_DYE, slotTitle(socketSlot, socketIndex, "已镶嵌"), extraLore);
    }

    private ItemStack renderPreviewDisplay(GemGuiSession state) {
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        List<String> lore = new ArrayList<>();
        if (!pendingOperation.active()) {
            lore.add("<gray>这里会显示待确认操作预览</gray>");
            lore.add("<gray>按当前模式点击目标插槽后可查看详情</gray>");
            return buildItem(Material.WRITABLE_BOOK, "<gold>操作预览</gold>", lore);
        }
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        GemItemDefinition.SocketSlot slot = itemDefinition == null ? null : itemDefinition.slot(pendingOperation.slotIndex());
        lore.add("<gray>待执行: <yellow>" + pendingText(pendingOperation.type()) + "</yellow></gray>");
        lore.add("<gray>目标插槽: <gold>#" + pendingOperation.slotIndex() + "</gold></gray>");
        if (slot != null) {
            lore.add("<gray>槽位类型: <yellow>" + slot.displayName() + "</yellow></gray>");
        }
        switch (pendingOperation.type()) {
            case INLAY -> {
                GemItemInstance instance = plugin.itemMatcher().readGemInstance(pendingOperation.inputItem());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                lore.add("<gray>宝石: <yellow>"
                        + (definition == null ? "未识别" : plugin.itemFactory().resolveGemDisplayName(definition, instance.level()))
                        + "</yellow></gray>");
                if (instance != null) {
                    lore.add("<gray>等级: <gold>Lv." + instance.level() + "</gold></gray>");
                }
            }
            case EXTRACT -> {
                GemItemInstance instance = gemState == null ? null : gemState.assignment(pendingOperation.slotIndex());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                lore.add("<gray>取出宝石: <yellow>"
                        + (definition == null ? "未知" : plugin.itemFactory().resolveGemDisplayName(definition, instance.level()))
                        + "</yellow></gray>");
            }
            default -> {
            }
        }
        lore.add("<green>点击确认按钮执行</green>");
        return buildItem(Material.WRITABLE_BOOK, "<gold>操作预览</gold>", lore);
    }

    private ItemStack renderConfirm(GemGuiSession state) {
        if (!state.pendingOperation().active()) {
            return buildItem(Material.GRAY_STAINED_GLASS_PANE, "<gray>确认操作</gray>", List.of(
                    "<dark_gray>请先选择一个待执行操作</dark_gray>"
            ));
        }
        return buildItem(Material.LIME_STAINED_GLASS_PANE, "<green>确认操作</green>", List.of(
                "<gray>点击执行当前预览中的操作</gray>",
                "<gray>待执行: <yellow>" + pendingText(state.pendingOperation().type()) + "</yellow></gray>"
        ));
    }

    private ItemStack buildModeButton(boolean active, String title, String description, Material material) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + description + "</gray>");
        lore.add(active ? "<green>当前已启用</green>" : "<dark_gray>点击切换到该模式</dark_gray>");
        return buildItem(material, (active ? "<green>" : "<yellow>") + title + (active ? "</green>" : "</yellow>"), lore);
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

    private ItemStack appendLore(ItemStack baseItem, List<String> extraLore) {
        if (baseItem == null || baseItem.getType().isAir() || extraLore == null || extraLore.isEmpty()) {
            return baseItem;
        }
        ItemStack cloned = baseItem.clone();
        ItemMeta itemMeta = cloned.getItemMeta();
        if (itemMeta == null) {
            return cloned;
        }
        List<String> lore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null && !existingLore.isEmpty()) {
            lore.addAll(existingLore);
            lore.add("");
        }
        lore.addAll(extraLore);
        ItemTextBridge.setLoreLines(itemMeta, lore);
        cloned.setItemMeta(itemMeta);
        return cloned;
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

    private String modeText(GemGuiMode mode) {
        return switch (mode) {
            case INLAY -> "镶嵌";
            case EXTRACT -> "取出";
            case OPEN_SOCKET -> "开孔";
            case UPGRADE -> "升级";
        };
    }

    private String pendingText(GemGuiSession.PendingType pendingType) {
        return switch (pendingType) {
            case INLAY -> "镶嵌宝石";
            case EXTRACT -> "取出宝石";
            default -> "无";
        };
    }
}

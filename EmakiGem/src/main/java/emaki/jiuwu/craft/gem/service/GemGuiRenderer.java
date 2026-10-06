package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.model.GemRerollSessionView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

final class GemGuiRenderer {

    private static final String TEXT_PREFIX = "gui_text.gem.";
    private static final String COMMON_PREFIX = "gui_text.common.";

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
            case "target_item" -> renderTargetItem(state, slot);
            case "socket_info" -> renderSocketInfo(state, slot);
            case "socket_summary" -> renderSocketSummary(state, slot);
            case "socket_slot" -> renderSocketSlot(state, resolvedSlot.slotIndex(), slot);
            case "preview_display" -> renderPreviewDisplay(state, slot);
            case "mode_inlay" -> buildModeButton(slot, state.mode() == GemGuiMode.INLAY,
                    text("mode_inlay_title", "镶嵌模式"),
                    text("mode_inlay_desc", "拖着宝石点击已开孔的空槽进入镶嵌预览"));
            case "mode_upgrade" -> buildModeButton(slot, state.mode() == GemGuiMode.UPGRADE,
                    text("mode_upgrade_title", "升级模式"),
                    text("mode_upgrade_desc", "放入宝石与配方材料以升级"));
            case "mode_extract" -> buildModeButton(slot, state.mode() == GemGuiMode.EXTRACT,
                    text("mode_extract_title", "取出模式"),
                    text("mode_extract_desc", "点击已镶嵌的宝石槽"));
            case "mode_reroll_full" -> buildModeButton(slot, state.mode() == GemGuiMode.REROLL_FULL,
                    text("mode_reroll_full_title", "洗炼模式"),
                    text("mode_reroll_full_desc", "将宝石拿在主手并重掷全部词条"));
            case "mode_reroll_value" -> buildModeButton(slot, state.mode() == GemGuiMode.REROLL_VALUE,
                    text("mode_reroll_value_title", "重算模式"),
                    text("mode_reroll_value_desc", "将宝石拿在主手并重算词条数值"));
            case "confirm" -> renderConfirm(state, slot);
            default -> GuiItemBuilder.build(slot.itemDefinition(), Map.of(),
                    plugin.coreLib().configuredItemService());
        };
    }

    public void refreshGui(GemGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetItem(GemGuiSession state, GuiSlot slot) {
        if (state.rerollMode()) {
            String title = text("reroll_target_name", "<light_purple>主手宝石</light_purple>");
            List<String> lore = List.of(
                    text("reroll_target_lore_1", "<gray>洗炼始终以主手宝石为目标</gray>"),
                    text("reroll_target_lore_2", "<dark_gray>此槽位不接受任何物品</dark_gray>")
            );
            return buildConfiguredItem(slot, Material.MAGENTA_STAINED_GLASS_PANE, title, lore,
                    targetReplacements(title, lore, state));
        }
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            String nameKey = state.mode() == GemGuiMode.UPGRADE ? "upgrade_target_empty_name" : "target_empty_name";
            String loreKey = state.mode() == GemGuiMode.UPGRADE ? "upgrade_target_empty_lore_1" : "target_empty_lore_1";
            String title = text(nameKey, state.mode() == GemGuiMode.UPGRADE
                    ? "<light_purple>放入宝石</light_purple>"
                    : "<aqua>放入装备</aqua>");
            List<String> lore = List.of(
                    text(loreKey, state.mode() == GemGuiMode.UPGRADE
                            ? "<gray>将可升级的宝石放入此槽</gray>"
                            : "<gray>将可镶嵌宝石的装备放入此槽</gray>"),
                    common("click_take_back", "<gray>支持从光标放入，也可点击取回</gray>")
            );
            return buildConfiguredItem(slot, Material.LIGHT_BLUE_STAINED_GLASS_PANE, title, lore,
                    targetReplacements(title, lore, state));
        }
        return targetItem.clone();
    }

    private Map<String, Object> targetReplacements(String title, List<String> lines, GemGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("target_title", title);
        replacements.put("target_lines", lines);
        replacements.put("mode", modeText(state.mode()));
        return replacements;
    }

    private ItemStack renderUpgradeInfo(GemGuiSession state, GuiSlot slot) {
        GemUpgradeView view = resolveUpgradeView(state);
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())),
                "<gray>当前模式: <yellow>%mode%</yellow></gray>"));
        if (view == null) {
            lore.add(text("upgrade_no_target_line_1", "<red>尚未放入可升级宝石</red>"));
            lore.add(text("upgrade_no_target_line_2", "<gray>请将已配置 stages 的宝石放入目标槽</gray>"));
        } else {
            lore.add(text("upgrade_current_level", Map.of("level", view.instance().level()),
                    "<gray>当前等级: <yellow>Lv.%level%</yellow></gray>"));
            lore.add(text("upgrade_max_level", Map.of("max_level", view.definition().stages().maxLevel()),
                    "<gray>最高等级: <gold>Lv.%max_level%</gold></gray>"));
            lore.add(view.nextStage() == null
                    ? text("upgrade_already_max", "<green>该宝石已达到配置的最高阶段</green>")
                    : text("upgrade_next_stage", Map.of(
                            "level", view.nextLevel(),
                            "stage", nextStageName(view)),
                            "<gray>下一阶段: <light_purple>Lv.%level% %stage%</light_purple></gray>"));
        }
        lore.add(plugin.strengthenIntegration() != null && plugin.strengthenIntegration().available()
                ? text("upgrade_framework_ready", "<green>Strengthen 框架已就绪</green>")
                : text("upgrade_framework_unavailable", "<red>Strengthen 框架不可用</red>"));
        String title = text("upgrade_info_name", "<light_purple>宝石升级</light_purple>");
        Map<String, Object> replacements = infoReplacements(title, lore, state);
        if (view != null) {
            replacements.put("item", view.definition().id());
            replacements.put("level", view.instance().level());
            replacements.put("max_level", view.definition().stages().maxLevel());
        }
        return buildConfiguredItem(slot, Material.ENCHANTED_BOOK, title, lore, replacements);
    }

    private Map<String, Object> infoReplacements(String title, List<String> lines, GemGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("info_title", title);
        replacements.put("info_lines", lines);
        replacements.put("mode", modeText(state.mode()));
        replacements.put("item", common("none", "无"));
        replacements.put("level", 0);
        replacements.put("max_level", 0);
        replacements.put("seconds", 0L);
        return replacements;
    }

    private ItemStack renderUpgradeSummary(GemGuiSession state, GuiSlot slot) {
        GemUpgradeView view = resolveUpgradeView(state);
        int occupied = 0;
        int totalAmount = 0;
        for (ItemStack material : state.upgradeMaterials()) {
            if (material != null && !material.getType().isAir()) {
                occupied++;
                totalAmount += material.getAmount();
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add(text("upgrade_material_slots", Map.of("count", occupied),
                "<gray>已填材料槽: <yellow>%count%</yellow></gray>"));
        lore.add(text("upgrade_material_amount", Map.of("amount", totalAmount),
                "<gray>材料总数量: <gold>%amount%</gold></gray>"));
        if (view != null) {
            lore.add(text("upgrade_recipe_id", Map.of("recipe", view.definition().id()),
                    "<gray>强化配方: <aqua>%recipe%</aqua></gray>"));
        }
        lore.add(text("upgrade_material_help", "<gray>将配方材料放入下方七个槽位</gray>"));
        String title = text("upgrade_summary_name", "<light_purple>升级材料</light_purple>");
        Map<String, Object> replacements = summaryReplacements(title, lore);
        replacements.put("count", occupied);
        replacements.put("amount", totalAmount);
        if (view != null) {
            replacements.put("recipe", view.definition().id());
        }
        return buildConfiguredItem(slot, Material.AMETHYST_SHARD, title, lore, replacements);
    }

    private ItemStack renderUpgradeMaterialSlot(GemGuiSession state, int displayIndex, GuiSlot guiSlot) {
        ItemStack material = state.upgradeMaterial(displayIndex);
        if (material != null) {
            return material;
        }
        String title = text("upgrade_material_slot_name", Map.of("slot", displayIndex + 1),
                "<light_purple>升级材料 #%slot%</light_purple>");
        List<String> lore = List.of(
                text("upgrade_material_slot_lore", "<gray>将材料堆放入此槽位</gray>"),
                common("click_take_back", "<gray>支持从光标放入，也可点击取回</gray>")
        );
        Map<String, Object> replacements = slotReplacements(title, lore);
        replacements.put("slot", displayIndex + 1);
        return buildConfiguredItem(guiSlot, Material.PURPLE_STAINED_GLASS_PANE, title, lore, replacements);
    }

    private Map<String, Object> slotReplacements(String title, List<String> lines) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("slot_title", title);
        replacements.put("slot_lines", lines);
        replacements.put("slot", 0);
        replacements.put("type", common("none", "无"));
        replacements.put("state", common("none", "无"));
        return replacements;
    }

    private ItemStack renderUpgradePreview(GemGuiSession state, GuiSlot slot) {
        GemUpgradeView view = resolveUpgradeView(state);
        String title = text("upgrade_preview_name", "<light_purple>升级预览</light_purple>");
        if (view == null || view.nextStage() == null) {
            List<String> lore = new ArrayList<>();
            lore.add(view == null
                    ? text("upgrade_preview_empty", "<gray>放入可升级宝石后可预览下一阶段</gray>")
                    : text("upgrade_already_max", "<green>该宝石已达到配置的最高阶段</green>"));
            Map<String, Object> replacements = previewReplacements(title, lore, state);
            if (view != null) {
                replacements.put("level", view.instance().level());
                replacements.put("stage", nextStageName(view));
            }
            return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore, replacements);
        }
        ItemStack preview = plugin.itemFactory().createGemItem(view.definition(), view.nextLevel(), 1);
        List<String> lore = List.of(
                text("upgrade_preview_transition", Map.of(
                        "previous_level", view.instance().level(),
                        "resulting_level", view.nextLevel()),
                        "<gray>等级: <yellow>%previous_level%</yellow> → <green>%resulting_level%</green></gray>"),
                text("upgrade_preview_stage", Map.of("stage", nextStageName(view)),
                        "<gray>阶段: <light_purple>%stage%</light_purple></gray>"),
                text("preview_confirm_hint", "<green>点击确认以执行</green>")
        );
        if (preview != null) {
            return appendLore(preview, lore);
        }
        Map<String, Object> replacements = previewReplacements(title, lore, state);
        replacements.put("level", view.instance().level());
        replacements.put("stage", nextStageName(view));
        return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore, replacements);
    }

    private Map<String, Object> previewReplacements(String title, List<String> lines, GemGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("preview_title", title);
        replacements.put("preview_lines", lines);
        replacements.put("action", pendingText(state.pendingOperation().type()));
        replacements.put("slot", state.pendingOperation().slotIndex());
        replacements.put("gem", common("none", "无"));
        replacements.put("level", 0);
        replacements.put("stage", common("none", "无"));
        return replacements;
    }

    private GemGuiSession.TargetResolution targetResolution(GemGuiSession state) {
        GemGuiSession.TargetResolution cached = state.targetResolution();
        if (cached != null) {
            return cached;
        }
        ItemStack targetItem = state.mutableTargetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        GemGuiSession.TargetResolution resolution = new GemGuiSession.TargetResolution(itemDefinition, gemState);
        state.cacheTargetResolution(resolution);
        return resolution;
    }

    private GemUpgradeView resolveUpgradeView(GemGuiSession state) {
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(state == null ? null : state.targetItem());
        GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (definition == null || !definition.stages().enabled()) {
            return null;
        }
        int nextLevel = instance.level() + 1;
        return new GemUpgradeView(instance, definition, nextLevel, definition.stage(nextLevel));
    }

    private String nextStageName(GemUpgradeView view) {
        return view == null || view.nextStage() == null || Texts.isBlank(view.nextStage().displayName())
                ? "Lv." + (view == null ? "?" : view.nextLevel())
                : view.nextStage().displayName();
    }

    private ItemStack renderSocketInfo(GemGuiSession state, GuiSlot slot) {
        if (state.mode() == GemGuiMode.UPGRADE) {
            return renderUpgradeInfo(state, slot);
        }
        if (state.rerollMode()) {
            return renderRerollInfo(state, slot);
        }
        GemGuiSession.TargetResolution resolution = targetResolution(state);
        GemItemDefinition itemDefinition = resolution.definition();
        GemState gemState = resolution.state();
        String title = text("info_name", "<gold>操作说明</gold>");
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())), "<gray>当前模式: <yellow>%mode%</yellow></gray>"));
        if (itemDefinition == null || gemState == null) {
            lore.add(text("no_target_line_1", "<red>尚未放入有效装备</red>"));
            lore.add(text("no_target_line_2", "<gray>请先放入装备</gray>"));
            return buildConfiguredItem(slot, Material.BOOK, title, lore,
                    infoReplacements(title, lore, state));
        }
        lore.add(text("equipment_definition", Map.of("item", itemDefinition.id()), "<gray>装备定义: <gold>%item%</gold></gray>"));
        lore.add(switch (state.mode()) {
            case INLAY -> text("inlay_help", "<gray>拖着宝石点击已开孔的空槽</gray>");
            case UPGRADE -> text("upgrade_help", "<gray>放入宝石与配方材料后点击确认</gray>");
            case EXTRACT -> text("extract_help", "<gray>点击已镶嵌的宝石槽</gray>");
            case REROLL_FULL -> text("reroll_full_help", "<gray>查看洗炼候选后点击确认</gray>");
            case REROLL_VALUE -> text("reroll_value_help", "<gray>查看重算后的数值后点击确认</gray>");
            case OPEN_SOCKET -> text("default_help", "<gray>当前界面使用装备宝石操作模式</gray>");
        });
        lore.add(text("unopened_help", "<gray>未开孔槽位请使用独立开孔 GUI</gray>"));
        Map<String, Object> replacements = infoReplacements(title, lore, state);
        replacements.put("item", itemDefinition.id());
        return buildConfiguredItem(slot, Material.BOOK, title, lore, replacements);
    }

    private ItemStack renderSocketSummary(GemGuiSession state, GuiSlot slot) {
        if (state.mode() == GemGuiMode.UPGRADE) {
            return renderUpgradeSummary(state, slot);
        }
        if (state.rerollMode()) {
            return renderRerollSummary(state, slot);
        }
        GemGuiSession.TargetResolution resolution = targetResolution(state);
        GemItemDefinition itemDefinition = resolution.definition();
        GemState gemState = resolution.state();
        String title = text("summary_name", "<gold>宝石槽统计</gold>");
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add(text("summary_empty_1", "<gray>这里会展示宝石槽统计</gray>"));
            lore.add(text("summary_empty_2", "<gray>放入装备后可查看宝石槽数量</gray>"));
            return buildConfiguredItem(slot, Material.COMPASS, title, lore,
                    summaryReplacements(title, lore));
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        int embedded = gemState.socketAssignments().size();
        int free = Math.max(0, opened - embedded);
        int locked = Math.max(0, total - opened);
        lore.add(text("total_slots", Map.of("total", total), "<gray>总宝石孔数: <yellow>%total%</yellow></gray>"));
        lore.add(text("opened_slots", Map.of("opened", opened), "<gray>已开孔数: <green>%opened%</green></gray>"));
        lore.add(text("embedded_slots", Map.of("embedded", embedded), "<gray>已镶嵌数: <aqua>%embedded%</aqua></gray>"));
        lore.add(text("free_opened_slots", Map.of("free", free), "<gray>空余已开孔: <gold>%free%</gold></gray>"));
        lore.add(text("locked_slots", Map.of("locked", locked), "<gray>未开孔数: <red>%locked%</red></gray>"));
        Map<String, Object> replacements = summaryReplacements(title, lore);
        replacements.put("total", total);
        replacements.put("opened", opened);
        replacements.put("embedded", embedded);
        replacements.put("free", free);
        replacements.put("locked", locked);
        return buildConfiguredItem(slot, Material.COMPASS, title, lore, replacements);
    }

    private Map<String, Object> summaryReplacements(String title, List<String> lines) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("summary_title", title);
        replacements.put("summary_lines", lines);
        replacements.put("total", 0);
        replacements.put("opened", 0);
        replacements.put("embedded", 0);
        replacements.put("free", 0);
        replacements.put("locked", 0);
        replacements.put("count", 0);
        replacements.put("amount", 0);
        replacements.put("recipe", common("none", "无"));
        return replacements;
    }

    private ItemStack renderRerollInfo(GemGuiSession state, GuiSlot slot) {
        String title = text("info_name", "<gold>操作说明</gold>");
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())),
                "<gray>当前模式: <yellow>%mode%</yellow></gray>"));
        GemRerollSessionView view = rerollView(state);
        if (view == null) {
            lore.add(state.mode() == GemGuiMode.REROLL_VALUE
                    ? text("reroll_value_help", "<gray>查看重算后的数值后点击确认</gray>")
                    : text("reroll_full_help", "<gray>查看洗炼候选后点击确认</gray>"));
            lore.add(text("reroll_hold_hint", "<gray>请将宝石拿在主手</gray>"));
            lore.add(text("reroll_generate_hint", "<green>点击确认生成候选</green>"));
            return buildConfiguredItem(slot, Material.BOOK, title, lore,
                    infoReplacements(title, lore, state));
        }
        long remainingSeconds = rerollRemainingSeconds(view);
        lore.add(text("reroll_candidate_open", "<green>已有候选等待确认</green>"));
        lore.add(text("reroll_expires_in", Map.of("seconds", remainingSeconds),
                "<gray>剩余时间: <gold>%seconds%s</gold></gray>"));
        lore.add(text("reroll_confirm_hint", "<gray>点击确认写入宝石</gray>"));
        lore.add(text("reroll_cancel_hint", "<dark_gray>切换模式或关闭界面将退还费用</dark_gray>"));
        Map<String, Object> replacements = infoReplacements(title, lore, state);
        replacements.put("seconds", remainingSeconds);
        return buildConfiguredItem(slot, Material.BOOK, title, lore, replacements);
    }

    private ItemStack renderRerollSummary(GemGuiSession state, GuiSlot slot) {
        GemRerollSessionView view = rerollView(state);
        String title = text("reroll_summary_name", "<gold>洗炼对比</gold>");
        List<String> lore = new ArrayList<>();
        if (view == null) {
            lore.add(text("reroll_summary_empty", "<gray>生成候选后将显示对比</gray>"));
            return buildConfiguredItem(slot, Material.COMPASS, title, lore,
                    summaryReplacements(title, lore));
        }
        lore.add(text("reroll_original_header", "<gray>当前词条:</gray>"));
        lore.addAll(rerollAffixLines(view.originalAffixes(), "<dark_gray>- %affix%</dark_gray>",
                "reroll_original_line", "reroll_none_line"));
        lore.add("");
        lore.add(text("reroll_candidate_header", "<gray>候选词条:</gray>"));
        lore.addAll(rerollAffixLines(view.candidateAffixes(), "<green>+ %affix%</green>",
                "reroll_candidate_line", "reroll_none_line"));
        return buildConfiguredItem(slot, Material.COMPASS, title, lore,
                summaryReplacements(title, lore));
    }

    private List<String> rerollAffixLines(List<String> affixes,
            String fallback,
            String lineKey,
            String emptyKey) {
        List<String> lines = new ArrayList<>();
        if (affixes == null || affixes.isEmpty()) {
            lines.add(text(emptyKey, "<dark_gray>- 无</dark_gray>"));
            return lines;
        }
        for (String affix : affixes) {
            lines.add(text(lineKey, Map.of("affix", Texts.toStringSafe(affix)), fallback));
        }
        return lines;
    }

    private GemRerollSessionView rerollView(GemGuiSession state) {
        if (state == null || state.player() == null || plugin.rerollSessionService() == null) {
            return null;
        }
        return plugin.rerollSessionService().view(state.player().getUniqueId()).orElse(null);
    }

    private long rerollRemainingSeconds(GemRerollSessionView view) {
        return view == null ? 0L : Math.max(0L, (view.expiryAt() - System.currentTimeMillis()) / 1000L);
    }

    private ItemStack renderSocketSlot(GemGuiSession state, int displayIndex, GuiSlot guiSlot) {
        if (state.mode() == GemGuiMode.UPGRADE) {
            return renderUpgradeMaterialSlot(state, displayIndex, guiSlot);
        }
        if (state.rerollMode()) {
            return hiddenSlot();
        }
        GemGuiSession.TargetResolution resolution = targetResolution(state);
        GemItemDefinition itemDefinition = resolution.definition();
        GemState gemState = resolution.state();
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            String emptyTitle = text("socket_name", "<white>宝石插槽</white>");
            List<String> emptyLore = List.of(
                    text("socket_empty_lore", "<gray>放入装备后将按实际宝石孔数量展示</gray>")
            );
            return buildConfiguredItem(guiSlot, Material.WHITE_STAINED_GLASS_PANE, emptyTitle, emptyLore,
                    slotReplacements(emptyTitle, emptyLore));
        }
        GemItemDefinition.SocketSlot socketSlot = itemDefinition.slots().get(displayIndex);
        int socketIndex = socketSlot.index();
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        boolean selected = pendingOperation.active() && pendingOperation.slotIndex() == socketIndex;
        if (!gemState.isOpened(socketIndex)) {
            List<String> lore = new ArrayList<>();
            lore.add(socketType(socketSlot.displayName()));
            lore.add(text("not_opened", "<red>当前尚未开孔</red>"));
            lore.add(text("open_in_open_gui", "<gray>请使用独立开孔 GUI 进行开孔</gray>"));
            if (selected) {
                lore.add(common("selected", "<green>已加入待确认操作</green>"));
            }
            String lockedState = text("socket_locked", "锁定");
            String lockedTitle = slotTitle(socketSlot, socketIndex, lockedState);
            Map<String, Object> replacements = slotReplacements(lockedTitle, lore);
            replacements.put("slot", socketIndex);
            replacements.put("type", socketSlot.displayName());
            replacements.put("state", lockedState);
            return buildConfiguredItem(guiSlot, Material.GRAY_STAINED_GLASS_PANE, lockedTitle, lore, replacements);
        }
        GemItemInstance assigned = gemState.assignment(socketIndex);
        if (assigned == null) {
            if (selected && pendingOperation.type() == GemGuiSession.PendingType.INLAY && pendingOperation.inputItem() != null) {
                GemItemInstance pendingInstance = plugin.itemMatcher().readGemInstance(pendingOperation.inputItem());
                GemDefinition pendingDefinition = pendingInstance == null ? null : plugin.gemLoader().get(pendingInstance.gemId());
                ItemStack pendingGemDisplay = plugin.itemFactory().recreateGemItem(pendingInstance, 1);
                List<String> extraLore = new ArrayList<>();
                extraLore.add(text("slot_position", Map.of("slot", socketIndex), "<gray>插槽位置: <gold>#%slot%</gold></gray>"));
                extraLore.add(socketType(socketSlot.displayName()));
                if (pendingDefinition != null) {
                    extraLore.add(text("gem_type", Map.of("type", pendingDefinition.gemType()), "<gray>宝石类型: <yellow>%type%</yellow></gray>"));
                }
                if (pendingInstance != null) {
                    extraLore.add(text("gem_level", Map.of("level", pendingInstance.level()), "<gray>宝石等级: <yellow>Lv.%level%</yellow></gray>"));
                }
                extraLore.add(text("pending_inlay", "<green>待确认镶嵌</green>"));
                if (pendingGemDisplay != null) {
                    return appendLore(pendingGemDisplay, extraLore);
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add(socketType(socketSlot.displayName()));
            lore.add(text("socket_current_empty", "<green>当前为空槽</green>"));
            lore.add(state.mode() == GemGuiMode.INLAY
                    ? text("socket_inlay_hint", "<gray>拖着宝石点击此槽</gray>")
                    : text("socket_extract_empty", "<dark_gray>空槽无法取出</dark_gray>"));
            if (selected) {
                lore.add(common("selected", "<green>已加入待确认操作</green>"));
            }
            String emptyState = text("socket_empty", "空槽");
            String emptyTitle = slotTitle(socketSlot, socketIndex, emptyState);
            Map<String, Object> replacements = slotReplacements(emptyTitle, lore);
            replacements.put("slot", socketIndex);
            replacements.put("type", socketSlot.displayName());
            replacements.put("state", emptyState);
            return buildConfiguredItem(guiSlot, baseSocketMaterial(socketSlot.type()), emptyTitle, lore, replacements);
        }
        GemDefinition definition = plugin.gemLoader().get(assigned.gemId());
        ItemStack gemItem = plugin.itemFactory().recreateGemItem(assigned, 1);
        List<String> extraLore = new ArrayList<>();
        extraLore.add(text("slot_position", Map.of("slot", socketIndex), "<gray>插槽位置: <gold>#%slot%</gold></gray>"));
        extraLore.add(socketType(socketSlot.displayName()));
        extraLore.add(text("gem_level", Map.of("level", assigned.level()), "<gray>宝石等级: <yellow>Lv.%level%</yellow></gray>"));
        if (definition != null) {
            extraLore.add(text("gem_type", Map.of("type", definition.gemType()), "<gray>宝石类型: <yellow>%type%</yellow></gray>"));
        }
        extraLore.add(state.mode() == GemGuiMode.EXTRACT
                ? text("socket_extract_hint", "<gray>点击进入取出确认</gray>")
                : text("socket_occupied_hint", "<dark_gray>该槽已有宝石</dark_gray>"));
        if (selected) {
            extraLore.add(common("selected", "<green>已加入待确认操作</green>"));
        }
        if (gemItem != null) {
            return appendLore(gemItem, extraLore);
        }
        String embeddedState = text("socket_embedded", "已镶嵌");
        String embeddedTitle = slotTitle(socketSlot, socketIndex, embeddedState);
        Map<String, Object> replacements = slotReplacements(embeddedTitle, extraLore);
        replacements.put("slot", socketIndex);
        replacements.put("type", socketSlot.displayName());
        replacements.put("state", embeddedState);
        return buildConfiguredItem(guiSlot, Material.RED_DYE, embeddedTitle, extraLore, replacements);
    }

    private ItemStack renderPreviewDisplay(GemGuiSession state, GuiSlot slot) {
        if (state.mode() == GemGuiMode.UPGRADE) {
            return renderUpgradePreview(state, slot);
        }
        if (state.rerollMode()) {
            return renderRerollSummary(state, slot);
        }
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        String title = text("preview_name", "<gold>操作预览</gold>");
        List<String> lore = new ArrayList<>();
        if (!pendingOperation.active()) {
            lore.add(text("preview_empty_1", "<gray>这里会显示待确认操作预览</gray>"));
            lore.add(text("preview_empty_2", "<gray>点击目标插槽查看详情</gray>"));
            return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore,
                    previewReplacements(title, lore, state));
        }
        GemGuiSession.TargetResolution resolution = targetResolution(state);
        GemItemDefinition itemDefinition = resolution.definition();
        GemState gemState = resolution.state();
        GemItemDefinition.SocketSlot socketSlot = itemDefinition == null ? null : itemDefinition.slot(pendingOperation.slotIndex());
        lore.add(text("pending_action", Map.of("action", pendingText(pendingOperation.type())), "<gray>待执行: <yellow>%action%</yellow></gray>"));
        lore.add(text("target_slot", Map.of("slot", pendingOperation.slotIndex()), "<gray>目标插槽: <gold>#%slot%</gold></gray>"));
        if (socketSlot != null) {
            lore.add(socketType(socketSlot.displayName()));
        }
        String previewGem = common("none", "无");
        int previewLevel = 0;
        switch (pendingOperation.type()) {
            case INLAY -> {
                GemItemInstance instance = plugin.itemMatcher().readGemInstance(pendingOperation.inputItem());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                previewGem = definition == null ? common("unrecognized", "未识别") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level());
                lore.add(text("preview_gem", Map.of("gem", previewGem), "<gray>宝石: <yellow>%gem%</yellow></gray>"));
                if (instance != null) {
                    previewLevel = instance.level();
                    lore.add(text("preview_level", Map.of("level", previewLevel), "<gray>等级: <gold>Lv.%level%</gold></gray>"));
                }
            }
            case EXTRACT -> {
                GemItemInstance instance = gemState == null ? null : gemState.assignment(pendingOperation.slotIndex());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                previewGem = definition == null ? common("unknown", "未知") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level());
                lore.add(text("preview_extract_gem", Map.of("gem", previewGem), "<gray>取出宝石: <yellow>%gem%</yellow></gray>"));
            }
            default -> {
            }
        }
        lore.add(text("preview_confirm_hint", "<green>点击确认以执行</green>"));
        Map<String, Object> replacements = previewReplacements(title, lore, state);
        replacements.put("gem", previewGem);
        replacements.put("level", previewLevel);
        return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore, replacements);
    }

    private ItemStack renderConfirm(GemGuiSession state, GuiSlot slot) {
        if (state.rerollMode()) {
            boolean hasCandidate = rerollView(state) != null;
            String title = text(hasCandidate ? "reroll_confirm_name_active" : "reroll_generate_name",
                    hasCandidate ? "<green>确认洗炼</green>" : "<light_purple>生成候选</light_purple>");
            List<String> lore = List.of(text(hasCandidate ? "reroll_confirm_active_lore" : "reroll_generate_lore",
                    hasCandidate
                            ? "<gray>点击将候选写入主手宝石</gray>"
                            : "<gray>点击消耗费用并生成候选</gray>"));
            return buildConfiguredItem(slot,
                    hasCandidate ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE,
                    title, lore, confirmReplacements(title, lore, state));
        }
        if (state.mode() == GemGuiMode.UPGRADE) {
            GemUpgradeView view = resolveUpgradeView(state);
            boolean active = view != null && view.nextStage() != null && !state.processing();
            String title = text(active ? "upgrade_confirm_name_active" : "upgrade_confirm_name_inactive",
                    active ? "<green>升级宝石</green>" : "<gray>升级宝石</gray>");
            List<String> lore = List.of(text(active ? "upgrade_confirm_active_lore" : "upgrade_confirm_inactive_lore",
                    active ? "<gray>点击执行本次阶段强化</gray>"
                            : "<dark_gray>请先放入未达到上限的宝石</dark_gray>"));
            return buildConfiguredItem(slot,
                    active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    title, lore, confirmReplacements(title, lore, state));
        }
        if (!state.pendingOperation().active()) {
            String title = text("confirm_name_inactive", "<gray>确认操作</gray>");
            List<String> lore = List.of(
                    text("confirm_inactive_lore", "<dark_gray>请先选择一个待执行操作</dark_gray>")
            );
            return buildConfiguredItem(slot, Material.GRAY_STAINED_GLASS_PANE, title, lore,
                    confirmReplacements(title, lore, state));
        }
        String title = text("confirm_name_active", "<green>确认操作</green>");
        List<String> lore = List.of(
                text("confirm_active_lore", "<gray>点击执行当前操作</gray>"),
                text("pending_action", Map.of("action", pendingText(state.pendingOperation().type())), "<gray>待执行: <yellow>%action%</yellow></gray>")
        );
        return buildConfiguredItem(slot, Material.LIME_STAINED_GLASS_PANE, title, lore,
                confirmReplacements(title, lore, state));
    }

    private Map<String, Object> confirmReplacements(String title, List<String> lines, GemGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("confirm_title", title);
        replacements.put("confirm_lines", lines);
        replacements.put("action", pendingText(state.pendingOperation().type()));
        replacements.put("mode", modeText(state.mode()));
        return replacements;
    }

    private ItemStack buildModeButton(GuiSlot slot, boolean active, String title, String description) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + description + "</gray>");
        String stateText = active
                ? common("active", "<green>当前已启用</green>")
                : common("click_switch", "<dark_gray>点击切换</dark_gray>");
        lore.add(stateText);
        Material pane = active ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String modeTitle = (active ? "<green>" : "<red>") + title + (active ? "</green>" : "</red>");
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("mode_title", modeTitle);
        replacements.put("mode_lines", lore);
        replacements.put("mode", title);
        replacements.put("state", stateText);
        return buildConfiguredItem(slot, pane, modeTitle, lore, replacements);
    }

    private ItemStack buildConfiguredItem(GuiSlot slot,
            Material material,
            String name,
            List<String> lore,
            Map<String, ?> replacements) {
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? material.name() : slot.item();
        return GuiItemBuilder.build(
                GemGuiTemplates.configuredDefinition(slot, item, name, lore),
                replacements,
                plugin.coreLib().configuredItemService()
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
        return common("slot_title", Map.of("name", slot.displayName(), "slot", slotIndex, "state", stateText), "<white>%name% <gray>(#%slot% %state%)</gray></white>");
    }

    private String socketType(String displayName) {
        return common("socket_type", Map.of("type", displayName), "<gray>槽位类型: <yellow>%type%</yellow></gray>");
    }

    public String modeText(GemGuiMode mode) {
        return switch (mode) {
            case INLAY -> text("mode_inlay", "镶嵌");
            case UPGRADE -> text("mode_upgrade", "升级");
            case EXTRACT -> text("mode_extract", "取出");
            case REROLL_FULL -> text("mode_reroll_full", "洗炼");
            case REROLL_VALUE -> text("mode_reroll_value", "重算");
            case OPEN_SOCKET -> text("mode_open_socket", "开孔");
        };
    }

    private String pendingText(GemGuiSession.PendingType pendingType) {
        return switch (pendingType) {
            case INLAY -> text("pending_inlay_text", "镶嵌宝石");
            case EXTRACT -> text("pending_extract_text", "取出宝石");
            default -> common("none", "无");
        };
    }

    private String text(String key, String fallback) {
        return text(key, Map.of(), fallback);
    }

    private String text(String key, Map<String, ?> placeholders, String fallback) {
        return resolve(TEXT_PREFIX + key, placeholders, fallback);
    }

    private String common(String key, String fallback) {
        return common(key, Map.of(), fallback);
    }

    private String common(String key, Map<String, ?> placeholders, String fallback) {
        return resolve(COMMON_PREFIX + key, placeholders, fallback);
    }

    private String resolve(String key, Map<String, ?> placeholders, String fallback) {
        String value = plugin.messageService().message(key, placeholders);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    private record GemUpgradeView(GemItemInstance instance,
            GemDefinition definition,
            int nextLevel,
            GemDefinition.GemStage nextStage) {
    }
}

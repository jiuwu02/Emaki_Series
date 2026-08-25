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
                    text("mode_inlay_title", "Inlay Mode"),
                    text("mode_inlay_desc", "Hold a gem and click an opened empty slot"),
                    Material.GREEN_STAINED_GLASS_PANE);
            case "mode_upgrade" -> buildModeButton(slot, state.mode() == GemGuiMode.UPGRADE,
                    text("mode_upgrade_title", "Upgrade Mode"),
                    text("mode_upgrade_desc", "Place a gem and recipe materials to upgrade"),
                    Material.PURPLE_STAINED_GLASS_PANE);
            case "mode_extract" -> buildModeButton(slot, state.mode() == GemGuiMode.EXTRACT,
                    text("mode_extract_title", "Extract Mode"),
                    text("mode_extract_desc", "Click an inlaid gem slot"),
                    Material.YELLOW_STAINED_GLASS_PANE);
            case "mode_reroll_full" -> buildModeButton(slot, state.mode() == GemGuiMode.REROLL_FULL,
                    text("mode_reroll_full_title", "Reroll Mode"),
                    text("mode_reroll_full_desc", "Hold the gem in your main hand and reroll every affix"),
                    Material.MAGENTA_STAINED_GLASS_PANE);
            case "mode_reroll_value" -> buildModeButton(slot, state.mode() == GemGuiMode.REROLL_VALUE,
                    text("mode_reroll_value_title", "Recalibrate Mode"),
                    text("mode_reroll_value_desc", "Hold the gem in your main hand and recalibrate affix values"),
                    Material.CYAN_STAINED_GLASS_PANE);
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
            String title = text("reroll_target_name", "<light_purple>Main-hand Gem</light_purple>");
            List<String> lore = List.of(
                    text("reroll_target_lore_1", "<gray>Reroll always targets the gem in your main hand</gray>"),
                    text("reroll_target_lore_2", "<dark_gray>This slot accepts no items</dark_gray>")
            );
            return buildConfiguredItem(slot, Material.MAGENTA_STAINED_GLASS_PANE, title, lore,
                    targetReplacements(title, lore, state));
        }
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            String nameKey = state.mode() == GemGuiMode.UPGRADE ? "upgrade_target_empty_name" : "target_empty_name";
            String loreKey = state.mode() == GemGuiMode.UPGRADE ? "upgrade_target_empty_lore_1" : "target_empty_lore_1";
            String title = text(nameKey, state.mode() == GemGuiMode.UPGRADE
                    ? "<light_purple>Place Gem</light_purple>"
                    : "<aqua>Place Equipment</aqua>");
            List<String> lore = List.of(
                    text(loreKey, state.mode() == GemGuiMode.UPGRADE
                            ? "<gray>Place an upgradeable gem here</gray>"
                            : "<gray>Place gem equipment here</gray>"),
                    common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
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
                "<gray>Current mode: <yellow>%mode%</yellow></gray>"));
        if (view == null) {
            lore.add(text("upgrade_no_target_line_1", "<red>No upgradeable gem placed</red>"));
            lore.add(text("upgrade_no_target_line_2", "<gray>Place a staged gem in the target slot</gray>"));
        } else {
            lore.add(text("upgrade_current_level", Map.of("level", view.instance().level()),
                    "<gray>Current level: <yellow>Lv.%level%</yellow></gray>"));
            lore.add(text("upgrade_max_level", Map.of("max_level", view.definition().stages().maxLevel()),
                    "<gray>Maximum level: <gold>Lv.%max_level%</gold></gray>"));
            lore.add(view.nextStage() == null
                    ? text("upgrade_already_max", "<green>This gem is already at its configured maximum stage</green>")
                    : text("upgrade_next_stage", Map.of(
                            "level", view.nextLevel(),
                            "stage", nextStageName(view)),
                            "<gray>Next stage: <light_purple>Lv.%level% %stage%</light_purple></gray>"));
        }
        lore.add(plugin.strengthenIntegration() != null && plugin.strengthenIntegration().available()
                ? text("upgrade_framework_ready", "<green>Strengthen framework ready</green>")
                : text("upgrade_framework_unavailable", "<red>Strengthen framework unavailable</red>"));
        String title = text("upgrade_info_name", "<light_purple>Gem Upgrade</light_purple>");
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
        replacements.put("item", common("none", "None"));
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
                "<gray>Filled material slots: <yellow>%count%</yellow></gray>"));
        lore.add(text("upgrade_material_amount", Map.of("amount", totalAmount),
                "<gray>Total material amount: <gold>%amount%</gold></gray>"));
        if (view != null) {
            lore.add(text("upgrade_recipe_id", Map.of("recipe", view.definition().id()),
                    "<gray>Strengthen recipe: <aqua>%recipe%</aqua></gray>"));
        }
        lore.add(text("upgrade_material_help", "<gray>Place recipe materials in the seven lower slots</gray>"));
        String title = text("upgrade_summary_name", "<light_purple>Upgrade Materials</light_purple>");
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
                "<light_purple>Upgrade Material #%slot%</light_purple>");
        List<String> lore = List.of(
                text("upgrade_material_slot_lore", "<gray>Place a material stack here</gray>"),
                common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
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
        replacements.put("type", common("none", "None"));
        replacements.put("state", common("none", "None"));
        return replacements;
    }

    private ItemStack renderUpgradePreview(GemGuiSession state, GuiSlot slot) {
        GemUpgradeView view = resolveUpgradeView(state);
        String title = text("upgrade_preview_name", "<light_purple>Upgrade Preview</light_purple>");
        if (view == null || view.nextStage() == null) {
            List<String> lore = new ArrayList<>();
            lore.add(view == null
                    ? text("upgrade_preview_empty", "<gray>Place an upgradeable gem to preview its next stage</gray>")
                    : text("upgrade_already_max", "<green>This gem is already at its configured maximum stage</green>"));
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
                        "<gray>Level: <yellow>%previous_level%</yellow> → <green>%resulting_level%</green></gray>"),
                text("upgrade_preview_stage", Map.of("stage", nextStageName(view)),
                        "<gray>Stage: <light_purple>%stage%</light_purple></gray>"),
                text("preview_confirm_hint", "<green>Click confirm to execute</green>")
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
        replacements.put("gem", common("none", "None"));
        replacements.put("level", 0);
        replacements.put("stage", common("none", "None"));
        return replacements;
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
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        String title = text("info_name", "<gold>Instructions</gold>");
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())), "<gray>Current mode: <yellow>%mode%</yellow></gray>"));
        if (itemDefinition == null || gemState == null) {
            lore.add(text("no_target_line_1", "<red>No valid equipment placed</red>"));
            lore.add(text("no_target_line_2", "<gray>Please place equipment first</gray>"));
            return buildConfiguredItem(slot, Material.BOOK, title, lore,
                    infoReplacements(title, lore, state));
        }
        lore.add(text("equipment_definition", Map.of("item", itemDefinition.id()), "<gray>Equipment definition: <gold>%item%</gold></gray>"));
        lore.add(switch (state.mode()) {
            case INLAY -> text("inlay_help", "<gray>Hold a gem and click an opened empty slot</gray>");
            case UPGRADE -> text("upgrade_help", "<gray>Place a gem and recipe materials, then confirm</gray>");
            case EXTRACT -> text("extract_help", "<gray>Click an inlaid gem slot</gray>");
            case REROLL_FULL -> text("reroll_full_help", "<gray>Review the reroll candidate, then confirm</gray>");
            case REROLL_VALUE -> text("reroll_value_help", "<gray>Review the recalibrated values, then confirm</gray>");
            case OPEN_SOCKET -> text("default_help", "<gray>Equipment gem operation mode</gray>");
        });
        lore.add(text("unopened_help", "<gray>Use the socket opening GUI for unopened slots</gray>"));
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
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        String title = text("summary_name", "<gold>Gem Socket Summary</gold>");
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add(text("summary_empty_1", "<gray>Socket statistics will be shown here</gray>"));
            lore.add(text("summary_empty_2", "<gray>Place equipment to view socket counts</gray>"));
            return buildConfiguredItem(slot, Material.COMPASS, title, lore,
                    summaryReplacements(title, lore));
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        int embedded = gemState.socketAssignments().size();
        int free = Math.max(0, opened - embedded);
        int locked = Math.max(0, total - opened);
        lore.add(text("total_slots", Map.of("total", total), "<gray>Total sockets: <yellow>%total%</yellow></gray>"));
        lore.add(text("opened_slots", Map.of("opened", opened), "<gray>Opened sockets: <green>%opened%</green></gray>"));
        lore.add(text("embedded_slots", Map.of("embedded", embedded), "<gray>Inlaid gems: <aqua>%embedded%</aqua></gray>"));
        lore.add(text("free_opened_slots", Map.of("free", free), "<gray>Free opened sockets: <gold>%free%</gold></gray>"));
        lore.add(text("locked_slots", Map.of("locked", locked), "<gray>Locked sockets: <red>%locked%</red></gray>"));
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
        replacements.put("recipe", common("none", "None"));
        return replacements;
    }

    private ItemStack renderRerollInfo(GemGuiSession state, GuiSlot slot) {
        String title = text("info_name", "<gold>Instructions</gold>");
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())),
                "<gray>Current mode: <yellow>%mode%</yellow></gray>"));
        GemRerollSessionView view = rerollView(state);
        if (view == null) {
            lore.add(state.mode() == GemGuiMode.REROLL_VALUE
                    ? text("reroll_value_help", "<gray>Review the recalibrated values, then confirm</gray>")
                    : text("reroll_full_help", "<gray>Review the reroll candidate, then confirm</gray>"));
            lore.add(text("reroll_hold_hint", "<gray>Hold the gem in your main hand</gray>"));
            lore.add(text("reroll_generate_hint", "<green>Click confirm to generate a candidate</green>"));
            return buildConfiguredItem(slot, Material.BOOK, title, lore,
                    infoReplacements(title, lore, state));
        }
        long remainingSeconds = rerollRemainingSeconds(view);
        lore.add(text("reroll_candidate_open", "<green>A candidate is awaiting confirmation</green>"));
        lore.add(text("reroll_expires_in", Map.of("seconds", remainingSeconds),
                "<gray>Expires in: <gold>%seconds%s</gold></gray>"));
        lore.add(text("reroll_confirm_hint", "<gray>Click confirm to write it to the gem</gray>"));
        lore.add(text("reroll_cancel_hint", "<dark_gray>Switching mode or closing refunds the cost</dark_gray>"));
        Map<String, Object> replacements = infoReplacements(title, lore, state);
        replacements.put("seconds", remainingSeconds);
        return buildConfiguredItem(slot, Material.BOOK, title, lore, replacements);
    }

    private ItemStack renderRerollSummary(GemGuiSession state, GuiSlot slot) {
        GemRerollSessionView view = rerollView(state);
        String title = text("reroll_summary_name", "<gold>Reroll Comparison</gold>");
        List<String> lore = new ArrayList<>();
        if (view == null) {
            lore.add(text("reroll_summary_empty", "<gray>The candidate comparison appears after generating</gray>"));
            return buildConfiguredItem(slot, Material.COMPASS, title, lore,
                    summaryReplacements(title, lore));
        }
        lore.add(text("reroll_original_header", "<gray>Current affixes:</gray>"));
        lore.addAll(rerollAffixLines(view.originalAffixes(), "<dark_gray>- %affix%</dark_gray>",
                "reroll_original_line", "reroll_none_line"));
        lore.add("");
        lore.add(text("reroll_candidate_header", "<gray>Candidate affixes:</gray>"));
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
            lines.add(text(emptyKey, "<dark_gray>- none</dark_gray>"));
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
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            String emptyTitle = text("socket_name", "<white>Gem Socket</white>");
            List<String> emptyLore = List.of(
                    text("socket_empty_lore", "<gray>Sockets are shown after equipment is placed</gray>")
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
            lore.add(text("not_opened", "<red>Not opened yet</red>"));
            lore.add(text("open_in_open_gui", "<gray>Please use the socket opening GUI</gray>"));
            if (selected) {
                lore.add(common("selected", "<green>Added to pending operation</green>"));
            }
            String lockedState = text("socket_locked", "Locked");
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
                extraLore.add(text("slot_position", Map.of("slot", socketIndex), "<gray>Socket position: <gold>#%slot%</gold></gray>"));
                extraLore.add(socketType(socketSlot.displayName()));
                if (pendingDefinition != null) {
                    extraLore.add(text("gem_type", Map.of("type", pendingDefinition.gemType()), "<gray>Gem type: <yellow>%type%</yellow></gray>"));
                }
                if (pendingInstance != null) {
                    extraLore.add(text("gem_level", Map.of("level", pendingInstance.level()), "<gray>Gem level: <yellow>Lv.%level%</yellow></gray>"));
                }
                extraLore.add(text("pending_inlay", "<green>Pending inlay</green>"));
                if (pendingGemDisplay != null) {
                    return appendLore(pendingGemDisplay, extraLore);
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add(socketType(socketSlot.displayName()));
            lore.add(text("socket_current_empty", "<green>Currently empty</green>"));
            lore.add(state.mode() == GemGuiMode.INLAY
                    ? text("socket_inlay_hint", "<gray>Hold a gem and click this slot</gray>")
                    : text("socket_extract_empty", "<dark_gray>Empty slot cannot be extracted</dark_gray>"));
            if (selected) {
                lore.add(common("selected", "<green>Added to pending operation</green>"));
            }
            String emptyState = text("socket_empty", "Empty");
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
        extraLore.add(text("slot_position", Map.of("slot", socketIndex), "<gray>Socket position: <gold>#%slot%</gold></gray>"));
        extraLore.add(socketType(socketSlot.displayName()));
        extraLore.add(text("gem_level", Map.of("level", assigned.level()), "<gray>Gem level: <yellow>Lv.%level%</yellow></gray>"));
        if (definition != null) {
            extraLore.add(text("gem_type", Map.of("type", definition.gemType()), "<gray>Gem type: <yellow>%type%</yellow></gray>"));
        }
        extraLore.add(state.mode() == GemGuiMode.EXTRACT
                ? text("socket_extract_hint", "<gray>Click to preview extraction</gray>")
                : text("socket_occupied_hint", "<dark_gray>This slot already has a gem</dark_gray>"));
        if (selected) {
            extraLore.add(common("selected", "<green>Added to pending operation</green>"));
        }
        if (gemItem != null) {
            return appendLore(gemItem, extraLore);
        }
        String embeddedState = text("socket_embedded", "Inlaid");
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
        String title = text("preview_name", "<gold>Operation Preview</gold>");
        List<String> lore = new ArrayList<>();
        if (!pendingOperation.active()) {
            lore.add(text("preview_empty_1", "<gray>Pending operation preview will be shown here</gray>"));
            lore.add(text("preview_empty_2", "<gray>Click a target socket to view details</gray>"));
            return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore,
                    previewReplacements(title, lore, state));
        }
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        GemItemDefinition.SocketSlot socketSlot = itemDefinition == null ? null : itemDefinition.slot(pendingOperation.slotIndex());
        lore.add(text("pending_action", Map.of("action", pendingText(pendingOperation.type())), "<gray>Pending: <yellow>%action%</yellow></gray>"));
        lore.add(text("target_slot", Map.of("slot", pendingOperation.slotIndex()), "<gray>Target socket: <gold>#%slot%</gold></gray>"));
        if (socketSlot != null) {
            lore.add(socketType(socketSlot.displayName()));
        }
        String previewGem = common("none", "None");
        int previewLevel = 0;
        switch (pendingOperation.type()) {
            case INLAY -> {
                GemItemInstance instance = plugin.itemMatcher().readGemInstance(pendingOperation.inputItem());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                previewGem = definition == null ? common("unrecognized", "Unrecognized") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level());
                lore.add(text("preview_gem", Map.of("gem", previewGem), "<gray>Gem: <yellow>%gem%</yellow></gray>"));
                if (instance != null) {
                    previewLevel = instance.level();
                    lore.add(text("preview_level", Map.of("level", previewLevel), "<gray>Level: <gold>Lv.%level%</gold></gray>"));
                }
            }
            case EXTRACT -> {
                GemItemInstance instance = gemState == null ? null : gemState.assignment(pendingOperation.slotIndex());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                previewGem = definition == null ? common("unknown", "Unknown") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level());
                lore.add(text("preview_extract_gem", Map.of("gem", previewGem), "<gray>Extract gem: <yellow>%gem%</yellow></gray>"));
            }
            default -> {
            }
        }
        lore.add(text("preview_confirm_hint", "<green>Click confirm to execute</green>"));
        Map<String, Object> replacements = previewReplacements(title, lore, state);
        replacements.put("gem", previewGem);
        replacements.put("level", previewLevel);
        return buildConfiguredItem(slot, Material.WRITABLE_BOOK, title, lore, replacements);
    }

    private ItemStack renderConfirm(GemGuiSession state, GuiSlot slot) {
        if (state.rerollMode()) {
            boolean hasCandidate = rerollView(state) != null;
            String title = text(hasCandidate ? "reroll_confirm_name_active" : "reroll_generate_name",
                    hasCandidate ? "<green>Confirm Reroll</green>" : "<light_purple>Generate Candidate</light_purple>");
            List<String> lore = List.of(text(hasCandidate ? "reroll_confirm_active_lore" : "reroll_generate_lore",
                    hasCandidate
                            ? "<gray>Click to write the candidate to your main-hand gem</gray>"
                            : "<gray>Click to charge the cost and generate a candidate</gray>"));
            return buildConfiguredItem(slot,
                    hasCandidate ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE,
                    title, lore, confirmReplacements(title, lore, state));
        }
        if (state.mode() == GemGuiMode.UPGRADE) {
            GemUpgradeView view = resolveUpgradeView(state);
            boolean active = view != null && view.nextStage() != null && !state.processing();
            String title = text(active ? "upgrade_confirm_name_active" : "upgrade_confirm_name_inactive",
                    active ? "<green>Upgrade Gem</green>" : "<gray>Upgrade Gem</gray>");
            List<String> lore = List.of(text(active ? "upgrade_confirm_active_lore" : "upgrade_confirm_inactive_lore",
                    active ? "<gray>Click to execute the staged enhancement</gray>"
                            : "<dark_gray>Place a staged gem below its maximum level first</dark_gray>"));
            return buildConfiguredItem(slot,
                    active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    title, lore, confirmReplacements(title, lore, state));
        }
        if (!state.pendingOperation().active()) {
            String title = text("confirm_name_inactive", "<gray>Confirm Operation</gray>");
            List<String> lore = List.of(
                    text("confirm_inactive_lore", "<dark_gray>Please select a pending operation first</dark_gray>")
            );
            return buildConfiguredItem(slot, Material.GRAY_STAINED_GLASS_PANE, title, lore,
                    confirmReplacements(title, lore, state));
        }
        String title = text("confirm_name_active", "<green>Confirm Operation</green>");
        List<String> lore = List.of(
                text("confirm_active_lore", "<gray>Click to execute current operation</gray>"),
                text("pending_action", Map.of("action", pendingText(state.pendingOperation().type())), "<gray>Pending: <yellow>%action%</yellow></gray>")
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

    private ItemStack buildModeButton(GuiSlot slot, boolean active, String title, String description, Material material) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + description + "</gray>");
        String stateText = active
                ? common("active", "<green>Currently enabled</green>")
                : common("click_switch", "<dark_gray>Click to switch</dark_gray>");
        lore.add(stateText);
        String modeTitle = (active ? "<green>" : "<yellow>") + title + (active ? "</green>" : "</yellow>");
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("mode_title", modeTitle);
        replacements.put("mode_lines", lore);
        replacements.put("mode", title);
        replacements.put("state", stateText);
        return buildConfiguredItem(slot, material, modeTitle, lore, replacements);
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
        return common("socket_type", Map.of("type", displayName), "<gray>Socket type: <yellow>%type%</yellow></gray>");
    }

    private String modeText(GemGuiMode mode) {
        return switch (mode) {
            case INLAY -> text("mode_inlay", "Inlay");
            case UPGRADE -> text("mode_upgrade", "Upgrade");
            case EXTRACT -> text("mode_extract", "Extract");
            case REROLL_FULL -> text("mode_reroll_full", "Reroll");
            case REROLL_VALUE -> text("mode_reroll_value", "Recalibrate");
            case OPEN_SOCKET -> text("mode_open_socket", "Open Socket");
        };
    }

    private String pendingText(GemGuiSession.PendingType pendingType) {
        return switch (pendingType) {
            case INLAY -> text("pending_inlay_text", "Inlay Gem");
            case EXTRACT -> text("pending_extract_text", "Extract Gem");
            default -> common("none", "None");
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

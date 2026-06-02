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
            case "mode_extract" -> buildModeButton(slot, state.mode() == GemGuiMode.EXTRACT,
                    text("mode_extract_title", "Extract Mode"),
                    text("mode_extract_desc", "Click an inlaid gem slot"),
                    Material.YELLOW_STAINED_GLASS_PANE);
            case "confirm" -> renderConfirm(state, slot);
            default -> GuiItemBuilder.build(slot.item(), slot.components(), 1, Map.of(),
                    (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount));
        };
    }

    public void refreshGui(GemGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetItem(GemGuiSession state, GuiSlot slot) {
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            return buildConfiguredItem(slot, Material.LIGHT_BLUE_STAINED_GLASS_PANE, text("target_empty_name", "<aqua>Place Equipment</aqua>"), List.of(
                    text("target_empty_lore_1", "<gray>Place gem equipment here</gray>"),
                    common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
            ));
        }
        return targetItem.clone();
    }

    private ItemStack renderSocketInfo(GemGuiSession state, GuiSlot slot) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        lore.add(text("mode_line", Map.of("mode", modeText(state.mode())), "<gray>Current mode: <yellow>%mode%</yellow></gray>"));
        if (itemDefinition == null || gemState == null) {
            lore.add(text("no_target_line_1", "<red>No valid equipment placed</red>"));
            lore.add(text("no_target_line_2", "<gray>Please place equipment first</gray>"));
            return buildConfiguredItem(slot, Material.BOOK, text("info_name", "<gold>Instructions</gold>"), lore);
        }
        lore.add(text("equipment_definition", Map.of("item", itemDefinition.id()), "<gray>Equipment definition: <gold>%item%</gold></gray>"));
        lore.add(switch (state.mode()) {
            case INLAY -> text("inlay_help", "<gray>Hold a gem and click an opened empty slot</gray>");
            case EXTRACT -> text("extract_help", "<gray>Click an inlaid gem slot</gray>");
            case OPEN_SOCKET, UPGRADE -> text("default_help", "<gray>Equipment gem operation mode</gray>");
        });
        lore.add(text("unopened_help", "<gray>Use the socket opening GUI for unopened slots</gray>"));
        return buildConfiguredItem(slot, Material.BOOK, text("info_name", "<gold>Instructions</gold>"), lore);
    }

    private ItemStack renderSocketSummary(GemGuiSession state, GuiSlot slot) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add(text("summary_empty_1", "<gray>Socket statistics will be shown here</gray>"));
            lore.add(text("summary_empty_2", "<gray>Place equipment to view socket counts</gray>"));
            return buildConfiguredItem(slot, Material.COMPASS, text("summary_name", "<gold>Gem Socket Summary</gold>"), lore);
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        int embedded = gemState.socketAssignments().size();
        lore.add(text("total_slots", Map.of("total", total), "<gray>Total sockets: <yellow>%total%</yellow></gray>"));
        lore.add(text("opened_slots", Map.of("opened", opened), "<gray>Opened sockets: <green>%opened%</green></gray>"));
        lore.add(text("embedded_slots", Map.of("embedded", embedded), "<gray>Inlaid gems: <aqua>%embedded%</aqua></gray>"));
        lore.add(text("free_opened_slots", Map.of("free", Math.max(0, opened - embedded)), "<gray>Free opened sockets: <gold>%free%</gold></gray>"));
        lore.add(text("locked_slots", Map.of("locked", Math.max(0, total - opened)), "<gray>Locked sockets: <red>%locked%</red></gray>"));
        return buildConfiguredItem(slot, Material.COMPASS, text("summary_name", "<gold>Gem Socket Summary</gold>"), lore);
    }

    private ItemStack renderSocketSlot(GemGuiSession state, int displayIndex, GuiSlot guiSlot) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            return buildConfiguredItem(guiSlot, Material.WHITE_STAINED_GLASS_PANE, text("socket_name", "<white>Gem Socket</white>"), List.of(
                    text("socket_empty_lore", "<gray>Sockets are shown after equipment is placed</gray>")
            ));
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
            return buildConfiguredItem(guiSlot, Material.GRAY_STAINED_GLASS_PANE, slotTitle(socketSlot, socketIndex, text("socket_locked", "Locked")), lore);
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
            return buildConfiguredItem(guiSlot, baseSocketMaterial(socketSlot.type()), slotTitle(socketSlot, socketIndex, text("socket_empty", "Empty")), lore);
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
        return buildConfiguredItem(guiSlot, Material.RED_DYE, slotTitle(socketSlot, socketIndex, text("socket_embedded", "Inlaid")), extraLore);
    }

    private ItemStack renderPreviewDisplay(GemGuiSession state, GuiSlot slot) {
        GemGuiSession.PendingOperation pendingOperation = state.pendingOperation();
        List<String> lore = new ArrayList<>();
        if (!pendingOperation.active()) {
            lore.add(text("preview_empty_1", "<gray>Pending operation preview will be shown here</gray>"));
            lore.add(text("preview_empty_2", "<gray>Click a target socket to view details</gray>"));
            return buildConfiguredItem(slot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Operation Preview</gold>"), lore);
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
        switch (pendingOperation.type()) {
            case INLAY -> {
                GemItemInstance instance = plugin.itemMatcher().readGemInstance(pendingOperation.inputItem());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                lore.add(text("preview_gem", Map.of("gem", definition == null ? common("unrecognized", "Unrecognized") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level())), "<gray>Gem: <yellow>%gem%</yellow></gray>"));
                if (instance != null) {
                    lore.add(text("preview_level", Map.of("level", instance.level()), "<gray>Level: <gold>Lv.%level%</gold></gray>"));
                }
            }
            case EXTRACT -> {
                GemItemInstance instance = gemState == null ? null : gemState.assignment(pendingOperation.slotIndex());
                GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
                lore.add(text("preview_extract_gem", Map.of("gem", definition == null ? common("unknown", "Unknown") : plugin.itemFactory().resolveGemDisplayName(definition, instance.level())), "<gray>Extract gem: <yellow>%gem%</yellow></gray>"));
            }
            default -> {
            }
        }
        lore.add(text("preview_confirm_hint", "<green>Click confirm to execute</green>"));
        return buildConfiguredItem(slot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Operation Preview</gold>"), lore);
    }

    private ItemStack renderConfirm(GemGuiSession state, GuiSlot slot) {
        if (!state.pendingOperation().active()) {
            return buildConfiguredItem(slot, Material.GRAY_STAINED_GLASS_PANE, text("confirm_name_inactive", "<gray>Confirm Operation</gray>"), List.of(
                    text("confirm_inactive_lore", "<dark_gray>Please select a pending operation first</dark_gray>")
            ));
        }
        return buildConfiguredItem(slot, Material.LIME_STAINED_GLASS_PANE, text("confirm_name_active", "<green>Confirm Operation</green>"), List.of(
                text("confirm_active_lore", "<gray>Click to execute current operation</gray>"),
                text("pending_action", Map.of("action", pendingText(state.pendingOperation().type())), "<gray>Pending: <yellow>%action%</yellow></gray>")
        ));
    }

    private ItemStack buildModeButton(GuiSlot slot, boolean active, String title, String description, Material material) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + description + "</gray>");
        lore.add(active ? common("active", "<green>Currently enabled</green>") : common("click_switch", "<dark_gray>Click to switch</dark_gray>"));
        return buildConfiguredItem(slot, material, (active ? "<green>" : "<yellow>") + title + (active ? "</green>" : "</yellow>"), lore);
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, Material material, String name, List<String> lore) {
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? material.name() : slot.item();
        return GuiItemBuilder.build(
                item,
                configuredComponents(slot, name, lore),
                1,
                Map.of(),
                (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount)
        );
    }

    private ItemComponentParser.ItemComponents configuredComponents(GuiSlot slot, String name, List<String> lore) {
        ItemComponentParser.ItemComponents configured = slot == null ? null : slot.components();
        if (configured == null) {
            return new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of());
        }
        boolean hasDisplayNameConfig = configured.displayNameConfig() != null;
        boolean hasLoreConfig = configured.loreConfig() != null;
        String displayName = Texts.isBlank(configured.displayName()) && !hasDisplayNameConfig ? name : configured.displayName();
        boolean loreConfigured = configured.loreConfigured();
        return new ItemComponentParser.ItemComponents(
                displayName,
                true,
                loreConfigured ? configured.lore() : lore,
                configured.itemModel(),
                configured.customModelData(),
                configured.enchantments(),
                configured.hiddenComponents(),
                hasDisplayNameConfig ? configured.displayNameConfig() : null,
                hasLoreConfig && loreConfigured ? configured.loreConfig() : null
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
            case EXTRACT -> text("mode_extract", "Extract");
            case OPEN_SOCKET -> text("mode_open_socket", "Open Socket");
            case UPGRADE -> text("mode_upgrade", "Upgrade");
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
}

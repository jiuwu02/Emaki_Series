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

    private static final String TEXT_PREFIX = "gui_text.open.";
    private static final String COMMON_PREFIX = "gui_text.common.";

    private final EmakiGemPlugin plugin;

    GemOpenGuiRenderer(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack renderSlot(GemOpenGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        return switch (type) {
            case "target_item" -> renderTargetItem(state, slot);
            case "socket_info" -> renderSocketInfo(state, slot);
            case "opener_item" -> renderOpenerItem(state, slot);
            case "socket_slot" -> renderSocketSlot(state, resolvedSlot.slotIndex(), slot);
            case "preview_display" -> renderPreview(state, slot);
            case "confirm" -> renderConfirm(state, slot);
            default -> GuiItemBuilder.build(slot.item(), slot.components(), 1, Map.of(),
                    (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount));
        };
    }

    public void refreshGui(GemOpenGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetItem(GemOpenGuiSession state, GuiSlot guiSlot) {
        ItemStack targetItem = state.targetItem();
        if (targetItem == null) {
            return buildItem(guiSlot, Material.LIGHT_BLUE_STAINED_GLASS_PANE, text("target_empty_name", "<aqua>Place Equipment</aqua>"), List.of(
                    text("target_empty_lore_1", "<gray>Place equipment here</gray>"),
                    common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
            ));
        }
        return targetItem.clone();
    }

    private ItemStack renderSocketInfo(GemOpenGuiSession state, GuiSlot guiSlot) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        List<String> lore = new ArrayList<>();
        if (itemDefinition == null || gemState == null) {
            lore.add(text("no_target_line_1", "<red>No valid equipment placed</red>"));
            lore.add(text("no_target_line_2", "<gray>Please place equipment first</gray>"));
            return buildItem(guiSlot, Material.BOOK, text("info_name", "<gold>Socket Info</gold>"), lore);
        }
        int total = itemDefinition.slots().size();
        int opened = gemState.openedSlotIndexes().size();
        lore.add(text("equipment_definition", Map.of("item", itemDefinition.id()), "<gray>Equipment definition: <gold>{item}</gold></gray>"));
        lore.add(text("opened_count", Map.of("opened", opened, "total", total), "<gray>Opened: <green>{opened}</green>/<yellow>{total}</yellow></gray>"));
        lore.add(text("locked_count", Map.of("locked", Math.max(0, total - opened)), "<gray>Locked: <yellow>{locked}</yellow></gray>"));
        lore.add(text("info_hint", "<gray>Place an opener, then click a locked slot</gray>"));
        return buildItem(guiSlot, Material.BOOK, text("info_name", "<gold>Socket Info</gold>"), lore);
    }

    private ItemStack renderOpenerItem(GemOpenGuiSession state, GuiSlot guiSlot) {
        ItemStack openerItem = state.openerItem();
        if (openerItem == null) {
            return buildItem(guiSlot, Material.AMETHYST_SHARD, text("opener_empty_name", "<light_purple>Place Socket Opener</light_purple>"), List.of(
                    text("opener_empty_lore_1", "<gray>Place a socket opener here</gray>"),
                    common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
            ));
        }
        return openerItem.clone();
    }

    private ItemStack renderSocketSlot(GemOpenGuiSession state, int displayIndex, GuiSlot guiSlot) {
        ItemStack targetItem = state.targetItem();
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(targetItem);
        GemState gemState = itemDefinition == null ? null : plugin.stateService().resolveState(targetItem, itemDefinition);
        if (itemDefinition != null && displayIndex >= itemDefinition.slots().size()) {
            return hiddenSlot();
        }
        if (itemDefinition == null || gemState == null) {
            return buildItem(guiSlot, Material.BLACK_STAINED_GLASS_PANE, text("unused_slot_name", "<dark_gray>Unused Slot</dark_gray>"), List.of(
                    text("unused_slot_lore", "<dark_gray>This equipment does not have this slot</dark_gray>")
            ));
        }
        GemItemDefinition.SocketSlot slot = itemDefinition.slots().get(displayIndex);
        int slotIndex = slot.index();
        boolean selected = state.selectedSlotIndex() == slotIndex;
        boolean hasOpenerItem = plugin.itemMatcher().isOpenerItem(state.mutableOpenerItem());
        SocketOpenerConfig opener = plugin.itemMatcher().matchOpenerForType(state.mutableOpenerItem(), slot.type());
        if (gemState.isOpened(slotIndex)) {
            return buildItem(guiSlot, baseSocketMaterial(slot.type()), slotTitle(slot, slotIndex, text("socket_opened", "Opened")), List.of(
                    text("already_opened_1", "<gray>This slot is already opened</gray>"),
                    text("already_opened_2", "<dark_gray>Please open a locked slot instead</dark_gray>")
            ));
        }
        List<String> lore = new ArrayList<>();
        lore.add(socketType(slot.displayName()));
        lore.add(text("not_opened", "<red>Not opened yet</red>"));
        if (!hasOpenerItem) {
            lore.add(text("place_opener", "<gray>Please place a socket opener first</gray>"));
        } else if (opener == null) {
            lore.add(text("opener_incompatible", "<red>The current opener cannot open this slot type</red>"));
        } else {
            lore.add(text("click_select", "<gray>Click to select this slot for opening</gray>"));
        }
        if (selected) {
            lore.add(text("selected", "<green>This slot is selected</green>"));
        }
        return buildItem(guiSlot, Material.GRAY_STAINED_GLASS_PANE, slotTitle(slot, slotIndex, text("socket_locked", "Locked")), lore);
    }

    private ItemStack renderPreview(GemOpenGuiSession state, GuiSlot guiSlot) {
        List<String> lore = new ArrayList<>();
        if (state.mutableTargetItem() == null) {
            lore.add(text("preview_empty", "<gray>Opening preview will be shown here</gray>"));
            return buildItem(guiSlot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Opening Preview</gold>"), lore);
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(state.mutableTargetItem());
        GemItemDefinition.SocketSlot slot = itemDefinition == null ? null : itemDefinition.slot(state.selectedSlotIndex());
        lore.add(text("preview_equipment", Map.of("item", itemDefinition == null ? common("unrecognized", "Unrecognized") : itemDefinition.id()), "<gray>Equipment: <yellow>{item}</yellow></gray>"));
        SocketOpenerConfig opener = slot == null
                ? plugin.itemMatcher().matchOpenerItem(state.mutableOpenerItem())
                : plugin.itemMatcher().matchOpenerForType(state.mutableOpenerItem(), slot.type());
        lore.add(text("preview_opener", Map.of("opener", openerText(state, opener)), "<gray>Opener: <yellow>{opener}</yellow></gray>"));
        lore.add(text("preview_slot", Map.of("slot", slot == null ? text("slot_not_selected", "Not selected") : "#" + slot.index() + " " + slot.displayName()), "<gray>Target slot: <yellow>{slot}</yellow></gray>"));
        lore.add(text("preview_hint", "<gray>Confirming will open the selected locked slot once</gray>"));
        return buildItem(guiSlot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Opening Preview</gold>"), lore);
    }

    private ItemStack renderConfirm(GemOpenGuiSession state, GuiSlot guiSlot) {
        if (state.mutableTargetItem() == null || state.mutableOpenerItem() == null || state.selectedSlotIndex() < 0) {
            return buildItem(guiSlot, Material.GRAY_STAINED_GLASS_PANE, text("confirm_name_inactive", "<gray>Confirm Opening</gray>"), List.of(
                    text("confirm_inactive_lore", "<dark_gray>Please place equipment, an opener, and select a locked slot first</dark_gray>")
            ));
        }
        return buildItem(guiSlot, Material.LIME_STAINED_GLASS_PANE, text("confirm_name_active", "<green>Confirm Opening</green>"), List.of(
                text("confirm_active_lore", "<gray>Click to execute current opening operation</gray>")
        ));
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        return buildItem(null, material, name, lore);
    }

    private ItemStack buildItem(GuiSlot slot, Material material, String name, List<String> lore) {
        return GuiItemBuilder.build(
                Texts.isBlank(slot == null ? null : slot.item()) ? material.name() : slot.item(),
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
        return common("slot_title", Map.of("name", slot.displayName(), "slot", slotIndex, "state", stateText), "<white>{name} <gray>(#{slot} {state})</gray></white>");
    }

    private String socketType(String displayName) {
        return common("socket_type", Map.of("type", displayName), "<gray>Socket type: <yellow>{type}</yellow></gray>");
    }

    private String openerText(GemOpenGuiSession state, SocketOpenerConfig opener) {
        if (state.mutableOpenerItem() == null) {
            return text("opener_not_placed", "Not placed");
        }
        return opener == null ? text("opener_placed_pending", "Placed, waiting for slot selection") : opener.id();
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

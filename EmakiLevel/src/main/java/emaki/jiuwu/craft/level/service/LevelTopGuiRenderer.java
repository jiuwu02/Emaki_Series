package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

final class LevelTopGuiRenderer {

    private final EmakiLevelPlugin plugin;
    private final LevelTopGuiService guiService;

    LevelTopGuiRenderer(EmakiLevelPlugin plugin, LevelTopGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        return switch (Texts.lower(slot.type())) {
            case "top_entry" -> renderTopEntry(session, resolvedSlot, slot);
            case "page_info" -> renderPageInfo(session, slot);
            case "type_info" -> renderTypeInfo(session, slot);
            default -> null;
        };
    }

    private ItemStack renderTopEntry(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot, GuiSlot slot) {
        String typeId = guiService.typeId(session);
        int index = guiService.entryIndex(session, resolvedSlot);
        List<LevelTopService.TopEntry> entries = plugin.topService().top(typeId, Math.max(index + 1, 1));
        if (index < 0 || index >= entries.size()) {
            return new ItemStack(Material.AIR);
        }
        LevelTopService.TopEntry entry = entries.get(index);
        Map<String, Object> replacements = replacements(session, entry, index + 1);
        String fallbackItem = switch (index) {
            case 0 -> "gold_block";
            case 1 -> "iron_block";
            case 2 -> "copper_block";
            default -> "paper";
        };
        return buildConfiguredItem(slot, fallbackItem, plugin.messages().message("gui.top.entry_name", replacements), List.of(plugin.messages().message("gui.top.entry_lore", replacements)), replacements);
    }

    private ItemStack renderPageInfo(GuiSession session, GuiSlot slot) {
        Map<String, Object> replacements = pageReplacements(session);
        return buildConfiguredItem(slot, "book", plugin.messages().message("gui.top.page_info_name", replacements), List.of(plugin.messages().message("gui.top.page_info_lore", replacements)), replacements);
    }

    private ItemStack renderTypeInfo(GuiSession session, GuiSlot slot) {
        Map<String, Object> replacements = pageReplacements(session);
        return buildConfiguredItem(slot, "experience_bottle", plugin.messages().message("gui.top.type_info_name", replacements), List.of(plugin.messages().message("gui.top.type_info_lore", replacements)), replacements);
    }

    private Map<String, Object> replacements(GuiSession session, LevelTopService.TopEntry entry, int rank) {
        Map<String, Object> replacements = pageReplacements(session);
        replacements.put("rank", rank);
        replacements.put("player", entry.name());
        replacements.put("level", entry.level());
        replacements.put("total_exp", PlayerLevelService.format(entry.totalExp()));
        return replacements;
    }

    private Map<String, Object> pageReplacements(GuiSession session) {
        String typeId = guiService.typeId(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("type", typeId);
        replacements.put("type_display_name", plugin.typeRegistry().type(typeId).map(type -> type.displayName()).orElse(typeId));
        replacements.put("page", guiService.page(session) + 1);
        replacements.put("current_page", guiService.page(session) + 1);
        replacements.put("total_pages", guiService.totalPages(session));
        replacements.put("entry_count", plugin.topService().top(typeId, Integer.MAX_VALUE).size());
        return replacements;
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, String fallbackItem, String fallbackName, List<String> fallbackLore, Map<String, ?> replacements) {
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? fallbackItem : slot.item();
        return GuiItemBuilder.build(
                slot,
                Texts.isBlank(item) ? "paper" : item,
                fallbackName,
                fallbackLore == null ? List.of() : fallbackLore,
                replacements == null ? Map.of() : replacements,
                plugin.coreLib().configuredItemService()
        );
    }
}

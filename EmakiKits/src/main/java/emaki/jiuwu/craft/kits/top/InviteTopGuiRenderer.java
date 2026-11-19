package emaki.jiuwu.craft.kits.top;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

final class InviteTopGuiRenderer implements GuiRenderer {

    private final InviteTopService top;

    InviteTopGuiRenderer(InviteTopService top) {
        this.top = top;
    }

    @Override
    public ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = slot.type() == null ? "" : Texts.lower(slot.type());
        return switch (type) {
            case "top_entry" -> renderEntry(session, resolvedSlot, slot);
            case "page_info" -> renderPageInfo(session, slot);
            case "page_prev" -> renderNav(session, slot, "arrow", "top.prev_name", "top.prev_lore");
            case "page_next" -> renderNav(session, slot, "arrow", "top.next_name", "top.next_lore");
            case "close" -> build(slot, "barrier", top.messages().message("top.close_name"), List.of(), Map.of());
            default -> null;
        };
    }

    private ItemStack renderEntry(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot, GuiSlot slot) {
        int index = top.entryIndex(session, resolvedSlot);
        List<InviteTopService.InviteEntry> entries =
                top.snapshot(session.viewer() == null ? null : session.viewer().getUniqueId());
        if (entries == null || index < 0 || index >= entries.size()) {
            return new ItemStack(Material.AIR);
        }
        InviteTopService.InviteEntry entry = entries.get(index);
        Map<String, Object> replacements = replacements(session);
        replacements.put("rank", index + 1);
        replacements.put("player", InviteTopService.playerName(entry.uuid()));
        replacements.put("count", entry.count());
        String fallbackItem = switch (index) {
            case 0 -> "gold_block";
            case 1 -> "iron_block";
            case 2 -> "copper_block";
            default -> "paper";
        };
        return build(slot, fallbackItem,
                top.messages().message("top.entry_name", replacements),
                List.of(top.messages().message("top.entry_lore", replacements)),
                replacements);
    }

    private ItemStack renderPageInfo(GuiSession session, GuiSlot slot) {
        Map<String, Object> replacements = replacements(session);
        return build(slot, "book",
                top.messages().message("top.page_info_name", replacements),
                List.of(top.messages().message("top.page_info_lore", replacements)),
                replacements);
    }

    private ItemStack renderNav(GuiSession session, GuiSlot slot, String fallbackItem,
                                String nameKey, String loreKey) {
        Map<String, Object> replacements = replacements(session);
        return build(slot, fallbackItem,
                top.messages().message(nameKey, replacements),
                List.of(top.messages().message(loreKey, replacements)),
                replacements);
    }

    private Map<String, Object> replacements(GuiSession session) {
        UUID viewer = session.viewer() == null ? null : session.viewer().getUniqueId();
        List<InviteTopService.InviteEntry> entries = top.snapshot(viewer);
        int count = entries == null ? 0 : entries.size();
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("page", top.page(session) + 1);
        replacements.put("current_page", top.page(session) + 1);
        replacements.put("total_pages", top.totalPages(count, session.template()));
        replacements.put("entry_count", count);
        return replacements;
    }

    private ItemStack build(GuiSlot slot, String fallbackItem, String fallbackName,
                            List<String> fallbackLore, Map<String, ?> replacements) {
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? fallbackItem : slot.item();
        return GuiItemBuilder.build(
                slot,
                Texts.isBlank(item) ? "paper" : item,
                fallbackName,
                fallbackLore == null ? List.of() : fallbackLore,
                replacements == null ? Map.of() : replacements,
                top.guiService().configuredItemService());
    }
}

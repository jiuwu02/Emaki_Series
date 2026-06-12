package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

final class LevelGuiRenderer {

    private final EmakiLevelPlugin plugin;
    private final LevelGuiService guiService;

    LevelGuiRenderer(EmakiLevelPlugin plugin, LevelGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        return switch (Texts.lower(slot.type())) {
            case "level_type" -> renderLevelType(session, resolvedSlot, slot);
            case "page_info" -> renderPageInfo(session, slot);
            case "top_button" -> renderTopButton(session, slot);
            default -> null;
        };
    }

    private ItemStack renderLevelType(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot, GuiSlot slot) {
        LevelTypeConfig type = guiService.typeAt(session, resolvedSlot);
        if (type == null) {
            return new ItemStack(Material.AIR);
        }
        PlayerLevelData data = plugin.dataStore().getOrLoad(session.viewer().getUniqueId(), plugin.typeRegistry().asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        if (entry == null) {
            return new ItemStack(Material.AIR);
        }
        Map<String, Object> replacements = replacements(type, entry);
        String fallbackItem = fallbackItem(type, entry, replacements);
        List<String> lore = new ArrayList<>();
        lore.add(plugin.messages().message("gui.level.lore.level", replacements));
        lore.add(plugin.messages().message("gui.level.lore.exp", replacements));
        lore.add(plugin.messages().message("gui.level.lore.total", replacements));
        lore.add(plugin.messages().message("gui.level.lore.progress", replacements));
        lore.add("");
        lore.add(plugin.messages().message("gui.level.lore.left_click", replacements));
        if (Boolean.TRUE.equals(replacements.get("manual_upgrade"))) {
            lore.add(plugin.messages().message("gui.level.lore.right_click", replacements));
            lore.add(plugin.messages().message("gui.level.lore.shift_right_click", replacements));
        }
        return buildConfiguredItem(slot, fallbackItem, "%type_display_name% <gray>Lv.%level%</gray>", lore, replacements);
    }

    private ItemStack renderPageInfo(GuiSession session, GuiSlot slot) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("page", guiService.page(session) + 1);
        replacements.put("current_page", guiService.page(session) + 1);
        replacements.put("total_pages", guiService.totalPages(session));
        replacements.put("type_count", guiService.types().size());
        return buildConfiguredItem(slot, "book", plugin.messages().message("gui.level.page_info_name", replacements), List.of(plugin.messages().message("gui.level.page_info_lore", replacements)), replacements);
    }

    private ItemStack renderTopButton(GuiSession session, GuiSlot slot) {
        String typeId = guiService.selectedType(session);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("type", typeId);
        replacements.put("type_display_name", plugin.typeRegistry().type(typeId).map(LevelTypeConfig::displayName).orElse(typeId));
        return buildConfiguredItem(slot, "gold_ingot", plugin.messages().message("gui.level.top_button_name", replacements), List.of(plugin.messages().message("gui.level.top_button_lore", replacements)), replacements);
    }

    private Map<String, Object> replacements(LevelTypeConfig type, PlayerLevelEntry entry) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        int targetLevel = Math.min(type.maxLevel(), entry.level() + 1);
        double required = plugin.requirementService().requiredExp(type, entry, targetLevel);
        double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
        boolean maxLevel = entry.level() >= type.maxLevel();
        boolean manual = type.upgrade().manualUpgrade();
        boolean canLevelUp = type.enabled() && manual && !maxLevel && required > 0D && entry.exp() + 1.0E-9D >= required;
        replacements.put("type", type.id());
        replacements.put("type_display_name", type.displayName());
        replacements.put("level", entry.level());
        replacements.put("max_level", type.maxLevel());
        replacements.put("exp", PlayerLevelService.format(entry.exp()));
        replacements.put("total_exp", PlayerLevelService.format(entry.totalExp()));
        replacements.put("required_exp", PlayerLevelService.format(required));
        replacements.put("progress", PlayerLevelService.format(progress));
        replacements.put("progress_percent", PlayerLevelService.format(progress * 100D));
        replacements.put("progress_bar", progressBar(progress));
        replacements.put("enabled", type.enabled());
        replacements.put("auto_upgrade", type.upgrade().autoUpgrade());
        replacements.put("manual_upgrade", manual);
        replacements.put("can_levelup", canLevelUp);
        replacements.put("status", status(type, maxLevel, canLevelUp));
        return replacements;
    }

    private String fallbackItem(LevelTypeConfig type, PlayerLevelEntry entry, Map<String, Object> replacements) {
        if (!type.enabled()) {
            return "barrier";
        }
        if (entry.level() >= type.maxLevel()) {
            return "nether_star";
        }
        if (Boolean.TRUE.equals(replacements.get("can_levelup"))) {
            return "experience_bottle";
        }
        return type.primary() ? "dragon_breath" : "book";
    }

    private String status(LevelTypeConfig type, boolean maxLevel, boolean canLevelUp) {
        if (!type.enabled()) {
            return plugin.messages().message("gui.level.status.locked");
        }
        if (maxLevel) {
            return plugin.messages().message("gui.level.status.max_level");
        }
        if (!type.upgrade().manualUpgrade()) {
            return plugin.messages().message("gui.level.status.auto_only");
        }
        if (canLevelUp) {
            return plugin.messages().message("gui.level.status.ready");
        }
        return plugin.messages().message("gui.level.status.not_enough_exp");
    }

    private String progressBar(double progress) {
        int filled = (int) Math.round(Math.max(0D, Math.min(1D, progress)) * 10D);
        return "<green>" + "|".repeat(filled) + "</green><dark_gray>" + "|".repeat(Math.max(0, 10 - filled)) + "</dark_gray>";
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, String fallbackItem, String fallbackName, List<String> fallbackLore, Map<String, ?> replacements) {
        ItemComponentParser.ItemComponents fallbackComponents = new ItemComponentParser.ItemComponents(
                fallbackName,
                true,
                fallbackLore == null ? List.of() : fallbackLore,
                null,
                null,
                Map.of(),
                List.of()
        );
        ItemComponentParser.ItemComponents components = hasConfiguredComponents(slot) ? slot.components() : fallbackComponents;
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? fallbackItem : slot.item();
        return GuiItemBuilder.build(
                Texts.isBlank(item) ? "barrier" : item,
                components,
                1,
                replacements == null ? Map.of() : replacements,
                (source, amount) -> plugin.coreLib().itemSourceService().createItem(source, amount)
        );
    }

    private boolean hasConfiguredComponents(GuiSlot slot) {
        if (slot == null || slot.components() == null) {
            return false;
        }
        ItemComponentParser.ItemComponents components = slot.components();
        return Texts.isNotBlank(components.displayName())
                || components.displayNameConfig() != null
                || components.loreConfigured()
                || Texts.isNotBlank(components.itemModel())
                || components.customModelData() != null
                || !components.enchantments().isEmpty()
                || !components.hiddenComponents().isEmpty();
    }
}

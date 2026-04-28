package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.gui.SlotParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

final class ConfiguredGuiSupport {

    private final EmakiForgePlugin plugin;

    ConfiguredGuiSupport(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    GuiTemplate template(String guiId, GuiTemplate fallback) {
        GuiTemplate template = plugin.guiTemplateLoader().get(guiId);
        return template == null ? fallback : template;
    }

    Object raw(String guiId, String path) {
        YamlSection configuration = configuration(guiId);
        return configuration == null || Texts.isBlank(path) ? null : configuration.get(path);
    }

    boolean has(String guiId, String path) {
        YamlSection configuration = configuration(guiId);
        return configuration != null && Texts.isNotBlank(path) && configuration.contains(path);
    }

    List<Integer> slots(String guiId, String path, List<Integer> fallback) {
        List<Integer> parsed = SlotParser.parse(raw(guiId, path));
        return parsed.isEmpty() ? fallback : parsed;
    }

    ItemStack build(String guiId,
            String path,
            Map<String, ?> replacements,
            String fallbackItem,
            ItemComponentParser.ItemComponents fallbackComponents) {
        Object raw = raw(guiId, path);
        String item = resolveItem(raw, fallbackItem);
        ItemComponentParser.ItemComponents components = raw == null
                ? (fallbackComponents == null ? ItemComponentParser.empty() : fallbackComponents)
                : ItemComponentParser.parse(raw);
        return GuiItemBuilder.build(item, components, 1, replacements, plugin.itemIdentifierService()::createItem);
    }

    ItemStack apply(String guiId,
            String path,
            ItemStack baseItem,
            Map<String, ?> replacements,
            ItemComponentParser.ItemComponents fallbackComponents) {
        Object raw = raw(guiId, path);
        ItemComponentParser.ItemComponents components = raw == null
                ? (fallbackComponents == null ? ItemComponentParser.empty() : fallbackComponents)
                : ItemComponentParser.parse(raw);
        return GuiItemBuilder.apply(baseItem, components, replacements);
    }

    String text(String guiId, String path, String fallback, Map<String, ?> replacements) {
        Object value = raw(guiId, path);
        if (value == null || Texts.isBlank(value)) {
            value = fallback;
        }
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        return value instanceof String text
                ? Texts.formatTemplate(text, safeReplacements)
                : ExpressionEngine.evaluateStringConfig(value, safeReplacements);
    }

    private YamlSection configuration(String guiId) {
        var entry = plugin.guiTemplateLoader().entry(guiId);
        return entry == null ? null : entry.configuration();
    }

    private String resolveItem(Object raw, String fallbackItem) {
        if (raw == null) {
            return fallbackItem;
        }
        if (raw instanceof String text && Texts.isNotBlank(text)) {
            return text.trim();
        }
        String configuredItem = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(configuredItem)) {
            return configuredItem;
        }
        ItemSource source = ItemSourceUtil.parse(raw);
        if (source != null) {
            String shorthand = ItemSourceUtil.toShorthand(source);
            if (Texts.isNotBlank(shorthand)) {
                return shorthand;
            }
        }
        return fallbackItem;
    }
}

package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.SlotParser;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemParser;
import emaki.jiuwu.craft.corelib.item.LegacyConfiguredItemConverter;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgeRuntimeSnapshot;

final class ConfiguredGuiSupport {

    private final EmakiForgePlugin plugin;
    private final ConfiguredItemParser configuredItemParser = new ConfiguredItemParser();
    private final LegacyConfiguredItemConverter legacyConverter =
            new LegacyConfiguredItemConverter(configuredItemParser);

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

    Object raw(ForgeRuntimeSnapshot runtime, String guiId, String path) {
        YamlSection configuration = configuration(runtime, guiId);
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
            ConfiguredItemDefinition fallbackDefinition) {
        Object raw = raw(guiId, path);
        ConfiguredItemDefinition definition = configuredDefinition(raw, fallbackItem, fallbackDefinition);
        return GuiItemBuilder.build(definition, replacements, plugin.coreLib().configuredItemService());
    }

    ItemStack build(ForgeRuntimeSnapshot runtime,
            String guiId,
            String path,
            Map<String, ?> replacements,
            String fallbackItem,
            ConfiguredItemDefinition fallbackDefinition) {
        Object raw = raw(runtime, guiId, path);
        ConfiguredItemDefinition definition = configuredDefinition(raw, fallbackItem, fallbackDefinition);
        return GuiItemBuilder.build(definition, replacements, plugin.coreLib().configuredItemService());
    }

    ItemStack apply(String guiId,
            String path,
            ItemStack baseItem,
            Map<String, ?> replacements,
            ConfiguredItemDefinition fallbackDefinition) {
        Object raw = raw(guiId, path);
        ConfiguredItemDefinition definition = configuredDefinition(raw, null, fallbackDefinition);
        return GuiItemBuilder.apply(baseItem, definition, replacements, plugin.coreLib().configuredItemService());
    }

    ItemStack apply(ForgeRuntimeSnapshot runtime,
            String guiId,
            String path,
            ItemStack baseItem,
            Map<String, ?> replacements,
            ConfiguredItemDefinition fallbackDefinition) {
        Object raw = raw(runtime, guiId, path);
        ConfiguredItemDefinition definition = configuredDefinition(raw, null, fallbackDefinition);
        return GuiItemBuilder.apply(baseItem, definition, replacements, plugin.coreLib().configuredItemService());
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

    String text(ForgeRuntimeSnapshot runtime,
            String guiId,
            String path,
            String fallback,
            Map<String, ?> replacements) {
        Object value = raw(runtime, guiId, path);
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

    private YamlSection configuration(ForgeRuntimeSnapshot runtime, String guiId) {
        if (runtime == null || runtime.guiTemplateLoader() == null) {
            return null;
        }
        var entry = runtime.guiTemplateLoader().entry(guiId);
        return entry == null ? null : entry.configuration();
    }

    private ConfiguredItemDefinition configuredDefinition(Object raw,
            String fallbackItem,
            ConfiguredItemDefinition fallbackDefinition) {
        Object canonical = ConfigNodes.get(raw, "item");
        if (canonical instanceof Map<?, ?> || canonical instanceof YamlSection) {
            return configuredItemParser.parse(canonical);
        }
        String item = resolveItem(raw, fallbackItem);
        if (raw == null) {
            ConfiguredItemDefinition fallback = fallbackDefinition == null
                    ? new ConfiguredItemDefinition(item, 1, Map.of())
                    : fallbackDefinition;
            return Texts.isBlank(item) ? fallback : fallback.withSource(item);
        }
        return legacyConverter.convert(item, 1, raw, Map.of());
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
        ItemSourceRef source = ItemSourceUtil.parse(raw);
        if (source != null) {
            String shorthand = ItemSourceUtil.toShorthand(source);
            if (Texts.isNotBlank(shorthand)) {
                return shorthand;
            }
        }
        return fallbackItem;
    }
}

package emaki.jiuwu.craft.station.gui;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.gui.SlotParser;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemParser;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;

public final class ConfiguredGuiSupport {

    private static final ConfiguredItemParser PARSER = new ConfiguredItemParser();

    private final Supplier<GuiTemplateLoader> loaderSupplier;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;

    public ConfiguredGuiSupport(Supplier<GuiTemplateLoader> loaderSupplier,
            Supplier<ConfiguredItemService> itemServiceSupplier) {
        this.loaderSupplier = loaderSupplier;
        this.itemServiceSupplier = itemServiceSupplier;
    }

    public Object raw(String layoutId, String path) {
        YamlSection configuration = configuration(layoutId);
        return configuration == null ? null : configuration.get(path);
    }

    public ItemStack build(String layoutId,
            String path,
            Map<String, ?> replacements,
            ConfiguredItemDefinition fallback) {
        ConfiguredItemDefinition definition = definition(layoutId, path);
        return GuiItemBuilder.build(definition == null ? fallback : definition,
                replacements, itemServiceSupplier.get());
    }

    public ItemStack apply(String layoutId,
            String path,
            ItemStack baseItem,
            Map<String, ?> replacements) {
        ConfiguredItemDefinition definition = definition(layoutId, path);
        if (definition == null || baseItem == null) {
            return baseItem;
        }
        return GuiItemBuilder.apply(baseItem, definition, replacements, itemServiceSupplier.get());
    }

    public String text(String layoutId,
            String path,
            String fallback,
            Map<String, ?> replacements) {
        Object value = raw(layoutId, path);
        if (value instanceof String text) {
            return Texts.formatTemplate(text, replacements);
        }
        return Texts.formatTemplate(fallback == null ? "" : fallback, replacements);
    }

    public List<Integer> slots(String layoutId, String path, List<Integer> fallback) {
        Object value = raw(layoutId, path);
        if (value == null) {
            return fallback == null ? List.of() : fallback;
        }
        List<Integer> parsed = SlotParser.parse(value);
        return parsed.isEmpty() && fallback != null ? fallback : parsed;
    }

    private ConfiguredItemDefinition definition(String layoutId, String path) {
        YamlSection configuration = configuration(layoutId);
        YamlSection section = configuration == null ? null : configuration.getSection(path);
        if (section == null) {
            return null;
        }
        YamlSection itemSection = section.getSection("item");
        return PARSER.parse(itemSection == null ? section : itemSection);
    }

    private YamlSection configuration(String layoutId) {
        GuiTemplateLoader loader = loaderSupplier.get();
        if (loader == null || layoutId == null) {
            return null;
        }
        var entry = loader.entry(layoutId);
        return entry == null ? null : entry.configuration();
    }
}

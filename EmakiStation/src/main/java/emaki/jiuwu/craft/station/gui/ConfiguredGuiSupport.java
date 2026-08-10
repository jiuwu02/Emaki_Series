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

/**
 * Reads the {@code virtual_items} and {@code texts} blocks that CoreLib's template parser ignores.
 *
 * <p>{@code GuiTemplateParser} only understands {@code id}, {@code title}, {@code gui_type}, {@code rows},
 * and {@code slots}. Anything else in a layout file survives untouched in the raw section, which is exactly
 * where these two blocks live. Reaching them means going back to
 * {@link GuiTemplateLoader#entry(String)} and reading its configuration.
 *
 * <p>Every lookup falls back rather than failing: a layout that omits an optional virtual item still opens,
 * it just uses the built-in fallback definition.
 */
public final class ConfiguredGuiSupport {

    private static final ConfiguredItemParser PARSER = new ConfiguredItemParser();

    private final Supplier<GuiTemplateLoader> loaderSupplier;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;

    /**
     * Creates the helper.
     *
     * @param loaderSupplier      supplies the current template loader, re-read per call so a reload is
     *                            picked up without re-wiring
     * @param itemServiceSupplier supplies CoreLib's configured-item service
     */
    public ConfiguredGuiSupport(Supplier<GuiTemplateLoader> loaderSupplier,
            Supplier<ConfiguredItemService> itemServiceSupplier) {
        this.loaderSupplier = loaderSupplier;
        this.itemServiceSupplier = itemServiceSupplier;
    }

    /**
     * Reads a raw value from a layout by dotted path.
     *
     * @param layoutId the layout id
     * @param path     the dotted path, for example {@code virtual_items.no_recipe}
     * @return the raw value, or {@code null} when absent
     */
    public Object raw(String layoutId, String path) {
        YamlSection configuration = configuration(layoutId);
        return configuration == null ? null : configuration.get(path);
    }

    /**
     * Builds an item from a layout's {@code virtual_items} block.
     *
     * @param layoutId     the layout id
     * @param path         the dotted path to the virtual item
     * @param replacements placeholder substitutions
     * @param fallback     the definition to use when the layout does not declare one
     * @return the built item; never {@code null}
     */
    public ItemStack build(String layoutId,
            String path,
            Map<String, ?> replacements,
            ConfiguredItemDefinition fallback) {
        ConfiguredItemDefinition definition = definition(layoutId, path);
        return GuiItemBuilder.build(definition == null ? fallback : definition,
                replacements, itemServiceSupplier.get());
    }

    /**
     * Applies a layout's {@code virtual_items} patch on top of an existing item.
     *
     * <p>Used for entries whose base material comes from data rather than configuration, where the layout
     * only supplies presentation.
     *
     * @param layoutId     the layout id
     * @param path         the dotted path to the virtual item
     * @param baseItem     the item to patch
     * @param replacements placeholder substitutions
     * @return the patched item, or {@code baseItem} when the layout declares nothing
     */
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

    /**
     * Reads a string from a layout's {@code texts} block.
     *
     * @param layoutId     the layout id
     * @param path         the dotted path to the text
     * @param fallback     the value to use when the layout does not declare one
     * @param replacements placeholder substitutions
     * @return the resolved text
     */
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

    /**
     * Reads a slot list from a layout by dotted path.
     *
     * @param layoutId the layout id
     * @param path     the dotted path
     * @param fallback the value to use when the layout does not declare one
     * @return the parsed slots
     */
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

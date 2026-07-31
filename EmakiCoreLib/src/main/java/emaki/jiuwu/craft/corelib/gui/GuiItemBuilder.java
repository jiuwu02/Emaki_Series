package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class GuiItemBuilder {

    private GuiItemBuilder() {
    }

    /**
     * Builds a slot item, preferring the template's own configured components and falling back to the
     * caller's name/lore when the template styled nothing.
     *
     * @param slot         the parsed template slot; may be {@code null}
     * @param fallbackItem the item source to use when the slot declares none
     * @param fallbackName the display name to use when the slot configured no components
     * @param fallbackLore the lore to use when the slot configured no components
     * @param replacements          the placeholder replacements applied to the resolved definition
     * @param configuredItemService the service resolving item sources and applying component patches
     * @return the built stack, or a barrier when the definition cannot be resolved
     */
    public static ItemStack build(GuiSlot slot,
            String fallbackItem,
            String fallbackName,
            List<String> fallbackLore,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        ConfiguredItemDefinition definition = definitionFor(slot, fallbackItem, fallbackName, fallbackLore);
        return build(definition, replacements, configuredItemService);
    }

    /**
     * {@return the slot's own definition when it configured components, otherwise a definition built
     * from the supplied fallback name and lore}
     *
     * @param slot         the parsed template slot; may be {@code null}
     * @param fallbackItem the item source to use when the slot declares none
     * @param fallbackName the display name used only when the slot configured no components
     * @param fallbackLore the lore used only when the slot configured no components
     */
    public static ConfiguredItemDefinition definitionFor(GuiSlot slot,
            String fallbackItem,
            String fallbackName,
            List<String> fallbackLore) {
        String item = slot == null || Texts.isBlank(slot.item()) ? fallbackItem : slot.item();
        if (slot != null && slot.hasConfiguredComponents()) {
            return slot.itemDefinition().withSource(Texts.isBlank(item) ? null : item);
        }
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        if (Texts.isNotBlank(fallbackName)) {
            patches.put("minecraft:custom_name", ItemComponentPatch.set(fallbackName));
        }
        if (fallbackLore != null) {
            patches.put("minecraft:lore", ItemComponentPatch.set(List.copyOf(fallbackLore)));
        }
        return new ConfiguredItemDefinition(item, 1, patches);
    }

    public static ItemStack build(ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        int amount = definition == null ? 1 : definition.amount();
        ItemBuildResult result = buildResult(definition, replacements, configuredItemService);
        ItemStack itemStack = result.itemStack();
        return itemStack == null || result.hasErrors() ? barrier(amount) : itemStack;
    }

    public static ItemBuildResult buildResult(ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        return configuredItemService == null
                ? ItemBuildResult.unavailable("Configured item service is unavailable.")
                : configuredItemService.create(definition, replacements);
    }

    public static ItemStack apply(ItemStack baseItem,
            ConfiguredItemDefinition definition,
            Map<String, ?> replacements,
            ConfiguredItemService configuredItemService) {
        ItemBuildResult result = configuredItemService == null
                ? ItemBuildResult.unavailable("Configured item service is unavailable.")
                : configuredItemService.apply(baseItem, definition, replacements);
        ItemStack itemStack = result.itemStack();
        return itemStack == null || result.hasErrors()
                ? barrier(baseItem == null ? 1 : baseItem.getAmount())
                : itemStack;
    }

    private static ItemStack barrier(int amount) {
        return new ItemStack(Material.BARRIER, Math.max(1, amount));
    }
}

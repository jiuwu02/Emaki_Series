package emaki.jiuwu.craft.item.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

public record ItemComponentsConfig(Object customModelData,
        String itemModel,
        String tooltipStyle,
        Map<String, Integer> enchantments,
        List<String> itemFlags,
        boolean hideTooltip,
        boolean unbreakable,
        Boolean enchantmentGlintOverride,
        Integer maxStackSize,
        String rarity,
        Integer damage,
        Integer maxDamage,
        Integer enchantable,
        List<VanillaAttributeModifierConfig> attributeModifiers,
        String raw) {

    public ItemComponentsConfig {
        customModelData = ConfigNodes.toPlainData(customModelData);
        itemModel = itemModel == null ? "" : itemModel;
        tooltipStyle = tooltipStyle == null ? "" : tooltipStyle;
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
        itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
        rarity = rarity == null ? "" : rarity;
        attributeModifiers = attributeModifiers == null ? List.of() : List.copyOf(attributeModifiers);
        raw = raw == null ? "" : raw;
    }

    public static ItemComponentsConfig empty() {
        return new ItemComponentsConfig(null, "", "", Map.of(), List.of(), false, false, null, null, "", null, null, null, List.of(), "");
    }
}

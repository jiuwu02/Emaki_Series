package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class BlueprintRequirement {

    private final String item;
    private final int amount;
    private final ItemSource source;

    public BlueprintRequirement(String item, int amount, ItemSource source) {
        this.item = item;
        this.amount = amount;
        this.source = source;
    }

    public static BlueprintRequirement fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof YamlSection section
                && (section.contains("requirement_mode") || section.contains("blueprint_options"))) {
            return null;
        }
        if (ConfigNodes.get(raw, "requirement_mode") != null || ConfigNodes.get(raw, "blueprint_options") != null) {
            return null;
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isBlank(item)) {
            return null;
        }
        ItemSource source = ItemSourceUtil.parseShorthand(item);
        if (source == null) {
            return null;
        }
        int amount = Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1);
        if (amount <= 0) {
            return null;
        }
        return new BlueprintRequirement(item, amount, source);
    }

    public boolean matches(ItemSource other) {
        return other != null && ItemSourceUtil.matches(source, other);
    }

    public String key() {
        String shorthand = ItemSourceUtil.toShorthand(source);
        return shorthand == null ? "" : Texts.lower(shorthand);
    }

    public String item() {
        return item;
    }

    public int amount() {
        return amount;
    }

    public ItemSource source() {
        return source;
    }
}

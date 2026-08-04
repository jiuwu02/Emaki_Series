package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class BlueprintRequirement {

    private final String item;
    private final int amount;
    private final ItemSourceRef source;

    public BlueprintRequirement(String item, int amount, ItemSourceRef source) {
        this.item = item;
        this.amount = amount;
        this.source = source;
    }

    public static BlueprintRequirement fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(ConfigNodes.get(raw, "item_sources"));
        String item = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(item)) {
            return null;
        }
        int amount = Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1);
        if (amount <= 0) {
            return null;
        }
        return new BlueprintRequirement(item, amount, source);
    }

    public boolean matches(ItemSourceRef other) {
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

    public ItemSourceRef source() {
        return source;
    }
}

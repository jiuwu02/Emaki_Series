package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public final class BlueprintRequirement {

    private final String item;
    private final int amount;
    private final ItemSourceRef source;
    private final Matcher matcher;
    private final String matcherKey;

    public BlueprintRequirement(String item, int amount, ItemSourceRef source) {
        this(item, amount, source, null, "");
    }

    public BlueprintRequirement(String item,
            int amount,
            ItemSourceRef source,
            Matcher matcher,
            String matcherKey) {
        this.item = item;
        this.amount = amount;
        this.source = source;
        this.matcher = matcher;
        this.matcherKey = matcherKey == null ? "" : matcherKey;
    }

    public static BlueprintRequirement fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        ItemSourceRef source = ItemSourceUtil.parse(ConfigNodes.get(raw, "item_sources"));
        String item = ItemSourceUtil.toShorthand(source);
        Object matcherNode = ConfigNodes.get(raw, "matcher");
        Matcher matcher = matcherNode == null ? null : Matcher.fromConfig(matcherNode);
        String matcherKey = matcher == null ? "" : MatcherIdentity.syntheticKey(matcherNode);
        if (Texts.isBlank(item) && Texts.isBlank(matcherKey)) {
            return null;
        }
        int amount = Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1);
        if (amount <= 0) {
            return null;
        }
        return new BlueprintRequirement(item, amount, source, matcher, matcherKey);
    }

    public boolean matches(ItemSourceRef other) {
        return other != null && ItemSourceUtil.matches(source, other);
    }

    public boolean matches(MatchContext context) {
        if (context == null) {
            return false;
        }
        if (matcher != null) {
            return matcher.test(context);
        }
        return matches(context.itemSource());
    }

    public String key() {
        String shorthand = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            return matcherKey;
        }
        return Texts.lower(shorthand);
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

    public Matcher matcher() {
        return matcher;
    }
}

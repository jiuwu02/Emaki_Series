package emaki.jiuwu.craft.forge.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.matcher.MatcherDigest;

public final class BlueprintRequirement {
    private final String item;
    private final int amount;
    private final List<ItemSourceRef> itemSources;
    private final Matcher matcher;
    private final String matcherKey;
    private final String blueprintId;
    private final ItemRequirement requirement;

    public BlueprintRequirement(String item, int amount, ItemSourceRef source) {
        this(item, amount, source == null ? List.of() : List.of(source), null, "", "");
    }

    public BlueprintRequirement(String item, int amount, ItemSourceRef source, Matcher matcher, String matcherKey) {
        this(item, amount, source == null ? List.of() : List.of(source), matcher, matcherKey, "");
    }

    public BlueprintRequirement(String item, int amount, ItemSourceRef source, Matcher matcher, String matcherKey, String blueprintId) {
        this(item, amount, source == null ? List.of() : List.of(source), matcher, matcherKey, blueprintId);
    }

    public BlueprintRequirement(String item, int amount, List<ItemSourceRef> itemSources,
            Matcher matcher, String matcherKey, String blueprintId) {
        this.item = Texts.toStringSafe(item);
        this.amount = amount;
        this.itemSources = List.copyOf(itemSources == null ? List.of() : itemSources);
        this.matcher = matcher;
        this.matcherKey = Texts.toStringSafe(matcherKey);
        this.blueprintId = resolveIdentity(blueprintId, this.itemSources, this.matcherKey);
        this.requirement = new ItemRequirement(this.itemSources, matcher, this.blueprintId);
    }

    private static String resolveIdentity(String declared, List<ItemSourceRef> sources, String matcherKey) {
        if (Texts.isNotBlank(declared)) {
            return ItemRequirement.normalizeIdentity(declared);
        }
        String sourceIdentity = ItemRequirement.sourceIdentity(sources);
        return Texts.isNotBlank(sourceIdentity) ? sourceIdentity : ItemRequirement.normalizeIdentity(matcherKey);
    }

    public static BlueprintRequirement fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        List<ItemSourceRef> sources = ItemRequirement.parseSources(ConfigNodes.get(raw, "item_sources"));
        String item = sources.isEmpty() ? ConfigNodes.string(raw, "item", "") : ItemSourceUtil.toShorthand(sources.get(0));
        Object matcherNode = ConfigNodes.get(raw, "matcher");
        Matcher matcher = matcherNode == null ? null : Matcher.fromConfig(matcherNode);
        String matcherKey = matcher == null ? "" : MatcherDigest.of(matcherNode);
        if (sources.isEmpty() && Texts.isBlank(matcherKey)) {
            return null;
        }
        int amount = Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1);
        if (amount <= 0) {
            return null;
        }
        return new BlueprintRequirement(item, amount, sources, matcher, matcherKey,
                ConfigNodes.string(raw, "id", ConfigNodes.string(raw, "blueprint_id", "")));
    }

    public boolean matches(ItemSourceRef other) {
        return other != null && requirement.matchesSource(other);
    }

    public boolean matches(MatchContext context) {
        return requirement.test(context);
    }

    public ItemRequirement requirement() { return requirement; }
    public String key() { return blueprintId; }
    public String blueprintId() { return blueprintId; }
    public String item() { return item; }
    public int amount() { return amount; }
    public List<ItemSourceRef> itemSources() { return itemSources; }
    public ItemSourceRef source() { return itemSources.isEmpty() ? null : itemSources.get(0); }
    public Matcher matcher() { return matcher; }
    public String matcherKey() { return matcherKey; }
}

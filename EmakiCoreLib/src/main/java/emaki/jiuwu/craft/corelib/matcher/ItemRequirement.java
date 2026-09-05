package emaki.jiuwu.craft.corelib.matcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ItemRequirementSchemaValidator;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

public record ItemRequirement(
        @NotNull List<ItemSourceRef> sources,
        @Nullable Matcher matcher,
        @NotNull String identity,
        @NotNull String canonicalIdentity) {

    public static final String KEY_ITEM_SOURCES = "item_sources";
    public static final String KEY_MATCHER = "matcher";

    private static final List<String> SOURCE_KEYS = List.of(KEY_ITEM_SOURCES, "item_source", "sources", "source");
    private static final List<String> MATCHER_KEYS = List.of(
            KEY_MATCHER, "matchers", "item_matcher", "input_matcher", "material_matcher", "tool_matcher", "container_matcher");
    private static final List<String> IDENTITY_KEYS = List.of("id", "material_id", "requirement_id", "count_key", "slot_id", "audit_id");

    public ItemRequirement {
        sources = sources == null ? List.of() : List.copyOf(sources);
        identity = normalizeIdentity(identity);
        canonicalIdentity = normalizeIdentity(canonicalIdentity);
    }

    public ItemRequirement(@NotNull List<ItemSourceRef> sources,
            @Nullable Matcher matcher,
            @NotNull String identity) {
        this(sources, matcher, identity, identity);
    }

    public static @NotNull ItemRequirement fromConfig(@Nullable Object node) {
        return fromConfig(node, KEY_ITEM_SOURCES, KEY_MATCHER);
    }

    public static @NotNull ItemRequirement fromConfig(@Nullable Object node,
            @NotNull String sourcesKey,
            @NotNull String matcherKey) {
        if (node == null) {
            return new ItemRequirement(List.of(), null, "", "");
        }
        String declared = resolveDeclaredIdentity(node);
        String resolvedSourcesKey = KEY_ITEM_SOURCES.equals(sourcesKey) ? firstDeclaredKey(node, SOURCE_KEYS) : sourcesKey;
        String resolvedMatcherKey = KEY_MATCHER.equals(matcherKey) ? firstDeclaredKey(node, MATCHER_KEYS) : matcherKey;
        java.util.Map<String, Object> validationNode = new java.util.LinkedHashMap<>(ConfigNodes.entries(node));
        if (!KEY_ITEM_SOURCES.equals(resolvedSourcesKey) && ConfigNodes.contains(node, resolvedSourcesKey)) {
            validationNode.remove(resolvedSourcesKey);
            validationNode.put(KEY_ITEM_SOURCES, ConfigNodes.get(node, resolvedSourcesKey));
        }
        if (!KEY_MATCHER.equals(resolvedMatcherKey) && ConfigNodes.contains(node, resolvedMatcherKey)) {
            validationNode.remove(resolvedMatcherKey);
            validationNode.put(KEY_MATCHER, ConfigNodes.get(node, resolvedMatcherKey));
        }
        List<ConfigPrecheckIssue> issues = ItemRequirementSchemaValidator.validate(
                "corelib", "item_requirement", validationNode, ItemRequirementSchemaValidator.Role.INPUT);
        if (ItemRequirementSchemaValidator.blocking(issues)) {
            for (ConfigPrecheckIssue issue : issues) {
                if (issue.severity().blocking()) {
                    ComponentMatcherSupport.LOGGER.warning("Item requirement rejected at load time: "
                            + issue.path() + ": " + issue.message());
                }
            }
            return new ItemRequirement(List.of(), null, declared, declared);
        }
        List<ItemSourceRef> sources = parseSources(ConfigNodes.get(node, resolvedSourcesKey));
        Object matcherNode = ConfigNodes.get(node, resolvedMatcherKey);
        Matcher matcher = matcherNode == null ? null : Matcher.fromConfig(matcherNode);
        String derived = sourceIdentity(sources);
        if (Texts.isBlank(derived)) {
            derived = MatcherDigest.of(matcherNode);
        }
        String identity = Texts.isNotBlank(declared) ? declared : derived;
        return new ItemRequirement(sources, matcher, identity, declared);
    }

    private static @NotNull String firstDeclaredKey(@NotNull Object node, @NotNull List<String> keys) {
        for (String key : keys) {
            if (ConfigNodes.contains(node, key)) {
                return key;
            }
        }
        return keys.getFirst();
    }

    public static @NotNull List<ItemSourceRef> parseSources(@Nullable Object node) {
        List<ItemSourceRef> sources = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(node)) {
            ItemSourceRef ref = ItemSourceUtil.parse(entry);
            if (ref != null && !sources.contains(ref)) {
                sources.add(ref);
            }
        }
        return List.copyOf(sources);
    }

    private static @NotNull String resolveDeclaredIdentity(@NotNull Object node) {
        for (String key : IDENTITY_KEYS) {
            String declared = ConfigNodes.string(node, key, null);
            if (Texts.isNotBlank(declared)) {
                return normalizeIdentity(declared);
            }
        }
        return "";
    }

    public boolean hasCanonicalIdentity() {
        return Texts.isNotBlank(canonicalIdentity);
    }

    public boolean hasDerivedIdentity() {
        return !hasCanonicalIdentity() && Texts.isNotBlank(identity);
    }

    public @NotNull String declaredIdentity() {
        return canonicalIdentity;
    }

    public static @NotNull String normalizeIdentity(@Nullable String raw) {
        return Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
    }

    public static @NotNull String sourceIdentity(@NotNull List<ItemSourceRef> sources) {
        LinkedHashSet<String> shorthands = new LinkedHashSet<>();
        for (ItemSourceRef source : sources) {
            String shorthand = ItemSourceUtil.toShorthand(source);
            if (Texts.isNotBlank(shorthand)) {
                shorthands.add(Texts.lower(shorthand));
            }
        }
        return String.join("+", shorthands);
    }

    public boolean declaresSources() {
        return !sources.isEmpty();
    }

    public boolean declaresMatcher() {
        return matcher != null;
    }

    public boolean empty() {
        return sources.isEmpty() && matcher == null;
    }

    public boolean matchesSource(@Nullable ItemSourceRef candidate) {
        if (sources.isEmpty()) {
            return true;
        }
        for (ItemSourceRef source : sources) {
            if (ItemSourceUtil.matches(source, candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean test(@Nullable MatchContext context) {
        if (context == null || empty()) {
            return false;
        }
        ItemStack item = context.item();
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (!matchesSource(context.itemSource())) {
            return false;
        }
        return matcher == null || matcher.test(context);
    }

    public boolean test(@Nullable ItemStack item, @Nullable ItemSourceRef itemSource, @Nullable Player player) {
        return test(MatchContext.of(item, itemSource, player));
    }
}

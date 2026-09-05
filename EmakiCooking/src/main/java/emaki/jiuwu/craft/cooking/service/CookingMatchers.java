package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

final class CookingMatchers {

    private CookingMatchers() {
    }

    static Matcher parse(YamlSection owner, String path) {
        if (owner == null || path == null || path.isBlank()) {
            return null;
        }
        return fromNode(owner.get(path));
    }

    static Matcher parse(Map<String, Object> owner, String path) {
        if (owner == null || owner.isEmpty() || path == null || path.isBlank()) {
            return null;
        }
        return fromNode(owner.get(path));
    }

    private static Matcher fromNode(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (node instanceof YamlSection section && section.isEmpty()) {
            return null;
        }
        return Matcher.fromConfig(node);
    }

    static ItemRequirement requirement(YamlSection owner, String sourcesPath, String matcherPath) {
        return owner == null
                ? new ItemRequirement(List.of(), null, "")
                : ItemRequirement.fromConfig(owner, sourcesPath, matcherPath);
    }

    static ItemRequirement requirementWithLegacyFallback(YamlSection station,
            String nestedKey,
            String legacySourcesKey,
            String legacyMatcherKey) {
        if (station == null) {
            return new ItemRequirement(List.of(), null, "");
        }
        if (station.contains(nestedKey)) {
            return ItemRequirement.fromConfig(station.get(nestedKey),
                    ItemRequirement.KEY_ITEM_SOURCES, ItemRequirement.KEY_MATCHER);
        }
        Object legacyMatcher = station.get(legacyMatcherKey);
        Map<String, Object> legacyMatcherMap = matcherMap(legacyMatcher);
        if (legacyMatcherMap != null) {
            String type = Texts.lower(String.valueOf(legacyMatcherMap.getOrDefault("type", "")));
            if (type.equals("item_source") || type.equals("item_sources") || type.equals("source") || type.equals("sources")) {
                Map<String, Object> sourceOwner = new java.util.LinkedHashMap<>();
                sourceOwner.put(ItemRequirement.KEY_ITEM_SOURCES, legacyMatcherMap.get("sources"));
                return ItemRequirement.fromConfig(sourceOwner);
            }
        }
        return ItemRequirement.fromConfig(station, legacySourcesKey, legacyMatcherKey);
    }

    private static Map<String, Object> matcherMap(Object value) {
        if (value instanceof YamlSection section) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (String key : List.of("type", "sources")) {
                if (section.contains(key)) {
                    result.put(key, section.get(key));
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (value instanceof Map<?, ?> map) {
            return MapYamlSection.normalizeMap(map);
        }
        return null;
    }

    static ItemRequirement requirement(Map<String, Object> owner, String sourcesPath, String matcherPath) {
        return owner == null || owner.isEmpty()
                ? new ItemRequirement(List.of(), null, "")
                : ItemRequirement.fromConfig(owner, sourcesPath, matcherPath);
    }

    static boolean accepts(ItemRequirement requirement,
            ItemStack itemStack,
            ItemSourceRef itemSource,
            Player player) {
        return requirement != null && requirement.test(itemStack, itemSource, player);
    }

    static boolean test(Matcher matcher, ItemStack itemStack, ItemSourceRef itemSource, Player player) {
        if (matcher == null) {
            return true;
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        return matcher.test(MatchContext.of(itemStack, itemSource, player));
    }

    static boolean accepts(Matcher matcher,
            ItemStack itemStack,
            ItemSourceRef itemSource,
            Player player) {
        return matcher != null && test(matcher, itemStack, itemSource, player);
    }
}

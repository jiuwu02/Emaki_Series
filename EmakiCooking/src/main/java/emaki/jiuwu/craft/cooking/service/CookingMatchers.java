package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
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
                : fromRequirementPaths(owner, sourcesPath, matcherPath);
    }

    static ItemRequirement requirement(Map<String, Object> owner, String sourcesPath, String matcherPath) {
        return owner == null || owner.isEmpty()
                ? new ItemRequirement(List.of(), null, "")
                : fromRequirementPaths(owner, sourcesPath, matcherPath);
    }

    private static ItemRequirement fromRequirementPaths(Object owner, String sourcesPath, String matcherPath) {
        Map<String, Object> node = new LinkedHashMap<>(ConfigNodes.entries(owner));
        putResolved(node, ItemRequirement.KEY_ITEM_SOURCES, ConfigNodes.get(owner, sourcesPath));
        putResolved(node, ItemRequirement.KEY_MATCHER, ConfigNodes.get(owner, matcherPath));
        return ItemRequirement.fromConfig(node);
    }

    private static void putResolved(Map<String, Object> node, String key, Object value) {
        if (value == null) {
            node.remove(key);
        } else {
            node.put(key, value);
        }
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

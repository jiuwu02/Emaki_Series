package emaki.jiuwu.craft.cooking.service;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
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

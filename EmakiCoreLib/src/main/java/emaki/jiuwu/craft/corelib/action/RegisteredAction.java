package emaki.jiuwu.craft.corelib.action;

import org.bukkit.plugin.Plugin;




public record RegisteredAction(
        Action action,
        Plugin owner,
        String ownerKey,
        String source,
        long generation) {
}

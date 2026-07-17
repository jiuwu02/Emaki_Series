package emaki.jiuwu.craft.corelib.action;

import org.bukkit.plugin.Plugin;

/**
 * Immutable registry entry retaining the scheduling owner and generation token.
 */
public record RegisteredAction(
        Action action,
        Plugin owner,
        String ownerKey,
        String source,
        long generation) {
}

package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.NamespacedKey;

/**
 * Abstraction over the mechanism used to register/unregister advancements at runtime.
 * v1.0 ships {@link UnsafeAdvancementPlatform} (the only public Spigot entry point);
 * the interface lets a datapack-based fallback be swapped in without touching callers.
 */
public interface AdvancementPlatform {

    /** {@return a short platform id for logging} */
    String id();

    /**
     * Registers (or re-registers) an advancement from its JSON definition.
     *
     * @param key  the advancement key
     * @param json the advancement JSON
     * @return {@code true} when the advancement is now registered
     */
    boolean register(NamespacedKey key, String json);

    /**
     * Removes a previously registered advancement.
     *
     * @param key the advancement key
     * @return {@code true} when something was removed
     */
    boolean remove(NamespacedKey key);

    /**
     * Removes every advancement currently registered under the given namespace,
     * applying a single data reload afterwards. This sweeps not only the nodes this
     * process registered but also any left over on disk from a previous session, so a
     * subsequent parent-first registration never sees a stale child whose parent was
     * just removed (which the vanilla loader would report as an orphaned advancement).
     *
     * @param namespace the advancement key namespace to purge
     * @return the number of advancements removed
     */
    int removeAll(String namespace);

    /** Applies any pending data reload needed for removals to take effect. */
    void reloadData();
}

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

    /** Applies any pending data reload needed for removals to take effect. */
    void reloadData();
}

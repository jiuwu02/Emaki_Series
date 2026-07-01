package emaki.jiuwu.craft.codex.advancement;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

/**
 * Advancement platform backed by {@code Bukkit.getUnsafe().loadAdvancement(...)}.
 *
 * <p>This is the only public Spigot API for dynamic advancement registration. Notes
 * from the API contract that this implementation handles:
 * <ul>
 *   <li>{@code loadAdvancement} throws {@link IllegalArgumentException} when the key
 *       already exists, so {@link #register} removes an existing entry first.</li>
 *   <li>{@code loadAdvancement} persists to the world's bukkit datapack and triggers a
 *       resource reload, so it takes effect immediately.</li>
 *   <li>{@code removeAdvancement} only deletes the persisted file; a
 *       {@link Bukkit#reloadData()} is needed to drop it from the running instance.</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public final class UnsafeAdvancementPlatform implements AdvancementPlatform {

    private final Logger logger;

    public UnsafeAdvancementPlatform(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return "unsafe";
    }

    @Override
    public boolean register(NamespacedKey key, String json) {
        if (key == null || json == null) {
            return false;
        }
        try {
            if (Bukkit.getAdvancement(key) != null) {
                Bukkit.getUnsafe().removeAdvancement(key);
                Bukkit.reloadData();
            }
            return Bukkit.getUnsafe().loadAdvancement(key, json) != null;
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] Failed to register advancement " + key + ": " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public boolean remove(NamespacedKey key) {
        if (key == null) {
            return false;
        }
        try {
            return Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] Failed to remove advancement " + key + ": " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public void reloadData() {
        try {
            Bukkit.reloadData();
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] reloadData failed: " + throwable.getMessage());
        }
    }
}

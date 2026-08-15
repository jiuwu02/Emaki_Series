package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.NamespacedKey;

public interface AdvancementPlatform {

    String id();

    boolean register(NamespacedKey key, String json);

    boolean remove(NamespacedKey key);

    boolean exists(NamespacedKey key);

    int removeAll(String namespace);

    void reloadData();
}

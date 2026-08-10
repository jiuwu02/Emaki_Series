package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.NamespacedKey;






public interface AdvancementPlatform {


    String id();








    boolean register(NamespacedKey key, String json);







    boolean remove(NamespacedKey key);

    /**
     * {@return whether the server currently exposes this advancement}
     *
     * <p>Asked after {@code reloadData()}: registering a node and the server still having it are two
     * different facts, because a reload rebuilds the tree from disk.</p>
     *
     * @param key the advancement key
     */
    boolean exists(NamespacedKey key);











    int removeAll(String namespace);


    void reloadData();
}

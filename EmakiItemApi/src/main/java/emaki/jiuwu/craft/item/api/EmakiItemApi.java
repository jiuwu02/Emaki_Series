package emaki.jiuwu.craft.item.api;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * Public API for creating and identifying EmakiItem custom items.
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiItem; obtain it
 * through {@link EmakiItemApiProvider#get()}. Lets other plugins look up item
 * definitions, build configured item stacks and identify the definition behind
 * an existing stack.
 */
public interface EmakiItemApi {

    /**
     * {@return whether a definition with the given id is loaded}
     *
     * @param id the item definition id
     */
    boolean exists(String id);

    /**
     * Builds a fresh item stack from a definition.
     *
     * @param id     the item definition id
     * @param amount the desired stack size
     * @return the created item stack, or {@code null} when the id is unknown
     */
    ItemStack create(String id, int amount);

    /**
     * Identifies the EmakiItem definition behind an existing stack.
     *
     * @param itemStack the stack to inspect
     * @return the definition id, or {@code null} when the stack is not an
     *         EmakiItem item
     */
    String identify(ItemStack itemStack);

    /** {@return an immutable view of all loaded item definition ids} */
    Set<String> definitionIds();

    /**
     * {@return the configured display name for a definition, or {@code null}}
     *
     * @param id the item definition id
     */
    String displayName(String id);
}

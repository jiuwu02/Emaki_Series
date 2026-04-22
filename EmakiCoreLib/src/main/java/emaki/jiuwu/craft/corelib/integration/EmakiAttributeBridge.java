package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.entity.Player;

/**
 * Stable bridge contract exposed by EmakiAttribute for optional integrations.
 * <p>
 * This interface intentionally stays narrow so soft-dependent plugins can
 * interact with EA resources and attribute snapshots without depending on
 * EmakiAttribute internal model classes.
 */
public interface EmakiAttributeBridge {

    boolean available();

    double readResourceCurrent(Player player, String resourceId);

    double readResourceMax(Player player, String resourceId);

    boolean consumeResource(Player player, String resourceId, double amount);

    double readAttributeValue(Player player, String attributeId);
}

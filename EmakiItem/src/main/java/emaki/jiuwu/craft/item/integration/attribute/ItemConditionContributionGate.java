package emaki.jiuwu.craft.item.integration.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGate;
import emaki.jiuwu.craft.attribute.api.extension.ItemContributionGateRegistration;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

/**
 * Applies an EmakiItem definition's {@code condition} block to every
 * contribution of that item.
 *
 * <p>Without this gate the condition block only guards trigger actions, so an
 * item whose condition fails still contributes its Lore and PDC attributes and
 * its equipment skills. Evaluation is silent because attribute and skill
 * collection runs per equipment slot on every resync.
 */
public final class ItemConditionContributionGate implements ItemContributionGate {

    /** Gate identifier surfaced in diagnostics and attribute snapshot signatures. */
    public static final String GATE_ID = "emakiitem_condition";

    /** Trigger name exposed to the condition context during gate evaluation. */
    private static final String GATE_TRIGGER = "contribution";

    private final EmakiItemPlugin plugin;

    private ItemConditionContributionGate(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the gate with EmakiAttribute.
     *
     * <p>Invoked reflectively by {@code ItemContributionGateLifecycle} only when
     * EmakiAttribute is enabled, so EmakiItem starts normally without it.
     *
     * @param plugin the owning EmakiItem plugin
     * @return the closeable registration handle
     */
    public static ItemContributionGateRegistration register(EmakiItemPlugin plugin) {
        return EmakiAttributeApi.extensions().registerItemContributionGate(
                plugin,
                new ItemConditionContributionGate(plugin));
    }

    @Override
    public String id() {
        return GATE_ID;
    }

    @Override
    public boolean isActive(Player player, ItemStack itemStack, String slotName) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return true;
        }
        if (plugin == null || plugin.identifier() == null || plugin.itemLoader() == null
                || plugin.conditionChecker() == null) {
            return true;
        }
        String id = plugin.identifier().identify(itemStack);
        if (id.isBlank()) {
            return true;
        }
        EmakiItemDefinition definition = plugin.itemLoader().get(id);
        if (definition == null) {
            return true;
        }
        return plugin.conditionChecker().evaluateSilently(player, definition, GATE_TRIGGER, itemStack);
    }
}

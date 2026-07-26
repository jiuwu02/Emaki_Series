package emaki.jiuwu.craft.attribute.api;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.gate.ItemContributionGate;
import emaki.jiuwu.craft.attribute.api.gate.ItemContributionGateRegistration;

/**
 * Static public API facade for EmakiAttribute's gameplay capabilities: player
 * resources, resolved attribute values, attribute-driven damage and equipment
 * attribute synchronization.
 *
 * <p>This is the canonical entry point. Every method degrades to a documented
 * no-op value when EmakiAttribute is absent, disabled or reloading, so callers
 * never need to guard on plugin presence beyond avoiding class loading.
 *
 * <p>Callers must not cache the {@link Bridge} instance; resolve through these
 * static methods so a reloaded or disabled EmakiAttribute is never called
 * through a stale bridge.
 */
public final class EmakiAttributeApi {

    private static volatile Bridge bridge;

    private EmakiAttributeApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiAttribute's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiAttribute
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiAttributeApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiAttributeApi.bridge == bridge) {
            EmakiAttributeApi.bridge = null;
        }
    }

    /** {@return whether EmakiAttribute has installed its gameplay API bridge} */
    public static boolean available() {
        Bridge resolved = bridge;
        return resolved != null && resolved.available();
    }

    /**
     * Reads a player's current value for a resource.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @return the current value, or {@code -1} when unavailable
     */
    public static double readResourceCurrent(@Nullable Player player, @Nullable String resourceId) {
        Bridge resolved = bridge;
        return resolved == null ? -1D : resolved.readResourceCurrent(player, resourceId);
    }

    /**
     * Reads a player's current maximum for a resource.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @return the current maximum, or {@code -1} when unavailable
     */
    public static double readResourceMax(@Nullable Player player, @Nullable String resourceId) {
        Bridge resolved = bridge;
        return resolved == null ? -1D : resolved.readResourceMax(player, resourceId);
    }

    /**
     * Consumes a resource amount from a player.
     *
     * <p>Fires {@code PlayerResourceConsumeEvent} and honours cancellation and
     * a listener-modified amount.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @param amount the amount to consume
     * @return {@code true} when the resource was consumed
     */
    public static boolean consumeResource(@Nullable Player player, @Nullable String resourceId, double amount) {
        Bridge resolved = bridge;
        return resolved != null && resolved.consumeResource(player, resourceId, amount);
    }

    /**
     * Reads a player's resolved value for an attribute.
     *
     * @param player the owning player
     * @param attributeId the attribute id
     * @return the resolved value, or {@code 0} when unavailable
     */
    public static double readAttributeValue(@Nullable Player player, @Nullable String attributeId) {
        Bridge resolved = bridge;
        return resolved == null ? 0D : resolved.readAttributeValue(player, attributeId);
    }

    /**
     * Requests an equipment attribute resynchronization for a player.
     *
     * @param player the owning player; {@code null} is a no-op
     */
    public static void scheduleEquipmentSync(@Nullable Player player) {
        Bridge resolved = bridge;
        if (resolved != null) {
            resolved.scheduleEquipmentSync(player);
        }
    }

    /**
     * Applies EmakiAttribute damage resolution to a target.
     *
     * @param attacker the attacking entity; may be {@code null}
     * @param target the damaged entity
     * @param damageTypeId the damage type id; blank uses the configured default
     * @param baseDamage the base damage before attribute resolution
     * @param context additional damage context variables; may be {@code null}
     * @return {@code true} when damage was applied
     */
    public static boolean applyDamage(@Nullable LivingEntity attacker,
            @Nullable LivingEntity target,
            @Nullable String damageTypeId,
            double baseDamage,
            @Nullable Map<String, Object> context) {
        Bridge resolved = bridge;
        return resolved != null && resolved.applyDamage(attacker, target, damageTypeId, baseDamage, context);
    }

    /**
     * Registers an item contribution gate.
     *
     * <p>A gate can veto every contribution of one item, dropping its Lore and PDC
     * values together. Callers must close the returned handle on disable or reload.
     *
     * @param plugin the owning plugin
     * @param gate the gate implementation
     * @return a closeable registration handle, or a no-op handle when unavailable
     */
    public static @NotNull ItemContributionGateRegistration registerItemContributionGate(
            @NotNull Plugin plugin,
            @NotNull ItemContributionGate gate) {
        Bridge resolved = bridge;
        return resolved == null
                ? ItemContributionGateRegistration.noop()
                : resolved.registerItemContributionGate(plugin, gate);
    }

    /**
     * Returns whether every registered gate accepts the item for the player.
     *
     * <p>Degrades to {@code true} when EmakiAttribute is absent, so callers never
     * lose functionality by consulting this method.
     *
     * @param player the owning player
     * @param itemStack the equipped item
     * @param slotName the equipment slot name; may be {@code null}
     * @return {@code false} only when a gate actively rejects the item
     */
    public static boolean isItemContributionActive(@Nullable Player player,
            @Nullable ItemStack itemStack,
            @Nullable String slotName) {
        Bridge resolved = bridge;
        return resolved == null || resolved.isItemContributionActive(player, itemStack, slotName);
    }

    /** Internal bridge installed by EmakiAttribute. */
    public interface Bridge {

        /** {@return whether the backing attribute services are usable} */
        boolean available();

        /**
         * Registers an item contribution gate.
         *
         * @param plugin the owning plugin
         * @param gate the gate implementation
         * @return a closeable registration handle
         */
        @NotNull
        ItemContributionGateRegistration registerItemContributionGate(
                @NotNull Plugin plugin,
                @NotNull ItemContributionGate gate);

        /**
         * Returns whether every registered gate accepts the item for the player.
         *
         * @param player the owning player
         * @param itemStack the equipped item
         * @param slotName the equipment slot name; may be {@code null}
         * @return {@code false} only when a gate actively rejects the item
         */
        boolean isItemContributionActive(@Nullable Player player,
                @Nullable ItemStack itemStack,
                @Nullable String slotName);

        /**
         * Reads a player's current value for a resource.
         *
         * @param player the owning player
         * @param resourceId the resource id
         * @return the current value, or {@code -1} when unavailable
         */
        double readResourceCurrent(@Nullable Player player, @Nullable String resourceId);

        /**
         * Reads a player's current maximum for a resource.
         *
         * @param player the owning player
         * @param resourceId the resource id
         * @return the current maximum, or {@code -1} when unavailable
         */
        double readResourceMax(@Nullable Player player, @Nullable String resourceId);

        /**
         * Consumes a resource amount from a player, firing
         * {@code PlayerResourceConsumeEvent}.
         *
         * @param player the owning player
         * @param resourceId the resource id
         * @param amount the amount to consume
         * @return {@code true} when the resource was consumed
         */
        boolean consumeResource(@Nullable Player player, @Nullable String resourceId, double amount);

        /**
         * Reads a player's resolved value for an attribute.
         *
         * @param player the owning player
         * @param attributeId the attribute id
         * @return the resolved value, or {@code 0} when unavailable
         */
        double readAttributeValue(@Nullable Player player, @Nullable String attributeId);

        /**
         * Requests an equipment attribute resynchronization for a player.
         *
         * @param player the owning player; may be {@code null}
         */
        void scheduleEquipmentSync(@Nullable Player player);

        /**
         * Applies EmakiAttribute damage resolution to a target.
         *
         * @param attacker the attacking entity; may be {@code null}
         * @param target the damaged entity
         * @param damageTypeId the damage type id; blank uses the default
         * @param baseDamage the base damage before attribute resolution
         * @param context additional damage context variables; may be {@code null}
         * @return {@code true} when damage was applied
         */
        boolean applyDamage(@Nullable LivingEntity attacker,
                @Nullable LivingEntity target,
                @Nullable String damageTypeId,
                double baseDamage,
                @Nullable Map<String, Object> context);
    }
}

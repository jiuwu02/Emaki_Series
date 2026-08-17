package emaki.jiuwu.craft.attribute.api.extension;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the item held in one equipment slot so it can take part in attribute aggregation.
 *
 * <p>EmakiAttribute ships providers for the vanilla slots (main hand, off hand, and the four armour
 * pieces). An accessory plugin registers its own slots — rings, necklaces, and the like — through
 * {@link AttributeExtensions#registerSlotProvider}, which is how EmakiAttribute aggregates custom
 * slots without depending on any accessory plugin.
 *
 * <p>{@link #id()} doubles as the slot name matched against an item's {@code active_slot} constraint,
 * so pick a name an item's PDC or Lore can plausibly declare. Ids are normalized (trimmed and
 * lower-cased with {@code Locale.ROOT}) and are global rather than per-owner, so reusing an id
 * supersedes the existing provider.
 *
 * <p>Implementations sit on hot equipment-collection paths: they must be cheap, side-effect free, and
 * safe to call for any entity. In particular they must not send messages, run actions, or mutate the
 * item. A provider that throws is treated as contributing nothing, so a broken provider cannot strip
 * an entity's other attributes.
 *
 * <p><strong>Thread:</strong> called on whichever thread collects the snapshot, which for the
 * combat paths is the entity's owner thread. Do not assume the main thread.
 */
public interface AttributeSlotProvider {

    /**
     * {@return the stable slot identifier, also used as the slot name for {@code active_slot} matching
     * and in cache signatures}
     */
    @NotNull
    String id();

    /**
     * Reads the item currently occupying this slot for the given entity.
     *
     * <p>A provider that does not apply to the entity — for example a player-only accessory slot asked
     * about a zombie — must return {@code null} rather than throwing.
     *
     * @param entity the entity whose slot is being read; never {@code null}
     * @return the equipped item, or {@code null} when the slot is empty or not applicable
     */
    @Nullable
    ItemStack readItem(@NotNull LivingEntity entity);
}

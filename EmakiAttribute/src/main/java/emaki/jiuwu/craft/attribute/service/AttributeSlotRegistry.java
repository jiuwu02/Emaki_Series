package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.api.extension.AttributeSlotProvider;
import emaki.jiuwu.craft.attribute.api.extension.SlotProviderRegistration;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;

/**
 * Registry of equipment slots that take part in attribute aggregation.
 *
 * <p>The six vanilla slots are registered as built-ins at construction and are owned by
 * EmakiAttribute itself, so they are never dropped by owner cleanup. Third-party slots arrive through
 * {@code AttributeExtensions.registerSlotProvider} and follow the same owner-scoped lifecycle as
 * item contribution gates.
 */
public final class AttributeSlotRegistry implements Listener, AutoCloseable {

    private final Map<String, RegisteredSlot> slots = new LinkedHashMap<>();
    private final Logger logger;
    private long registrationGeneration;

    public AttributeSlotRegistry(Logger logger) {
        this.logger = logger;
        registerBuiltIns();
    }

    public synchronized SlotProviderRegistration register(Plugin owner, AttributeSlotProvider provider) {
        if (owner == null || provider == null) {
            return SlotProviderRegistration.noop();
        }
        String id = normalize(provider.id());
        if (id.isBlank()) {
            return SlotProviderRegistration.noop();
        }
        long generation = ++registrationGeneration;
        slots.put(id, new RegisteredSlot(owner, provider, generation));
        return new RegistrationHandle(this, id, generation);
    }

    /**
     * Reads the item in each registered slot for the given entity.
     *
     * <p>Iteration order is built-in vanilla slots first in their declared order, then third-party
     * slots sorted by id, so aggregation and cache signatures stay stable across restarts. A slot whose
     * provider throws is skipped rather than aborting the whole collection.
     *
     * @param entity the entity whose slots are read; {@code null} yields an empty result
     * @return an ordered map of slot name to equipped item; slots that are empty or not applicable are
     *         present with a {@code null} value so signature parts stay positionally stable
     */
    /**
     * Reads the item in each registered slot for the given entity.
     *
     * <p>Iteration order is built-in vanilla slots first in their declared order, then third-party
     * slots sorted by id, so aggregation and cache signatures stay stable across restarts. A slot whose
     * provider throws is skipped rather than aborting the whole collection.
     *
     * <p>Slots that read back empty are omitted entirely, so an entity with no equipment at all yields
     * an empty map and equipment collection is skipped rather than contributing empty signature parts.
     *
     * @param entity the entity whose slots are read; {@code null} yields an empty result
     * @return an ordered map of slot name to the non-empty item occupying it
     */
    public Map<String, ItemStack> readSlots(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        Map<String, ItemStack> result = new LinkedHashMap<>();
        for (Map.Entry<String, AttributeSlotProvider> entry : activeSlots().entrySet()) {
            ItemStack itemStack;
            try {
                itemStack = entry.getValue().readItem(entity);
            } catch (RuntimeException exception) {
                warnSlotFailure(entry.getKey(), exception);
                continue;
            }
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            result.put(entry.getKey(), itemStack);
        }
        return result;
    }

    /**
     * {@return the provider registered under the given slot id, or {@code null} when none is registered
     * or its owning plugin is disabled}
     *
     * @param slotId the slot id to look up; normalized before matching
     */
    public AttributeSlotProvider find(String slotId) {
        String id = normalize(slotId);
        return id.isBlank() ? null : activeSlots().get(id);
    }

    /**
     * Removes the third-party slot registered under the given id.
     *
     * <p>Built-in vanilla slots cannot be removed this way; a request naming one is ignored so a caller
     * cannot strip an entity's armour contributions.
     *
     * @param slotId the slot id to remove; normalized before matching
     * @return {@code true} when a third-party slot was actually removed
     */
    public synchronized boolean unregister(String slotId) {
        String id = normalize(slotId);
        RegisteredSlot registered = id.isBlank() ? null : slots.get(id);
        if (registered == null || registered.builtIn()) {
            return false;
        }
        slots.remove(id);
        return true;
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        slots.entrySet().removeIf(entry -> !entry.getValue().builtIn()
                && entry.getValue().owner() == owner);
    }

    /** Removes every third-party slot, keeping the built-in vanilla slots registered. */
    public synchronized void clear() {
        slots.entrySet().removeIf(entry -> !entry.getValue().builtIn());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        clear();
    }

    private synchronized Map<String, AttributeSlotProvider> activeSlots() {
        slots.entrySet().removeIf(entry -> !entry.getValue().builtIn()
                && !entry.getValue().owner().isEnabled());
        List<Map.Entry<String, RegisteredSlot>> thirdParty = new ArrayList<>();
        Map<String, AttributeSlotProvider> result = new LinkedHashMap<>();
        for (Map.Entry<String, RegisteredSlot> entry : slots.entrySet()) {
            if (entry.getValue().builtIn()) {
                result.put(entry.getKey(), entry.getValue().provider());
            } else {
                thirdParty.add(entry);
            }
        }
        thirdParty.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, RegisteredSlot> entry : thirdParty) {
            result.put(entry.getKey(), entry.getValue().provider());
        }
        return result;
    }

    private void registerBuiltIns() {
        addBuiltIn(EquipmentSlotMatcher.SLOT_MAIN_HAND, EntityEquipment::getItemInMainHand);
        addBuiltIn(EquipmentSlotMatcher.SLOT_OFF_HAND, EntityEquipment::getItemInOffHand);
        addBuiltIn(EquipmentSlotMatcher.SLOT_HELMET, EntityEquipment::getHelmet);
        addBuiltIn(EquipmentSlotMatcher.SLOT_CHESTPLATE, EntityEquipment::getChestplate);
        addBuiltIn(EquipmentSlotMatcher.SLOT_LEGGINGS, EntityEquipment::getLeggings);
        addBuiltIn(EquipmentSlotMatcher.SLOT_BOOTS, EntityEquipment::getBoots);
    }

    private void addBuiltIn(String id, EquipmentReader reader) {
        slots.put(id, new RegisteredSlot(null, new VanillaSlotProvider(id, reader), 0L));
    }

    private void warnSlotFailure(String id, RuntimeException exception) {
        if (logger != null) {
            logger.log(Level.WARNING, "Attribute slot provider '" + id
                    + "' failed; skipping that slot.", exception);
        }
    }

    private synchronized void unregister(String id, long generation) {
        RegisteredSlot registered = slots.get(id);
        if (registered == null || registered.builtIn() || registered.generation() != generation) {
            return;
        }
        slots.remove(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Reads one slot from an entity's equipment; separated so the six built-ins stay one line each. */
    @FunctionalInterface
    private interface EquipmentReader {

        ItemStack read(EntityEquipment equipment);
    }

    private record VanillaSlotProvider(String id, EquipmentReader reader) implements AttributeSlotProvider {

        @Override
        public ItemStack readItem(LivingEntity entity) {
            EntityEquipment equipment = entity.getEquipment();
            return equipment == null ? null : reader.read(equipment);
        }
    }

    private record RegisteredSlot(Plugin owner, AttributeSlotProvider provider, long generation) {

        private boolean builtIn() {
            return owner == null;
        }
    }

    private static final class RegistrationHandle implements SlotProviderRegistration {

        private final AttributeSlotRegistry registry;
        private final String id;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(AttributeSlotRegistry registry, String id, long generation) {
            this.registry = registry;
            this.id = id;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(id, generation);
            }
        }
    }
}

package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.api.gate.ItemContributionGate;
import emaki.jiuwu.craft.attribute.api.gate.ItemContributionGateRegistration;

/**
 * Owner-aware registry of {@link ItemContributionGate} providers.
 *
 * <p>A gate may veto every contribution of one item. A provider that throws is
 * treated as accepting the item, so a broken third-party gate can never wipe out
 * all equipment attributes.
 */
public final class ItemContributionGateRegistry implements Listener, AutoCloseable {

    private final Map<String, RegisteredGate> gates = new LinkedHashMap<>();
    private final Logger logger;
    private long registrationGeneration;

    public ItemContributionGateRegistry(Logger logger) {
        this.logger = logger;
    }

    public synchronized ItemContributionGateRegistration register(Plugin owner, ItemContributionGate gate) {
        if (owner == null || gate == null) {
            return ItemContributionGateRegistration.noop();
        }
        String id = normalize(gate.id());
        if (id.isBlank()) {
            return ItemContributionGateRegistration.noop();
        }
        long generation = ++registrationGeneration;
        gates.put(id, new RegisteredGate(owner, gate, generation));
        return new RegistrationHandle(this, id, generation);
    }

    /**
     * Evaluates every registered gate and returns the first rejecting gate id.
     *
     * @param player the owning player
     * @param itemStack the equipped item
     * @param slotName the equipment slot name; may be {@code null}
     * @return the rejecting gate id, or {@code ""} when the item is accepted
     */
    public String rejectingGateId(Player player, ItemStack itemStack, String slotName) {
        for (Map.Entry<String, ItemContributionGate> entry : activeGates().entrySet()) {
            boolean active;
            try {
                active = entry.getValue().isActive(player, itemStack, slotName);
            } catch (RuntimeException exception) {
                warnGateFailure(entry.getKey(), exception);
                continue;
            }
            if (!active) {
                return entry.getKey();
            }
        }
        return "";
    }

    /** {@return whether any gate is currently registered} */
    public synchronized boolean hasGates() {
        gates.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
        return !gates.isEmpty();
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        gates.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }

    public synchronized void clear() {
        gates.clear();
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

    private synchronized Map<String, ItemContributionGate> activeGates() {
        gates.entrySet().removeIf(entry -> !entry.getValue().owner().isEnabled());
        List<Map.Entry<String, RegisteredGate>> ordered = new ArrayList<>(gates.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        Map<String, ItemContributionGate> result = new LinkedHashMap<>();
        for (Map.Entry<String, RegisteredGate> entry : ordered) {
            result.put(entry.getKey(), entry.getValue().gate());
        }
        return result;
    }

    private void warnGateFailure(String id, RuntimeException exception) {
        if (logger != null) {
            logger.log(Level.WARNING, "Item contribution gate '" + id + "' failed; treating item as active.", exception);
        }
    }

    private synchronized void unregister(String id, long generation) {
        RegisteredGate registered = gates.get(id);
        if (registered == null || registered.generation() != generation) {
            return;
        }
        gates.remove(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredGate(Plugin owner, ItemContributionGate gate, long generation) {
    }

    private static final class RegistrationHandle implements ItemContributionGateRegistration {

        private final ItemContributionGateRegistry registry;
        private final String id;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(ItemContributionGateRegistry registry, String id, long generation) {
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

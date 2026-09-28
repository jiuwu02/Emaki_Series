package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.effect.ItemEffectRegistration;
import emaki.jiuwu.craft.item.api.effect.ItemEffectType;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

public final class EmakiItemEffectRegistry implements Listener, AutoCloseable {

    private final Map<String, RegisteredEffectType> types = new LinkedHashMap<>();
    private final Runnable prototypeInvalidator;
    private long registrationGeneration;

    public EmakiItemEffectRegistry() {
        this(null);
    }

    public EmakiItemEffectRegistry(Runnable prototypeInvalidator) {
        this.prototypeInvalidator = prototypeInvalidator;
    }

    public ItemEffectRegistration register(Plugin owner, ItemEffectType type) {
        if (owner == null || type == null) {
            return ItemEffectRegistration.noop();
        }
        String id;
        try {
            id = normalize(type.typeId());
        } catch (RuntimeException | LinkageError exception) {
            return ItemEffectRegistration.noop();
        }
        if (id.isBlank() || EmakiItemDefinitionParser.BUILT_IN_EFFECT_TYPES.contains(id)) {
            return ItemEffectRegistration.noop();
        }
        long generation;
        synchronized (this) {
            generation = ++registrationGeneration;
            types.put(id, new RegisteredEffectType(owner, type, generation));
        }
        invalidatePrototypes();
        return new RegistrationHandle(this, id, type, generation);
    }

    public ItemEffectType find(String typeId) {
        String id = normalize(typeId);
        if (id.isBlank()) {
            return null;
        }
        synchronized (this) {
            RegisteredEffectType registered = types.get(id);
            if (registered == null) {
                return null;
            }
            if (!registered.owner().isEnabled()) {
                types.remove(id);
                return null;
            }
            return registered.type();
        }
    }

    public void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        boolean changed;
        synchronized (this) {
            changed = types.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
        }
        if (changed) {
            invalidatePrototypes();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        boolean changed;
        synchronized (this) {
            changed = !types.isEmpty();
            types.clear();
        }
        if (changed) {
            invalidatePrototypes();
        }
    }

    private void unregister(String id, ItemEffectType type, long generation) {
        boolean changed;
        synchronized (this) {
            RegisteredEffectType registered = types.get(id);
            if (registered == null || registered.generation() != generation || registered.type() != type) {
                return;
            }
            types.remove(id);
            changed = true;
        }
        if (changed) {
            invalidatePrototypes();
        }
    }

    private void invalidatePrototypes() {
        if (prototypeInvalidator != null) {
            prototypeInvalidator.run();
        }
    }

    private static String normalize(String value) {
        return Texts.normalizeId(value);
    }

    private record RegisteredEffectType(Plugin owner, ItemEffectType type, long generation) {
    }

    private static final class RegistrationHandle implements ItemEffectRegistration {

        private final EmakiItemEffectRegistry registry;
        private final String id;
        private final ItemEffectType type;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(EmakiItemEffectRegistry registry, String id, ItemEffectType type, long generation) {
            this.registry = registry;
            this.id = id;
            this.type = type;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(id, type, generation);
            }
        }
    }
}

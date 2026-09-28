package emaki.jiuwu.craft.skills.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.ExternalSkillDefinition;
import emaki.jiuwu.craft.skills.api.SkillDefinitionRegistration;
import emaki.jiuwu.craft.skills.model.SkillDefinition;

public final class ExternalSkillDefinitionRegistry implements Listener, AutoCloseable {

    private final Map<ProviderKey, RegisteredDefinition> registrations = new LinkedHashMap<>();
    private final Runnable changeListener;
    private long registrationGeneration;

    public ExternalSkillDefinitionRegistry(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public SkillDefinitionRegistration register(Plugin owner, ExternalSkillDefinition definition) {
        if (owner == null || !owner.isEnabled() || definition == null) {
            return SkillDefinitionRegistration.noop();
        }
        String id;
        SkillDefinition mapped;
        try {
            id = normalizeId(definition.id());
            if (id.isBlank()) {
                return SkillDefinitionRegistration.noop();
            }
            mapped = ExternalSkillDefinitionMapper.toDefinition(definition, id);
        } catch (RuntimeException | LinkageError exception) {
            return SkillDefinitionRegistration.noop();
        }
        ProviderKey key = new ProviderKey(owner, id);
        long generation;
        synchronized (this) {
            generation = ++registrationGeneration;
            registrations.put(key, new RegisteredDefinition(definition, mapped, generation));
        }
        notifyChanged();
        return new RegistrationHandle(this, key, definition, generation);
    }

    public Map<String, SkillDefinition> overlay(Map<String, SkillDefinition> base) {
        List<RegisteredSkill> snapshot;
        synchronized (this) {
            registrations.keySet().removeIf(key -> !key.owner().isEnabled());
            if (registrations.isEmpty()) {
                return base == null ? Map.of() : base;
            }
            snapshot = registrations.entrySet().stream()
                    .map(entry -> new RegisteredSkill(entry.getKey().id(), entry.getValue().definition()))
                    .toList();
        }
        Map<String, SkillDefinition> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        for (RegisteredSkill registered : snapshot) {
            merged.put(registered.id(), registered.definition());
        }
        return Map.copyOf(merged);
    }

    public void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        boolean changed;
        synchronized (this) {
            changed = registrations.keySet().removeIf(key -> key.owner() == owner);
        }
        if (changed) {
            notifyChanged();
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
            changed = !registrations.isEmpty();
            registrations.clear();
        }
        if (changed) {
            notifyChanged();
        }
    }

    private void unregister(ProviderKey key, ExternalSkillDefinition definition, long generation) {
        boolean changed;
        synchronized (this) {
            RegisteredDefinition registered = registrations.get(key);
            if (registered == null
                    || registered.generation() != generation
                    || registered.source() != definition) {
                return;
            }
            registrations.remove(key);
            changed = true;
        }
        if (changed) {
            notifyChanged();
        }
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private static String normalizeId(String value) {
        return Texts.normalizeId(value);
    }

    private record ProviderKey(Plugin owner, String id) {
    }

    private record RegisteredDefinition(ExternalSkillDefinition source, SkillDefinition definition, long generation) {
    }

    private record RegisteredSkill(String id, SkillDefinition definition) {
    }

    private static final class RegistrationHandle implements SkillDefinitionRegistration {

        private final ExternalSkillDefinitionRegistry registry;
        private final ProviderKey key;
        private final ExternalSkillDefinition definition;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(ExternalSkillDefinitionRegistry registry,
                ProviderKey key,
                ExternalSkillDefinition definition,
                long generation) {
            this.registry = registry;
            this.key = key;
            this.definition = definition;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registry.unregister(key, definition, generation);
            }
        }
    }
}

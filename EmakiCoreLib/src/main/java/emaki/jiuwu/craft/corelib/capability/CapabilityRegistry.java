package emaki.jiuwu.craft.corelib.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CapabilityRegistry {

    private final AtomicLong generationSequence = new AtomicLong();
    private final Object writeLock = new Object();

    private volatile Map<String, Entry> entries = Map.of();

    public @NotNull CapabilityRegistration publish(@Nullable Plugin owner,
            @Nullable Set<ApiCapability> capabilities) {
        if (owner == null) {
            return CapabilityRegistration.unavailable("capability.publish.missing_owner");
        }
        if (capabilities == null || capabilities.isEmpty()) {
            return CapabilityRegistration.unavailable("capability.publish.empty");
        }
        Set<ApiCapability> requested = new LinkedHashSet<>();
        for (ApiCapability capability : capabilities) {
            if (capability != null) {
                requested.add(capability);
            }
        }
        if (requested.isEmpty()) {
            return CapabilityRegistration.unavailable("capability.publish.empty");
        }
        synchronized (writeLock) {
            for (ApiCapability capability : requested) {
                Entry existing = entries.get(capability.key());
                if (existing != null) {
                    return CapabilityRegistration.unavailable(
                            Texts.isBlank(existing.ownerName())
                                    ? "capability.publish.duplicate"
                                    : "capability.publish.duplicate_owned_by:" + existing.ownerName()
                                            + ":" + capability.key());
                }
            }
            long generation = generationSequence.incrementAndGet();
            Map<String, Entry> copy = new LinkedHashMap<>(entries);
            for (ApiCapability capability : requested) {
                copy.put(capability.key(), new Entry(capability, owner, owner.getName(), generation));
            }
            entries = Map.copyOf(copy);
            return new Handle(this, Set.copyOf(requested), generation);
        }
    }

    public int revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        synchronized (writeLock) {
            Map<String, Entry> copy = new LinkedHashMap<>(entries);
            int removed = 0;
            for (Map.Entry<String, Entry> entry : List.copyOf(copy.entrySet())) {
                if (entry.getValue().owner() == owner) {
                    copy.remove(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) {
                entries = Map.copyOf(copy);
            }
            return removed;
        }
    }

    public boolean has(@Nullable ApiCapability capability) {
        if (capability == null) {
            return false;
        }
        Entry entry = entries.get(capability.key());
        return entry != null && entry.live();
    }

    public @NotNull Set<ApiCapability> all() {
        Set<ApiCapability> result = new LinkedHashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.live()) {
                result.add(entry.capability());
            }
        }
        return Set.copyOf(result);
    }

    public @NotNull Set<ApiCapability> ownedBy(@Nullable String pluginName) {
        if (Texts.isBlank(pluginName)) {
            return Set.of();
        }
        String needle = Texts.lower(pluginName);
        Set<ApiCapability> result = new LinkedHashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.live() && Texts.lower(entry.ownerName()).equals(needle)) {
                result.add(entry.capability());
            }
        }
        return Set.copyOf(result);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        synchronized (writeLock) {
            entries = Map.of();
        }
    }

    private void revokeGeneration(Set<ApiCapability> published, long generation) {
        synchronized (writeLock) {
            Map<String, Entry> copy = new LinkedHashMap<>(entries);
            List<String> removable = new ArrayList<>();
            for (ApiCapability capability : published) {
                Entry entry = copy.get(capability.key());
                if (entry != null && entry.generation() == generation) {
                    removable.add(capability.key());
                }
            }
            if (removable.isEmpty()) {
                return;
            }
            removable.forEach(copy::remove);
            entries = Map.copyOf(copy);
        }
    }

    private record Entry(ApiCapability capability, Plugin owner, String ownerName, long generation) {

        private boolean live() {
            return owner == null || owner.isEnabled();
        }
    }

    private static final class Handle implements CapabilityRegistration {

        private final CapabilityRegistry registry;
        private final Set<ApiCapability> published;
        private final long generation;

        private volatile boolean active = true;

        private Handle(CapabilityRegistry registry, Set<ApiCapability> published, long generation) {
            this.registry = registry;
            this.published = published;
            this.generation = generation;
        }

        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public @NotNull Set<ApiCapability> published() {
            return published;
        }

        @Override
        public @NotNull String reasonKey() {
            return "";
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            registry.revokeGeneration(published, generation);
        }
    }
}

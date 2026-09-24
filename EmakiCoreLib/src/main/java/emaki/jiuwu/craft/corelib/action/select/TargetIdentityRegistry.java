package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentityProvider;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.integration.IntegrationFailures;

public final class TargetIdentityRegistry {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
    private final Consumer<String> failureReporter;

    public TargetIdentityRegistry() {
        this(null);
    }

    public TargetIdentityRegistry(@Nullable Consumer<String> failureReporter) {
        this.failureReporter = failureReporter;
    }

    public @NotNull CoreTargetRegistration register(@Nullable Plugin owner,
            @Nullable CoreTargetIdentityProvider provider) {
        if (provider == null || Texts.isBlank(provider.systemId())) {
            return CoreTargetRegistration.unavailable("", "action.register.blank_id");
        }
        String systemId = Texts.lower(provider.systemId());
        Entry entry = new Entry(systemId, owner, provider);
        Entry existing = entries.putIfAbsent(systemId, entry);
        if (existing != null) {
            String ownerName = existing.ownerName();
            return CoreTargetRegistration.unavailable(systemId, Texts.isBlank(ownerName)
                    ? "action.register.duplicate_id"
                    : "action.register.duplicate_id_owned_by:" + ownerName);
        }
        return new Handle(systemId, entry);
    }

    public @NotNull Set<String> systemIds() {
        return Set.copyOf(entries.keySet());
    }

    public @NotNull Map<String, CoreTargetIdentity> identifyAll(@Nullable LivingEntity entity) {
        if (entity == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<String, CoreTargetIdentity> identified = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            CoreTargetIdentity identity = identify(entry, entity);
            if (identity != null && identity.present()) {
                identified.put(entry.systemId(), identity);
            }
        }
        return identified;
    }

    public int revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (entry.getValue().owner() == owner && entries.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    public void clear() {
        entries.clear();
        reportedFailures.clear();
    }

    private @Nullable CoreTargetIdentity identify(Entry entry, LivingEntity entity) {
        try {
            return entry.provider().identify(entity);
        } catch (RuntimeException | LinkageError exception) {
            if (failureReporter != null && reportedFailures.add(entry.systemId())) {
                failureReporter.accept("Target identity provider '" + entry.systemId() + "' failed: "
                        + IntegrationFailures.detail(exception));
            }
            return null;
        }
    }

    private static final class Entry {

        private final String systemId;
        private final Plugin owner;
        private final CoreTargetIdentityProvider provider;

        private Entry(String systemId, Plugin owner, CoreTargetIdentityProvider provider) {
            this.systemId = systemId;
            this.owner = owner;
            this.provider = provider;
        }

        private String systemId() {
            return systemId;
        }

        private Plugin owner() {
            return owner;
        }

        private String ownerName() {
            return owner == null ? "" : owner.getName();
        }

        private CoreTargetIdentityProvider provider() {
            return provider;
        }
    }

    private final class Handle implements CoreTargetRegistration {

        private final String systemId;
        private final Entry entry;
        private volatile boolean active = true;

        private Handle(String systemId, Entry entry) {
            this.systemId = systemId;
            this.entry = entry;
        }

        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public @NotNull String targetId() {
            return systemId;
        }

        @Override
        public @NotNull String reasonKey() {
            return "";
        }

        @Override
        public boolean active() {
            return active && entries.get(systemId) == entry;
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                entries.remove(systemId, entry);
            }
        }
    }
}
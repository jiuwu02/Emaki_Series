package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class TriggerRegistry {

    public static final String REASON_BLANK_ID = "action.trigger.register.blank_id";

    public static final String REASON_MISSING_NAMESPACE = "action.trigger.register.missing_namespace";

    public static final String REASON_INVALID_CONTRACT = "action.trigger.register.invalid_contract";

    public static final String REASON_DUPLICATE_OWNED_BY = "action.trigger.register.duplicate_id_owned_by:";

    public static final String REASON_DUPLICATE = "action.trigger.register.duplicate_id";

    private final AtomicLong generationSequence = new AtomicLong();
    private final Object writeLock = new Object();

    private volatile Map<String, RegisteredTrigger> entries = Map.of();

    public @NotNull CoreTriggerRegistration register(@Nullable Plugin owner,
            @Nullable CoreActionTrigger trigger) {
        if (trigger == null) {
            return CoreTriggerRegistration.unavailable(REASON_BLANK_ID);
        }
        String id;
        TriggerContract contract;
        try {
            id = trigger.id();
            contract = trigger.contract();
        } catch (RuntimeException exception) {
            return CoreTriggerRegistration.unavailable(REASON_INVALID_CONTRACT);
        }
        if (Texts.isBlank(id)) {
            return CoreTriggerRegistration.unavailable(REASON_BLANK_ID);
        }
        if (!hasNamespace(id)) {
            return CoreTriggerRegistration.unavailable(REASON_MISSING_NAMESPACE);
        }
        if (contract == null) {
            return CoreTriggerRegistration.unavailable(REASON_INVALID_CONTRACT);
        }
        String key = Texts.lower(Texts.trim(id));
        synchronized (writeLock) {
            RegisteredTrigger existing = entries.get(key);
            if (existing != null) {
                String firstOwner = existing.ownerName();
                return CoreTriggerRegistration.unavailable(Texts.isBlank(firstOwner)
                        ? REASON_DUPLICATE
                        : REASON_DUPLICATE_OWNED_BY + firstOwner);
            }
            RegisteredTrigger entry = new RegisteredTrigger(key, trigger, contract, owner,
                    owner == null ? "" : owner.getName(), generationSequence.incrementAndGet());
            Map<String, RegisteredTrigger> copy = new LinkedHashMap<>(entries);
            copy.put(key, entry);
            entries = Map.copyOf(copy);
            return new Handle(this, key, entry.generation());
        }
    }

    public @Nullable TriggerContract contractOf(@Nullable String triggerId) {
        RegisteredTrigger entry = live(triggerId);
        return entry == null ? null : entry.contract();
    }

    public @Nullable RegisteredTrigger lookup(@Nullable String triggerId) {
        return live(triggerId);
    }

    public @NotNull String ownerNameOf(@Nullable String triggerId) {
        RegisteredTrigger entry = entries.get(Texts.lower(Texts.trim(Texts.toStringSafe(triggerId))));
        return entry == null ? "" : entry.ownerName();
    }

    public boolean active(@Nullable String triggerId, long generation) {
        String key = Texts.lower(Texts.trim(Texts.toStringSafe(triggerId)));
        RegisteredTrigger entry = entries.get(key);
        return entry != null && entry.generation() == generation;
    }

    public boolean revoke(@Nullable String triggerId, long generation) {
        String key = Texts.lower(Texts.trim(Texts.toStringSafe(triggerId)));
        synchronized (writeLock) {
            RegisteredTrigger entry = entries.get(key);
            if (entry == null || entry.generation() != generation) {
                return false;
            }
            Map<String, RegisteredTrigger> copy = new LinkedHashMap<>(entries);
            copy.remove(key);
            entries = Map.copyOf(copy);
            return true;
        }
    }

    public int revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        synchronized (writeLock) {
            Map<String, RegisteredTrigger> copy = new LinkedHashMap<>(entries);
            int removed = 0;
            for (Map.Entry<String, RegisteredTrigger> entry : List.copyOf(copy.entrySet())) {
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

    public @NotNull List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    public @NotNull List<RegisteredTrigger> all() {
        return List.copyOf(entries.values());
    }

    public @NotNull List<RegisteredTrigger> byOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return List.of();
        }
        List<RegisteredTrigger> result = new ArrayList<>();
        for (RegisteredTrigger entry : entries.values()) {
            if (entry.owner() == owner) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        synchronized (writeLock) {
            entries = Map.of();
        }
    }

    public static boolean hasNamespace(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return false;
        }
        String trimmed = Texts.trim(id);
        int colon = trimmed.indexOf(':');
        return colon > 0 && colon < trimmed.length() - 1;
    }

    private RegisteredTrigger live(@Nullable String triggerId) {
        RegisteredTrigger entry = entries.get(Texts.lower(Texts.trim(Texts.toStringSafe(triggerId))));
        if (entry == null) {
            return null;
        }
        return entry.owner() == null || entry.owner().isEnabled() ? entry : null;
    }

    private static final class Handle implements CoreTriggerRegistration {

        private final TriggerRegistry registry;
        private final String triggerId;
        private final long generation;

        private volatile boolean active = true;

        private Handle(TriggerRegistry registry, String triggerId, long generation) {
            this.registry = registry;
            this.triggerId = triggerId;
            this.generation = generation;
        }

        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public @NotNull String triggerId() {
            return triggerId;
        }

        @Override
        public @NotNull String reasonKey() {
            return "";
        }

        @Override
        public boolean active() {
            return active && registry.active(triggerId, generation);
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            registry.revoke(triggerId, generation);
        }
    }
}

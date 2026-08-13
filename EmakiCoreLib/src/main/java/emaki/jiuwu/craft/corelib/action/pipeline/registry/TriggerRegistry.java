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

/**
 * The one place every pipeline trigger contract is registered.
 *
 * <p>Deliberately built on the same four rules {@link StageRegistry} enforces, because a trigger has
 * the same ownership problem a stage does:</p>
 * <ul>
 *   <li>a duplicate id is a hard failure naming the first owner, never a silent overwrite;</li>
 *   <li>registration returns a revocable handle whose {@code close()} is idempotent;</li>
 *   <li>every trigger owned by one plugin can be revoked in one call when that plugin is disabled;</li>
 *   <li>the contract is validated at registration time rather than at first dispatch.</li>
 * </ul>
 *
 * <p>Unlike {@link StageRegistry} this registry additionally requires a namespace prefix. Stage ids
 * predate third-party registration and could not be tightened retroactively, but trigger ids are new
 * and public from the first release, so a bare {@code forge_success} is rejected before two plugins can
 * ever race for it.</p>
 *
 * <p>Reads go through a {@code volatile} immutable map, so dispatch-path lookup takes no lock and does
 * no per-server scan. Writes happen only on enable / reload and copy the whole map.</p>
 */
public final class TriggerRegistry {

    /** Stable reason key: the id was blank. */
    public static final String REASON_BLANK_ID = "action.trigger.register.blank_id";

    /** Stable reason key: the id carried no {@code namespace:} prefix. */
    public static final String REASON_MISSING_NAMESPACE = "action.trigger.register.missing_namespace";

    /** Stable reason key: {@code contract()} returned {@code null} or threw. */
    public static final String REASON_INVALID_CONTRACT = "action.trigger.register.invalid_contract";

    /** Stable reason key prefix: the id is already owned. The first owner name is appended. */
    public static final String REASON_DUPLICATE_OWNED_BY = "action.trigger.register.duplicate_id_owned_by:";

    /** Stable reason key: the id is already registered but its first owner is unknown. */
    public static final String REASON_DUPLICATE = "action.trigger.register.duplicate_id";

    private final AtomicLong generationSequence = new AtomicLong();
    private final Object writeLock = new Object();

    private volatile Map<String, RegisteredTrigger> entries = Map.of();

    /**
     * Registers a trigger contract.
     *
     * @param owner owning plugin
     * @param trigger the trigger declaration
     * @return a revocable handle; an inactive handle carrying a stable reason key on rejection
     */
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

    /**
     * Looks up the contract for one trigger id.
     *
     * <p>This is the compile-path entry point. An unknown id resolves to {@code null} so the caller can
     * keep its existing permissive fallback rather than having a permissive contract fabricated here —
     * the distinction between "no such trigger" and "a trigger that declared nothing" matters to
     * diagnostics.</p>
     *
     * @param triggerId trigger id
     * @return the declared contract, or {@code null} when no live trigger holds that id
     */
    public @Nullable TriggerContract contractOf(@Nullable String triggerId) {
        RegisteredTrigger entry = live(triggerId);
        return entry == null ? null : entry.contract();
    }

    /**
     * Looks up a live trigger.
     *
     * @param triggerId trigger id
     * @return the entry, or {@code null} when unknown or its owner is disabled
     */
    public @Nullable RegisteredTrigger lookup(@Nullable String triggerId) {
        return live(triggerId);
    }

    /**
     * Reads the owner name of a registered id.
     *
     * @param triggerId trigger id
     * @return the owner name, or an empty string when the id is unknown
     */
    public @NotNull String ownerNameOf(@Nullable String triggerId) {
        RegisteredTrigger entry = entries.get(Texts.lower(Texts.trim(Texts.toStringSafe(triggerId))));
        return entry == null ? "" : entry.ownerName();
    }

    /**
     * Revokes one entry when its generation still matches.
     *
     * @param triggerId trigger id
     * @param generation the generation captured at registration time
     * @return whether the entry was removed
     */
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

    /**
     * Revokes every trigger owned by {@code owner}.
     *
     * @param owner the owning plugin
     * @return how many entries were removed
     */
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

    /** {@return every live trigger id, in registration order} */
    public @NotNull List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    /** {@return every live entry} */
    public @NotNull List<RegisteredTrigger> all() {
        return List.copyOf(entries.values());
    }

    /**
     * Lists live entries owned by {@code owner}.
     *
     * @param owner the owning plugin
     * @return matching entries
     */
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

    /** {@return how many live entries this registry holds} */
    public int size() {
        return entries.size();
    }

    /** Clears every entry. Used when CoreLib itself shuts down. */
    public void clear() {
        synchronized (writeLock) {
            entries = Map.of();
        }
    }

    /**
     * Tests whether an id carries a usable {@code namespace:name} prefix.
     *
     * @param id the candidate id
     * @return whether both sides of the colon are non-empty
     */
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
            return active;
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

package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class StageTable {

    private final CoreStageKind kind;
    private final AtomicLong generationSequence = new AtomicLong();
    private final Object writeLock = new Object();

    private volatile Map<String, RegisteredStage> entries = Map.of();
    private volatile Map<String, String> tombstones = Map.of();

    public StageTable(@NotNull CoreStageKind kind) {
        this.kind = kind == null ? CoreStageKind.ACTION : kind;
    }

    public @NotNull CoreStageKind kind() {
        return kind;
    }

    public @Nullable RegisteredStage register(@Nullable String id, @Nullable Object stage, @Nullable Plugin owner) {
        if (Texts.isBlank(id) || stage == null) {
            return null;
        }
        String key = Texts.lower(id);
        synchronized (writeLock) {
            if (entries.containsKey(key)) {
                return null;
            }
            RegisteredStage entry = new RegisteredStage(key, kind, stage, owner,
                    owner == null ? "" : owner.getName(), generationSequence.incrementAndGet());
            Map<String, RegisteredStage> copy = new LinkedHashMap<>(entries);
            copy.put(key, entry);
            entries = Map.copyOf(copy);
            if (tombstones.containsKey(key)) {
                Map<String, String> tombCopy = new LinkedHashMap<>(tombstones);
                tombCopy.remove(key);
                tombstones = Map.copyOf(tombCopy);
            }
            return entry;
        }
    }

    public @NotNull StageLookup lookup(@Nullable String id) {
        String key = Texts.lower(id);
        RegisteredStage entry = entries.get(key);
        if (entry != null) {
            return entry.ownerEnabled() || entry.owner() == null
                    ? new StageLookup.Found(entry)
                    : new StageLookup.OwnerDisabled(key, kind, entry.ownerName());
        }
        String formerOwner = tombstones.get(key);
        if (formerOwner != null) {
            return new StageLookup.OwnerDisabled(key, kind, formerOwner);
        }
        return new StageLookup.Unknown(key, kind);
    }

    public @NotNull String ownerNameOf(@Nullable String id) {
        String key = Texts.lower(id);
        RegisteredStage entry = entries.get(key);
        if (entry != null) {
            return entry.ownerName();
        }
        return Texts.toStringSafe(tombstones.get(key));
    }

    public boolean revoke(@Nullable String id, long generation) {
        String key = Texts.lower(id);
        synchronized (writeLock) {
            RegisteredStage entry = entries.get(key);
            if (entry == null || entry.generation() != generation) {
                return false;
            }
            Map<String, RegisteredStage> copy = new LinkedHashMap<>(entries);
            copy.remove(key);
            entries = Map.copyOf(copy);
            addTombstone(key, entry.ownerName());
            return true;
        }
    }

    public int revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        synchronized (writeLock) {
            Map<String, RegisteredStage> copy = new LinkedHashMap<>(entries);
            Map<String, String> tombCopy = new LinkedHashMap<>(tombstones);
            int removed = 0;
            for (Map.Entry<String, RegisteredStage> entry : List.copyOf(copy.entrySet())) {
                if (entry.getValue().owner() == owner) {
                    copy.remove(entry.getKey());
                    tombCopy.put(entry.getKey(), entry.getValue().ownerName());
                    removed++;
                }
            }
            if (removed > 0) {
                entries = Map.copyOf(copy);
                tombstones = Map.copyOf(tombCopy);
            }
            return removed;
        }
    }

    public @NotNull List<String> ids() {
        return List.copyOf(entries.keySet());
    }

    public @NotNull List<RegisteredStage> all() {
        return List.copyOf(entries.values());
    }

    public @NotNull List<RegisteredStage> byOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return List.of();
        }
        List<RegisteredStage> result = new ArrayList<>();
        for (RegisteredStage entry : entries.values()) {
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
            tombstones = Map.of();
        }
    }

    private void addTombstone(String key, String ownerName) {
        if (Texts.isBlank(ownerName)) {
            return;
        }
        Map<String, String> copy = new LinkedHashMap<>(tombstones);
        copy.put(key, ownerName);
        tombstones = Map.copyOf(copy);
    }
}

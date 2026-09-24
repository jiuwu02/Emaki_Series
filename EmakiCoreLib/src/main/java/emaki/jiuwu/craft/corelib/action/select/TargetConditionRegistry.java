package emaki.jiuwu.craft.corelib.action.select;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class TargetConditionRegistry {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public @NotNull CoreTargetRegistration register(@Nullable Plugin owner, @Nullable CoreTargetCondition condition) {
        if (condition == null || Texts.isBlank(condition.id())) {
            return CoreTargetRegistration.unavailable("", "action.register.blank_id");
        }
        String id = Texts.lower(condition.id());
        if (BuiltinTargetConditions.contains(id)) {
            return CoreTargetRegistration.unavailable(id, "action.register.condition_reserved");
        }
        Entry entry = new Entry(id, owner, condition);
        Entry existing = entries.putIfAbsent(id, entry);
        if (existing != null) {
            String ownerName = existing.ownerName();
            return CoreTargetRegistration.unavailable(id, Texts.isBlank(ownerName)
                    ? "action.register.duplicate_id"
                    : "action.register.duplicate_id_owned_by:" + ownerName);
        }
        return new Handle(id, entry);
    }

    public @Nullable CoreTargetCondition find(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        Entry entry = entries.get(Texts.lower(id));
        return entry == null ? null : entry.condition();
    }

    public @NotNull List<String> ids() {
        return entries.keySet().stream().sorted().toList();
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
    }

    private static final class Entry {

        private final String id;
        private final Plugin owner;
        private final CoreTargetCondition condition;

        private Entry(String id, Plugin owner, CoreTargetCondition condition) {
            this.id = id;
            this.owner = owner;
            this.condition = condition;
        }

        private Plugin owner() {
            return owner;
        }

        private String ownerName() {
            return owner == null ? "" : owner.getName();
        }

        private CoreTargetCondition condition() {
            return condition;
        }
    }

    private final class Handle implements CoreTargetRegistration {

        private final String id;
        private final Entry entry;
        private volatile boolean active = true;

        private Handle(String id, Entry entry) {
            this.id = id;
            this.entry = entry;
        }

        @Override
        public boolean successful() {
            return true;
        }

        @Override
        public @NotNull String targetId() {
            return id;
        }

        @Override
        public @NotNull String reasonKey() {
            return "";
        }

        @Override
        public boolean active() {
            return active && entries.get(id) == entry;
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                entries.remove(id, entry);
            }
        }
    }
}
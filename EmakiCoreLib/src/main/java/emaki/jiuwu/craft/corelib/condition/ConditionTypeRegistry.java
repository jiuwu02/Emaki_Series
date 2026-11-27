package emaki.jiuwu.craft.corelib.condition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.condition.CoreCondition;
import emaki.jiuwu.craft.corelib.api.condition.CoreConditionOutcome;
import emaki.jiuwu.craft.corelib.api.condition.CoreConditionRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ConditionTypeRegistry {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public @NotNull CoreConditionRegistration register(@Nullable Plugin owner, @Nullable CoreCondition condition) {
        if (condition == null || Texts.isBlank(condition.id())) {
            return CoreConditionRegistration.unavailable("", "condition.register.blank_id");
        }
        String id = Texts.lower(condition.id());
        Entry entry = new Entry(id, owner, condition);
        Entry existing = entries.putIfAbsent(id, entry);
        if (existing != null) {
            String ownerName = existing.ownerName();
            return CoreConditionRegistration.unavailable(id, Texts.isBlank(ownerName)
                    ? "condition.register.duplicate_id"
                    : "condition.register.duplicate_id_owned_by:" + ownerName);
        }
        return new Handle(id, entry);
    }

    public @Nullable CoreCondition find(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        Entry entry = entries.get(Texts.lower(id));
        return entry == null ? null : entry.condition();
    }

    public @NotNull CoreConditionOutcome test(@Nullable String id,
            @NotNull Map<String, Object> fields,
            @Nullable ConditionContext context) {
        CoreCondition condition = find(id);
        if (condition == null) {
            return CoreConditionOutcome.UNKNOWN;
        }
        try {
            return condition.test(fields, context == null ? ConditionContext.EMPTY : context);
        } catch (RuntimeException _) {
            return CoreConditionOutcome.UNKNOWN;
        }
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

    private record Entry(String id, Plugin owner, CoreCondition condition) {

        private String ownerName() {
            return owner == null ? "" : owner.getName();
        }
    }

    private final class Handle implements CoreConditionRegistration {

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
            active = false;
            entries.remove(id, entry);
        }
    }
}

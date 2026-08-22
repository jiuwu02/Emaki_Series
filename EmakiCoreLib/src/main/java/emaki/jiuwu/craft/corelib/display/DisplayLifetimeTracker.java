package emaki.jiuwu.craft.corelib.display;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class DisplayLifetimeTracker {

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final BiConsumer<String, String> expiryAction;
    private final Map<String, Set<String>> displaysByGroup = new ConcurrentHashMap<>();
    private final Map<String, TaskToken> expiryTasks = new ConcurrentHashMap<>();

    public DisplayLifetimeTracker(Plugin plugin,
            ExecutionDispatcher executionDispatcher,
            BiConsumer<String, String> expiryAction) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.expiryAction = expiryAction;
    }

    public void trackGroupMember(String groupKey, String runtimeKey) {
        displaysByGroup.computeIfAbsent(groupKey, ignored -> ConcurrentHashMap.newKeySet()).add(runtimeKey);
    }

    public void scheduleExpiry(DisplayLifetimeSpec spec) {
        String key = spec.runtimeKey();
        cancelQuietly(expiryTasks.remove(key));
        if (!spec.hasLifetime()) {
            return;
        }
        String groupKey = spec.groupKey();
        TaskToken handle = executionDispatcher.runGlobalLater(
                plugin,
                () -> {
                    expiryTasks.remove(key);
                    expiryAction.accept(groupKey, key);
                },
                spec.lifetimeTicks()
        );
        if (handle != null) {
            expiryTasks.put(key, handle);
        }
    }

    public void cancelQuietly(TaskToken handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (RuntimeException _) {

        }
    }

    public void cancelExpiry(String runtimeKey) {
        cancelQuietly(expiryTasks.remove(runtimeKey));
    }

    public void cancelAllExpiry() {
        for (TaskToken handle : Map.copyOf(expiryTasks).values()) {
            cancelQuietly(handle);
        }
        expiryTasks.clear();
    }

    public Set<String> groupKeys() {
        return Set.copyOf(displaysByGroup.keySet());
    }

    public Set<String> membersOf(String groupKey) {
        return displaysByGroup.get(groupKey);
    }

    public Set<String> removeGroup(String groupKey) {
        return displaysByGroup.remove(groupKey);
    }

    public void removeGroupMember(String groupKey, String runtimeKey) {
        Set<String> members = displaysByGroup.get(groupKey);
        if (members == null) {
            return;
        }
        members.remove(runtimeKey);
        if (members.isEmpty()) {
            displaysByGroup.remove(groupKey);
        }
    }

    public void clearGroups() {
        displaysByGroup.clear();
    }
}

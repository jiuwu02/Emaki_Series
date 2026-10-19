package emaki.jiuwu.craft.dungeon.orchestration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;

public final class InstanceRegistry {

    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();

    public boolean register(DungeonInstance instance) {
        if (instance == null) {
            return false;
        }
        return instances.putIfAbsent(instance.instanceId(), instance) == null;
    }

    public DungeonInstance get(String instanceId) {
        return instanceId == null ? null : instances.get(instanceId);
    }

    public DungeonInstance unregister(String instanceId) {
        return instanceId == null ? null : instances.remove(instanceId);
    }

    public Collection<DungeonInstance> all() {
        return List.copyOf(instances.values());
    }

    public List<DungeonInstance> byDungeon(String dungeonId) {
        List<DungeonInstance> matched = new ArrayList<>();
        for (DungeonInstance instance : instances.values()) {
            if (instance.dungeonId().equals(dungeonId)) {
                matched.add(instance);
            }
        }
        return matched;
    }

    public int countActive(String dungeonId) {
        int count = 0;
        for (DungeonInstance instance : instances.values()) {
            if (instance.dungeonId().equals(dungeonId) && instance.state() == InstanceState.RUNNING) {
                count++;
            }
        }
        return count;
    }

    public int size() {
        return instances.size();
    }

    public void clear() {
        instances.clear();
    }
}

package emaki.jiuwu.craft.dungeon.random.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;

public final class RandomInstance {

    private final String instanceId;
    private final long seed;
    private final Seeds seeds;
    private final String configHash;
    private final String topologyHash;
    private final int roomCount;
    private final long createdAt;
    private final AtomicReference<RandomInstanceState> state =
            new AtomicReference<>(RandomInstanceState.CREATED);
    private final List<String> events = new ArrayList<>();
    private volatile Layout layout;
    private volatile DungeonInstance dungeonInstance;

    public RandomInstance(String instanceId, long seed, Seeds seeds, String configHash,
            String topologyHash, int roomCount) {
        this.instanceId = instanceId;
        this.seed = seed;
        this.seeds = seeds;
        this.configHash = configHash;
        this.topologyHash = topologyHash;
        this.roomCount = roomCount;
        this.createdAt = System.currentTimeMillis();
    }

    public String instanceId() {
        return instanceId;
    }

    public long seed() {
        return seed;
    }

    public Seeds seeds() {
        return seeds;
    }

    public String configHash() {
        return configHash;
    }

    public String topologyHash() {
        return topologyHash;
    }

    public int roomCount() {
        return roomCount;
    }

    public long createdAt() {
        return createdAt;
    }

    public RandomInstanceState state() {
        return state.get();
    }

    public boolean transition(RandomInstanceState from, RandomInstanceState to) {
        return state.compareAndSet(from, to);
    }

    public void forceState(RandomInstanceState to) {
        state.set(to);
    }

    public Layout layout() {
        return layout;
    }

    public void layout(Layout layout) {
        this.layout = layout;
    }

    public DungeonInstance dungeonInstance() {
        return dungeonInstance;
    }

    public void dungeonInstance(DungeonInstance dungeonInstance) {
        this.dungeonInstance = dungeonInstance;
    }

    public List<String> events() {
        return events;
    }

    public void record(String event) {
        events.add(event);
    }
}

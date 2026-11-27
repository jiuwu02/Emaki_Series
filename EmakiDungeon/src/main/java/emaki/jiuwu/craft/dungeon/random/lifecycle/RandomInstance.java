package emaki.jiuwu.craft.dungeon.random.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;

public final class RandomInstance {

    private final String instanceId;
    private final String dungeonId;
    private final long seed;
    private final Seeds seeds;
    private final String configHash;
    private final String topologyHash;
    private final int roomCount;
    private final String generatorId;
    private final int algorithmVersion;
    private final String layoutHash;
    private final String templatePoolHash;
    private final long createdAt;
    private final AtomicReference<RandomInstanceState> state =
            new AtomicReference<>(RandomInstanceState.CREATED);
    private final List<String> events = new ArrayList<>();
    private volatile Layout layout;
    private volatile DungeonInstance dungeonInstance;
    private volatile UUID testPlayer;

    public RandomInstance(String instanceId, String dungeonId, long seed, Seeds seeds, String configHash,
            String topologyHash, int roomCount) {
        this(instanceId, dungeonId, seed, seeds, configHash, topologyHash, roomCount,
                "legacy", 1, "", "");
    }

    public RandomInstance(String instanceId, String dungeonId, long seed, Seeds seeds, String configHash,
            String topologyHash, int roomCount, String generatorId, int algorithmVersion,
            String layoutHash, String templatePoolHash) {
        this.generatorId = generatorId;
        this.algorithmVersion = algorithmVersion;
        this.layoutHash = layoutHash;
        this.templatePoolHash = templatePoolHash;
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
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

    /** 创建该实例的副本 ID, 用于回溯到 world/<副本ID>/config.yml 的 random 段。 */
    public String dungeonId() {
        return dungeonId;
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

    public String generatorId() {
        return generatorId;
    }

    public int algorithmVersion() {
        return algorithmVersion;
    }

    public String layoutHash() {
        return layoutHash;
    }

    public String templatePoolHash() {
        return templatePoolHash;
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

    /** 编辑器测试实例: 生成完成后把该玩家传送进去; null 表示普通实例。 */
    public UUID testPlayer() {
        return testPlayer;
    }

    public void testPlayer(UUID testPlayer) {
        this.testPlayer = testPlayer;
    }

    public List<String> events() {
        return events;
    }

    public void record(String event) {
        events.add(event);
    }
}

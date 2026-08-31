package emaki.jiuwu.craft.mobs.spawner;

public interface SpawnHandler {

    void register(SpawnRule rule);

    void clear();
}

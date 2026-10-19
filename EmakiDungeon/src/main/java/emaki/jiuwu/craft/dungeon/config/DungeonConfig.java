package emaki.jiuwu.craft.dungeon.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record DungeonConfig(
        int poolMaxWorlds
) {

    public static DungeonConfig defaults() {
        return new DungeonConfig(5);
    }

    public static DungeonConfig parse(YamlSection section) {
        if (section == null || section.isEmpty()) {
            throw new IllegalArgumentException("config empty");
        }
        Integer poolMaxWorlds = section.getInt("pool.max-worlds");
        if (poolMaxWorlds == null || poolMaxWorlds < 1) {
            throw new IllegalArgumentException("pool.max-worlds must be >= 1");
        }
        return new DungeonConfig(poolMaxWorlds);
    }
}

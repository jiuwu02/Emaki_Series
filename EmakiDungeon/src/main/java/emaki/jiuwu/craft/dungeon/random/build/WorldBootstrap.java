package emaki.jiuwu.craft.dungeon.random.build;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;

import emaki.jiuwu.craft.dungeon.world.WorldMounts;
import emaki.jiuwu.craft.dungeon.world.WorldTypeSupport;

public final class WorldBootstrap {

    public static World createWorld(String worldName, String worldType) {
        World world = WorldMounts.create(WorldTypeSupport.apply(
                new org.bukkit.WorldCreator(worldName), worldType));
        if (world == null) {
            throw new IllegalStateException("createWorld returned null: " + worldName);
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.SPAWN_MOBS, Boolean.FALSE);
        return world;
    }

    private WorldBootstrap() {
    }
}

package emaki.jiuwu.craft.dungeon.random.build;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import emaki.jiuwu.craft.dungeon.world.WorldMounts;

public final class WorldBootstrap {

    private static final class VoidGenerator extends ChunkGenerator {
    }

    public static World createAirWorld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidGenerator());
        World world = WorldMounts.create(creator);
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

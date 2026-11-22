package emaki.jiuwu.craft.dungeon.world;

import java.util.Locale;

import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

public final class WorldTypeSupport {

    public static final String TYPE_AIR = "air";
    public static final String TYPE_NORMAL = "normal";
    public static final String TYPE_NETHER = "nether";
    public static final String TYPE_END = "end";

    private static final class VoidGenerator extends ChunkGenerator {
    }

    private WorldTypeSupport() {
    }

    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case TYPE_NORMAL -> TYPE_NORMAL;
            case TYPE_NETHER -> TYPE_NETHER;
            case TYPE_END -> TYPE_END;
            default -> TYPE_AIR;
        };
    }

    public static WorldCreator creator(String worldName) {
        return new WorldCreator(worldName);
    }

    public static WorldCreator apply(WorldCreator creator, String raw) {
        return switch (normalize(raw)) {
            case TYPE_NORMAL -> creator.environment(Environment.NORMAL);
            case TYPE_NETHER -> creator.environment(Environment.NETHER);
            case TYPE_END -> creator.environment(Environment.THE_END);
            default -> creator.environment(Environment.NORMAL).generator(new VoidGenerator());
        };
    }
}

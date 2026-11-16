package emaki.jiuwu.craft.dungeon.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;

public final class WorldMounts {

    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private static final boolean FOLIA = probeFolia();
    private static volatile WorldMountSupport support;

    private WorldMounts() {
    }

    public static void requireSupport() {
        support();
    }

    public static World create(WorldCreator creator) {
        WorldMountSupport mounted = support();
        return mounted == null ? Bukkit.createWorld(creator) : mounted.create(creator);
    }

    public static boolean unload(World world, boolean save) {
        if (FOLIA && Bukkit.getServer().isStopping()) {
            return false;
        }
        WorldMountSupport mounted = support();
        return mounted == null ? Bukkit.unloadWorld(world, save) : mounted.unload(world, save);
    }

    public static void save(World world, Runnable afterSave) {
        WorldMountSupport mounted = support();
        if (mounted == null) {
            world.save();
            afterSave.run();
            return;
        }
        mounted.save(world, afterSave);
    }

    private static WorldMountSupport support() {
        if (!FOLIA) {
            return null;
        }
        WorldMountSupport mounted = support;
        if (mounted != null) {
            return mounted;
        }
        synchronized (WorldMounts.class) {
            if (support == null) {
                support = loadSupport();
            }
            return support;
        }
    }

    private static WorldMountSupport loadSupport() {
        WorldMountSupport mounted = EmakiCoreLibApi.worldMountSupport();
        if (mounted == null) {
            throw new IllegalStateException("Folia 世界桥装载失败: EmakiCoreLib 未提供 WorldMountSupport 实现, EmakiNms 运行库未就绪");
        }
        return mounted;
    }

    private static boolean probeFolia() {
        try {
            Class.forName(FOLIA_MARKER, false, WorldMounts.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }
}

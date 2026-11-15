package emaki.jiuwu.craft.nms;

import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public final class BukkitWorldMountSupport implements WorldMountSupport {

    @Override
    public World create(WorldCreator creator) {
        return Bukkit.createWorld(creator);
    }

    @Override
    public boolean unload(World world, boolean save) {
        return Bukkit.unloadWorld(world, save);
    }

    @Override
    public void save(World world, Runnable onSaved) {
        world.save();
        onSaved.run();
    }
}

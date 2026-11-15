package emaki.jiuwu.craft.nms;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;
import emaki.jiuwu.craft.nms.v26_1.FoliaWorldSupport26_1Impl;
import emaki.jiuwu.craft.nms.v26_2.FoliaWorldSupport26_2Impl;
import emaki.jiuwu.craft.nms.v26_3.FoliaWorldSupport26_3Impl;

import org.bukkit.World;
import org.bukkit.WorldCreator;

public final class WorldMountSupportImpl implements WorldMountSupport {

    private static final List<String> SUPPORTED_VERSION_LINES = List.of("26.1", "26.2", "26.3");

    private final WorldMountSupport delegate;

    public WorldMountSupportImpl() {
        this.delegate = FoliaPlatform.isFolia() ? foliaWorldSupport() : new BukkitWorldMountSupport();
    }

    @Override
    public World create(WorldCreator creator) {
        return delegate.create(creator);
    }

    @Override
    public boolean unload(World world, boolean save) {
        return delegate.unload(world, save);
    }

    @Override
    public void save(World world, Runnable onSaved) {
        delegate.save(world, onSaved);
    }

    private static WorldMountSupport foliaWorldSupport() {
        String versionLine = FoliaPlatform.serverVersionLine();
        return switch (versionLine) {
            case "26.1" -> new FoliaWorldSupport26_1Impl();
            case "26.2" -> new FoliaWorldSupport26_2Impl();
            case "26.3" -> new FoliaWorldSupport26_3Impl();
            default -> throw new IllegalStateException("Unsupported server version " + FoliaPlatform.serverVersion()
                    + " (version line " + versionLine + "), emakinms supports version lines " + SUPPORTED_VERSION_LINES);
        };
    }
}

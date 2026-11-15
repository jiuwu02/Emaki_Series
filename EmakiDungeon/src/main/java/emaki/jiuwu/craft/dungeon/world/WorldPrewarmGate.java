package emaki.jiuwu.craft.dungeon.world;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

public final class WorldPrewarmGate implements Listener {

    private final WorldPool pool;

    public WorldPrewarmGate(WorldPool pool) {
        this.pool = pool;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        pool.openPrewarm();
    }
}

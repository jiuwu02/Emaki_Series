package emaki.jiuwu.craft.corelib.execution;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface ThreadOwnership {

    boolean isGlobalOwned();

    boolean isEntityOwned(Entity entity);

    boolean isLocationOwned(Location location);
}

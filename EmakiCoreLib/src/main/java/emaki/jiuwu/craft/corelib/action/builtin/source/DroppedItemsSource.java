package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class DroppedItemsSource extends BaseSource {

    public DroppedItemsSource() {
        super("dropped_items", "管道原点周围的掉落物。",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("radius", CoreStageParameterType.DOUBLE, "8", "搜索半径"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大掉落物数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.dropped_items.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.dropped_items.no_origin");
        }
        double radius = Math.max(0D, arguments.getDouble("radius", 8D));
        int limit = Math.max(0, arguments.getInt("limit", 0));
        World world = centre.getWorld();
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(centre, radius, radius, radius)) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            if (entity instanceof Item) {
                subjects.add(CoreActionSubject.of(entity));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.dropped_items.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}

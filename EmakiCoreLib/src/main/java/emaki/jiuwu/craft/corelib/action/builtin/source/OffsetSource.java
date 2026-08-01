package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

/**
 * A location offset from the pipeline origin.
 *
 * <p>With {@code relative=false} the offset is along world axes. With {@code relative=true} it is along
 * the origin's own facing, so {@code z} means forward and {@code x} means right.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: pure arithmetic on a {@code Location} the context already holds. No
 * block, chunk or entity state is read.</p>
 */
public final class OffsetSource extends BaseSource {

    public OffsetSource() {
        super("offset", "A location offset from the pipeline origin.",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.optional("x", CoreStageParameterType.DOUBLE, "0", "X offset"),
                CoreStageParameter.optional("y", CoreStageParameterType.DOUBLE, "0", "Y offset"),
                CoreStageParameter.optional("z", CoreStageParameterType.DOUBLE, "0", "Z offset"),
                CoreStageParameter.optional("relative", CoreStageParameterType.BOOLEAN, "false",
                        "Offset along the origin facing instead of world axes"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.offset.no_origin");
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreSourceResult.empty("action.source.offset.no_origin");
        }
        double x = arguments.getDouble("x", 0D);
        double y = arguments.getDouble("y", 0D);
        double z = arguments.getDouble("z", 0D);
        Location result = origin.clone();
        if (arguments.getBoolean("relative", false)) {
            Vector forward = origin.getDirection().setY(0D);
            if (forward.lengthSquared() <= 0D) {
                forward = new Vector(0D, 0D, 1D);
            }
            forward.normalize();
            Vector right = new Vector(-forward.getZ(), 0D, forward.getX());
            result.add(right.multiply(x)).add(0D, y, 0D).add(forward.multiply(z));
        } else {
            result.add(x, y, z);
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(result)));
    }
}

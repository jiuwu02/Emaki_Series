package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class AtSource extends BaseSource {

    public AtSource() {
        super("at", "An absolute or origin-relative coordinate.",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.optional("world", CoreStageParameterType.STRING, "",
                        "World name or key, defaults to the origin world"),
                CoreStageParameter.optional("x", CoreStageParameterType.STRING, "~", "X, supports ~"),
                CoreStageParameter.optional("y", CoreStageParameterType.STRING, "~", "Y, supports ~"),
                CoreStageParameter.optional("z", CoreStageParameterType.STRING, "~", "Z, supports ~"));
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return context.caster().entityOrNull() == null
                ? CoreActionExecutionTarget.global()
                : CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location origin = origin(context);
        World fallback = origin == null ? null : origin.getWorld();
        String requested = arguments.getString("world");
        if (requested.isBlank() && fallback == null) {
            return CoreSourceResult.invalid("action.source.at.world_required");
        }
        World world = StageSupport.world(requested, fallback);
        if (world == null) {
            return CoreSourceResult.invalid("action.source.at.world_not_found",
                    Map.of("world", requested));
        }
        String x = arguments.getString("x", "~");
        String y = arguments.getString("y", "~");
        String z = arguments.getString("z", "~");
        if (origin == null && (relative(x) || relative(y) || relative(z))) {
            return CoreSourceResult.invalid("action.source.at.relative_without_origin");
        }
        double baseX = origin == null ? 0D : origin.getX();
        double baseY = origin == null ? 0D : origin.getY();
        double baseZ = origin == null ? 0D : origin.getZ();
        Location location = new Location(world,
                ValueParsers.parseCoordinate(x, baseX),
                ValueParsers.parseCoordinate(y, baseY),
                ValueParsers.parseCoordinate(z, baseZ));
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(location)));
    }

    private static Location origin(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static boolean relative(String raw) {
        return raw != null && raw.trim().startsWith("~");
    }
}

package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detached scheduler target declared by a third-party action.
 */
public record CoreActionExecutionTarget(
        @NotNull CoreActionExecutionDomain domain,
        @Nullable String world,
        double x,
        double y,
        double z) {

    public CoreActionExecutionTarget {
        domain = domain == null ? CoreActionExecutionDomain.UNDECLARED : domain;
        world = world == null ? null : world.trim();
    }

    public static @NotNull CoreActionExecutionTarget undeclared() {
        return new CoreActionExecutionTarget(CoreActionExecutionDomain.UNDECLARED, null, 0D, 0D, 0D);
    }

    public static @NotNull CoreActionExecutionTarget global() {
        return new CoreActionExecutionTarget(CoreActionExecutionDomain.SERVER_GLOBAL, null, 0D, 0D, 0D);
    }

    public static @NotNull CoreActionExecutionTarget contextEntity() {
        return new CoreActionExecutionTarget(CoreActionExecutionDomain.CONTEXT_ENTITY, null, 0D, 0D, 0D);
    }

    public static @NotNull CoreActionExecutionTarget location(@Nullable String world, double x, double y, double z) {
        return new CoreActionExecutionTarget(CoreActionExecutionDomain.LOCATION_REGION, world, x, y, z);
    }

    public static @NotNull CoreActionExecutionTarget asyncCompute() {
        return new CoreActionExecutionTarget(CoreActionExecutionDomain.ASYNC_COMPUTE, null, 0D, 0D, 0D);
    }
}

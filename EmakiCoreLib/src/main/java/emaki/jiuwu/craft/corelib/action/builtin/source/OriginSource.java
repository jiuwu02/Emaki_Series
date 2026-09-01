package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;

public final class OriginSource extends BaseSource {

    public OriginSource() {
        super("origin", "The pipeline origin as a location target.",
                CoreActionExecutionDomain.SERVER_GLOBAL);
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
        Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {

            return CoreSourceResult.empty("action.source.origin.no_origin");
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreSourceResult.empty("action.source.origin.no_origin");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(origin)));
    }
}

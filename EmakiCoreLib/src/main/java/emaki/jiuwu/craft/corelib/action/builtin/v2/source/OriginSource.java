package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;

/**
 * The pipeline's spatial reference point as a location subject.
 *
 * <p>Domain {@code SERVER_GLOBAL}: the origin is a {@code Location} value the context already holds, so
 * reading it touches no world or block state.</p>
 */
public final class OriginSource extends BaseSource {

    public OriginSource() {
        super("origin", "The pipeline origin as a location target.",
                CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        org.bukkit.Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {
            // A console-triggered pipeline has neither caster nor origin. That is a normal state for a
            // trigger, not a configuration error, so it is Empty rather than Invalid.
            return CoreSourceResult.empty("action.v2.source.origin.no_origin");
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreSourceResult.empty("action.v2.source.origin.no_origin");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(origin)));
    }
}

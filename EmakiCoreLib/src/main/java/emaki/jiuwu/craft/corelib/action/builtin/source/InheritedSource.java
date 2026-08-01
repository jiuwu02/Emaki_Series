package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;

/**
 * The target flow supplied by the caller or the previous phase.
 *
 * <p>Must be written explicitly: omitting the source means {@code self}, so a pipeline that intends to
 * act on inherited targets and leaves the source out would silently act on the caster instead.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: pure reference passing of the list the context already holds.</p>
 */
public final class InheritedSource extends BaseSource {

    public InheritedSource() {
        super("inherited", "Targets inherited from the caller or previous phase.",
                CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (context.targets().isEmpty()) {
            return CoreSourceResult.empty("action.v2.source.inherited.no_targets");
        }
        return CoreSourceResult.selected(context.targets());
    }
}

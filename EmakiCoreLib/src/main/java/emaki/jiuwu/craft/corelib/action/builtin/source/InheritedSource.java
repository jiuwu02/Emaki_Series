package emaki.jiuwu.craft.corelib.action.builtin.source;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class InheritedSource extends BaseSource {

    public InheritedSource() {
        super("inherited", "Targets inherited from the caller or previous phase.",
                CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (context.targets().isEmpty()) {
            return CoreSourceResult.empty("action.source.inherited.no_targets");
        }
        return CoreSourceResult.selected(context.targets());
    }
}

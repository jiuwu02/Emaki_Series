package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class SelfSource extends BaseSource {

    public SelfSource() {
        super("self", "The caster itself.", CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        CoreActionSubject caster = context.caster();
        if (caster instanceof CoreActionSubject.Absent) {
            return CoreSourceResult.empty("action.source.self.no_caster");
        }
        return CoreSourceResult.selected(List.of(caster));
    }
}

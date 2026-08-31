package emaki.jiuwu.craft.mobs.action.source;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.mobs.api.MobActionKeys;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TargetSource extends BaseSource {

    public TargetSource() {
        super("target", 
              "The target entity (from on_target or on_damage_give trigger).",
              CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
                                             @NotNull CoreResolvedArguments arguments) {
        return context.get(MobActionKeys.TARGET)
                .map(target -> CoreSourceResult.selected(List.of(CoreActionSubject.of(target))))
                .orElse(CoreSourceResult.empty("action.source.target.not_found"));
    }
}

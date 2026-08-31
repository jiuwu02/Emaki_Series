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

public final class KillerSource extends BaseSource {

    public KillerSource() {
        super("killer", 
              "The entity that killed the caster (from on_death trigger).",
              CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
                                             @NotNull CoreResolvedArguments arguments) {
        return context.get(MobActionKeys.KILLER)
                .map(killer -> CoreSourceResult.selected(List.of(CoreActionSubject.of(killer))))
                .orElse(CoreSourceResult.empty("action.source.killer.not_found"));
    }
}

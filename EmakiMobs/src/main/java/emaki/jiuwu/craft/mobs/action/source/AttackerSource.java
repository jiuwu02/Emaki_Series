package emaki.jiuwu.craft.mobs.action.source;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.mobs.api.MobActionKeys;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AttackerSource extends BaseSource {

    public AttackerSource() {
        super("attacker", 
              "The entity that attacked the caster (from on_damage_take trigger).",
              CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
                                             @NotNull CoreResolvedArguments arguments) {
        return context.get(MobActionKeys.ATTACKER)
                .map(attacker -> CoreSourceResult.selected(List.of(CoreActionSubject.of(attacker))))
                .orElse(CoreSourceResult.empty("action.source.attacker.not_found"));
    }
}

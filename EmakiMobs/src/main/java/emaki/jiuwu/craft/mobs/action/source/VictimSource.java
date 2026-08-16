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

/**
 * 选择器：victim
 * 
 * <p>从触发器上下文中选择被施法者击杀的实体。
 * 主要用于 {@code on_kill} 触发器。
 * 
 * <p><b>配置示例：</b>
 * <pre>{@code
 * on_kill:
 *   - "victim | spawn_particle particle=SOUL count=20"
 *   - "self | heal amount=5"  # 吸血效果
 * }</pre>
 */
public final class VictimSource extends BaseSource {

    public VictimSource() {
        super("victim", 
              "The entity killed by the caster (from on_kill trigger).",
              CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
                                             @NotNull CoreResolvedArguments arguments) {
        return context.get(MobActionKeys.VICTIM)
                .map(victim -> CoreSourceResult.selected(List.of(CoreActionSubject.of(victim))))
                .orElse(CoreSourceResult.empty("action.source.victim.not_found"));
    }
}

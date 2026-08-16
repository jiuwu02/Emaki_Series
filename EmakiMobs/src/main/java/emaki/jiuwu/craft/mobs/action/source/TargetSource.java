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
 * 选择器：target
 * 
 * <p>从触发器上下文中选择施法者的目标实体。
 * 主要用于 {@code on_target}（锁定目标）和 {@code on_damage_give}（造成伤害）触发器。
 * 
 * <p><b>配置示例：</b>
 * <pre>{@code
 * on_damage_give:
 *   - "target | give_potion_effect type=POISON duration=60 amplifier=1"
 * 
 * on_target:
 *   - "target | send_message text='<red>你被 Boss 锁定了！'"
 * }</pre>
 */
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

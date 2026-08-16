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

/**
 * 选择器：attacker
 * 
 * <p>从触发器上下文中选择攻击施法者的实体。
 * 主要用于 {@code on_damage_take} 触发器。
 * 
 * <p>如果攻击者不存在（如环境伤害），返回空结果。
 * 
 * <p><b>配置示例：</b>
 * <pre>{@code
 * on_damage_take:
 *   - "attacker | give_potion_effect type=SLOWNESS duration=40 amplifier=0"
 * }</pre>
 */
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

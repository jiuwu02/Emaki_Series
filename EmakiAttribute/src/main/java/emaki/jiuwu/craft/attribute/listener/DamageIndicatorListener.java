package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.config.DamageIndicatorConfig;
import emaki.jiuwu.craft.attribute.service.DamageIndicatorService;

import java.util.function.Supplier;

/**
 * 把伤害结算结果转成飘字。
 *
 * <p>监听 {@link EmakiAttributeDamageEvent}，覆盖普通攻击、暴击与闪避三个触发器。
 * 生命恢复不在此处：回血额度是在事件发布之后才算出来的，那条路径挂在
 * {@code DamageRecoveryCalculator} 上。
 *
 * <p>用 {@code MONITOR} 优先级读取其他插件改写后的最终值，并且不修改事件。
 */
public final class DamageIndicatorListener implements Listener {

    private final Supplier<DamageIndicatorService> serviceSupplier;
    private final Supplier<DamageIndicatorConfig> configSupplier;

    public DamageIndicatorListener(Supplier<DamageIndicatorService> serviceSupplier,
            Supplier<DamageIndicatorConfig> configSupplier) {
        this.serviceSupplier = serviceSupplier;
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttributeDamage(EmakiAttributeDamageEvent event) {
        DamageIndicatorService service = serviceSupplier.get();
        DamageIndicatorConfig config = configSupplier.get();
        if (service == null || config == null || !config.enabled()) {
            return;
        }
        if (config.isIgnored(event.getCause())) {
            return;
        }
        // 闪避必须先判：闪避成功时最终伤害为 0，若先过滤伤害就永远看不到闪避飘字。
        if (event.getVariables() != null && event.getVariables().getBoolean(false, "dodged")) {
            service.showDodge(event.getTarget(), event.getAttacker());
            return;
        }
        if (event.getFinalDamage() <= 0D) {
            return;
        }
        service.showDamage(event.getTarget(), event.getAttacker(), event.getFinalDamage(), event.isCritical());
    }
}

package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.attribute.api.event.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.config.DamageIndicatorConfig;
import emaki.jiuwu.craft.attribute.service.DamageIndicatorService;

import java.util.function.Supplier;

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

package emaki.jiuwu.craft.attribute.service;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.model.ResolvedDamage;

public final class PerfectTakeoverCoordinator implements Listener {

    public record Pending(ResolvedDamage resolvedDamage) {
    }

    private final AttributeService service;
    private final Map<EntityDamageEvent, Pending> pending = new HashMap<>();

    PerfectTakeoverCoordinator(AttributeService service) {
        this.service = service;
    }

    public boolean isClaimed(EntityDamageEvent event) {
        return event != null && pending.containsKey(event);
    }

    @SuppressWarnings("deprecation")
    public void claimAndApply(EntityDamageEvent event,
            ResolvedDamage resolvedDamage,
            Entity visualSource,
            boolean bypassInvulnerability) {
        if (event == null || resolvedDamage == null) {
            return;
        }
        if (bypassInvulnerability && event.getEntity() instanceof LivingEntity livingEntity) {
            zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.INVULNERABILITY_REDUCTION);
            livingEntity.setNoDamageTicks(0);
            livingEntity.setLastDamage(0D);
        }
        neutralizeVanillaMitigation(event);
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, resolvedDamage.finalDamage());
        pending.put(event, new Pending(resolvedDamage));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageMonitor(EntityDamageEvent event) {
        Pending claimed = pending.remove(event);
        if (claimed == null) {
            return;
        }
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            return;
        }
        service.applyDamageSideEffectsAsync(claimed.resolvedDamage()).exceptionally(throwable -> false);
    }

    private void neutralizeVanillaMitigation(EntityDamageEvent event) {
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.HARD_HAT);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.ARMOR);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.RESISTANCE);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.MAGIC);
        zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.FREEZING);
        // BLOCKING 默认保留给原版：举盾按原版规则完全免除该次伤害。切到 attribute 模式后由
        // 伤害类型的格挡阶段结算，必须在此清零，否则原版减伤会与 EA 阶段重复叠加。
        if (service.config().shield().attributeModeEnabled()) {
            zeroModifierIfApplicable(event, EntityDamageEvent.DamageModifier.BLOCKING);
        }
    }

    @SuppressWarnings("deprecation")
    private void zeroModifierIfApplicable(EntityDamageEvent event, EntityDamageEvent.DamageModifier modifier) {
        try {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, 0D);
            }
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            service.plugin().getLogger().log(Level.WARNING,
                    "Vanilla damage modifier neutralization failed: entity=" + event.getEntity().getType()
                            + ", modifier=" + modifier
                            + ", operation=zero_damage_modifier, cause=" + exception,
                    exception);
        }
    }
}

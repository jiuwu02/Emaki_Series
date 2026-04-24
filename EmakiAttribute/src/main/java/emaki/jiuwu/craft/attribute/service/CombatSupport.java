package emaki.jiuwu.craft.attribute.service;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.corelib.entity.EntityPhysicsSupport;

/**
 * Shared combat utility methods extracted from AttributeListener and MmoItemsBridge
 * to eliminate duplication.
 */
public final class CombatSupport {

    private CombatSupport() {
    }

    /**
     * Builds a {@link DamageContextVariables} populated with common damage-event keys
     * (cause, base/source/input/final damage, target UUID/type, and optional damager info).
     */
    public static DamageContextVariables baseContext(EntityDamageEvent event, LivingEntity target) {
        DamageContextVariables.Builder context = DamageContextVariables.builder();
        String cause = event.getCause().name();
        context.put("cause", cause);
        context.put("damage_cause", cause);
        context.put("damage_cause_id", cause);
        context.put("base_damage", event.getDamage());
        context.put("source_damage", event.getDamage());
        context.put("input_damage", event.getDamage());
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            context.put("damager_type", byEntityEvent.getDamager().getType().name());
            context.put("damager_uuid", byEntityEvent.getDamager().getUniqueId().toString());
        }
        return context.build();
    }

    /**
     * Applies synthetic knockback to the target from the source entity, respecting
     * the config toggle and strength setting.
     */
    public static void applySyntheticKnockback(LivingEntity target, Entity source, double finalDamage,
            AttributeConfig config) {
        if (target == null || source == null || finalDamage <= 0D || !target.isValid() || target.isDead()
                || !config.syntheticHitKnockback()) {
            return;
        }
        double strength = Math.max(0D, config.syntheticHitKnockbackStrength());
        if (strength <= 0D) {
            return;
        }
        EntityPhysicsSupport.applyKnockback(target, source, strength);
    }

    /**
     * Walks the cause chain of the given throwable and returns the deepest root-cause message,
     * or {@code "unknown"} if no message is available.
     */
    public static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return "unknown";
        }
        return current.getMessage();
    }
}

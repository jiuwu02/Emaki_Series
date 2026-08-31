package emaki.jiuwu.craft.attribute.service;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.corelib.api.entity.EntityPhysicsSupport;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;

public final class CombatSupport {

    private CombatSupport() {
    }

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

    public static CompletableFuture<Boolean> applyFallbackDamage(EmakiAttributePlugin plugin,
            EmakiScheduling scheduling,
            LivingEntity target,
            double damage,
            String dispatcherName) {
        if (target == null || damage <= 0D) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            EmakiScheduling sched = scheduling != null ? scheduling : plugin.scheduling();
            if (sched == null) {
                future.completeExceptionally(new IllegalStateException(
                        dispatcherName + " damage dispatcher is unavailable."));
                return future;
            }
            var scheduled = sched.runForEntity(
                    plugin,
                    target,
                    () -> {
                        if (!target.isValid() || target.isDead()) {
                            future.complete(false);
                            return;
                        }
                        try {
                            target.setNoDamageTicks(0);
                            double remaining = Math.max(0D, damage);
                            double absorption = Math.max(0D, target.getAbsorptionAmount());
                            if (absorption > 0D) {
                                double absorbed = Math.min(absorption, remaining);
                                target.setAbsorptionAmount(Math.max(0D, absorption - absorbed));
                                remaining -= absorbed;
                            }
                            target.setLastDamage(damage);
                            if (remaining > 0D) {
                                target.setHealth(Math.max(0D, target.getHealth() - remaining));
                            }
                            future.complete(true);
                        } catch (Throwable throwable) {
                            future.completeExceptionally(throwable);
                        }
                    },
                    () -> future.completeExceptionally(new IllegalStateException(
                            dispatcherName + " damage entity retired before execution."))
            );
            if (scheduled == null) {
                future.completeExceptionally(new IllegalStateException(
                        dispatcherName + " damage scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

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

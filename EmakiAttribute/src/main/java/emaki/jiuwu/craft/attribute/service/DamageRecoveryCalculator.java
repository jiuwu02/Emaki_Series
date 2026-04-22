package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Map;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.RecoveryDefinition;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

final class DamageRecoveryCalculator {

    private final AttributeService service;

    DamageRecoveryCalculator(AttributeService service) {
        this.service = service;
    }

    void applyRecovery(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        if (damageContext == null || damageType == null || !damageType.hasRecovery() || result == null) {
            return;
        }
        LivingEntity attacker = damageContext.attacker();
        if (attacker == null || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        double recoveryAmount = resolveRecoveryAmount(damageContext, damageType.recovery(), finalDamage);
        if (recoveryAmount <= 0D) {
            return;
        }
        double currentHealth = Math.max(0D, attacker.getHealth());
        AttributeInstance maxHealthAttribute = attacker.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? Math.max(1D, currentHealth) : Math.max(1D, maxHealthAttribute.getValue());
        attacker.setHealth(Math.min(maxHealth, currentHealth + recoveryAmount));
        service.scheduleHealthSync(attacker);
    }

    private double resolveRecoveryAmount(DamageContext damageContext, RecoveryDefinition recovery, double finalDamage) {
        if (damageContext == null || recovery == null) {
            return 0D;
        }
        DamageContextVariables.Builder context = damageContext.variables().toBuilder();
        AttributeSnapshot sourceSnapshot = snapshotForRecovery(damageContext, recovery.source());
        AttributeSnapshot resistanceSnapshot = snapshotForRecovery(damageContext, recovery.resistanceSource());
        Map<String, Object> evaluationContext = context.build().asMap();
        double flat = sumAttributes(sourceSnapshot, evaluationContext, recovery.flatAttributes());
        double percent = sumAttributes(sourceSnapshot, evaluationContext, recovery.percentAttributes());
        double resistance = sumAttributes(resistanceSnapshot, evaluationContext, recovery.resistanceAttributes());
        double percentAmount = finalDamage * (percent / 100D);
        double grossRecovery = flat + percentAmount;
        context.put("input", finalDamage);
        context.put("base", finalDamage);
        context.put("damage", finalDamage);
        context.put("final_damage", finalDamage);
        context.put("flat", flat);
        context.put("percent", percent);
        context.put("percent_amount", percentAmount);
        context.put("gross", grossRecovery);
        context.put("resistance", resistance);
        context.put("healing_flat", flat);
        context.put("healing_percent", percent);
        context.put("healing_percent_amount", percentAmount);
        context.put("healing_gross", grossRecovery);
        context.put("healing_resistance", resistance);
        evaluationContext = context.build().asMap();
        double value = Texts.isBlank(recovery.expression())
                ? grossRecovery * (1D - (resistance / 100D))
                : ExpressionEngine.evaluate(recovery.expression(), evaluationContext);
        if (recovery.minResult() != null) {
            value = Math.max(value, recovery.minResult());
        }
        if (recovery.maxResult() != null) {
            value = Math.min(value, recovery.maxResult());
        }
        return Math.max(0D, value);
    }

    private AttributeSnapshot snapshotForRecovery(DamageContext damageContext, DamageStageSource source) {
        if (damageContext == null || source == null) {
            return null;
        }
        return switch (source) {
            case ATTACKER -> damageContext.attackerSnapshot();
            case TARGET -> damageContext.targetSnapshot();
            case CONTEXT -> null;
        };
    }

    private double sumAttributes(AttributeSnapshot snapshot, Map<String, ?> context, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            Double value = snapshot == null ? null : snapshot.values().get(id);
            if (value == null && context != null) {
                value = Numbers.tryParseDouble(context.get(id), null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }
}

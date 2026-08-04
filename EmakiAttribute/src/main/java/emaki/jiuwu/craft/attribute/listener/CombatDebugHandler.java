package emaki.jiuwu.craft.attribute.listener;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;

import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CombatDebugHandler {

    private final AttributeService attributeService;

    public CombatDebugHandler(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    public boolean shouldDebugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile) {
        return projectile != null
                ? attributeService.shouldTraceCombat(projectile, target)
                : attributeService.shouldTraceCombat(attacker, target);
    }

    public void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey) {
        debugCombat(attacker, target, projectile, phase, messageKey, Map.of());
    }

    public void debugCombat(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            String phase,
            String messageKey,
            Map<String, ?> replacements) {
        if (phase == null || Texts.isBlank(messageKey) || !shouldDebugCombat(attacker, target, projectile)) {
            return;
        }
        attributeService.logCombatDebug(phase, messageKey, replacements);
    }

    public String describeResolvedDamage(ResolvedDamage resolvedDamage) {
        if (resolvedDamage == null) {
            return "<null>";
        }
        return "damageType=" + (resolvedDamage.damageType() == null ? resolvedDamage.damageResult().damageTypeId() : resolvedDamage.damageType().id())
                + ", finalDamage=" + formatNumber(resolvedDamage.finalDamage())
                + ", critical=" + resolvedDamage.damageResult().critical()
                + ", stages=" + resolvedDamage.damageResult().stageValues();
    }

    public String describeEntity(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        String name = entity.getName();
        if (name == null || name.isBlank()) {
            name = entity.getType().name().toLowerCase(Locale.ROOT);
        }
        return name + "(" + entity.getType().name() + "," + entity.getUniqueId() + ")";
    }

    public String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }
}

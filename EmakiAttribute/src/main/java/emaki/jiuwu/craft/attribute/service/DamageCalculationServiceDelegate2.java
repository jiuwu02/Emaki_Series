package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import emaki.jiuwu.craft.attribute.api.event.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.formula.AttributeFormulaEvaluator;
import emaki.jiuwu.craft.attribute.model.AttributeContributionTrace;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.StageRole;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.RecoveryDefinition;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.service.MessageService;

final class DamageCalculationServiceDelegate2 {

    private final DamageCalculationService ownerRef;

    DamageCalculationServiceDelegate2(DamageCalculationService ownerRef) {
        this.ownerRef = ownerRef;
    }

    String debugStageChain(DamageTypeDefinition damageType) {
        if (damageType == null || damageType.stages().isEmpty()) {
            return DamageCalculationService.DEBUG_NONE;
        }
        StringBuilder builder = new StringBuilder();
        for (DamageStageDefinition stage : damageType.stages()) {
            if (stage == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" -> ");
            }
            builder.append(Texts.toStringSafe(stage.id()));
        }
        return builder.isEmpty() ? DamageCalculationService.DEBUG_NONE : builder.toString();
    }

    String debugClamps(DamageStageDefinition stage) {
        return "result[" + ownerRef.debugBound(stage.minResult()) + "," + ownerRef.debugBound(stage.maxResult()) + "]"
                + " chance[" + ownerRef.debugBound(stage.minChance()) + "," + ownerRef.debugBound(stage.maxChance()) + "]"
                + " multiplier[" + ownerRef.debugBound(stage.minMultiplier()) + "," + ownerRef.debugBound(stage.maxMultiplier()) + "]";
    }

    String debugBound(Double value) {
        return value == null ? DamageCalculationService.DEBUG_NONE : ownerRef.debugNumber(value);
    }

    String debugContributions(AttributeSnapshot snapshot, DamageContextVariables variables, Map<String, List<AttributeContributionTrace>> traces, List<String> attributeIds) {
        if (attributeIds == null || attributeIds.isEmpty()) {
            return DamageCalculationService.DEBUG_NONE;
        }
        StringBuilder builder = new StringBuilder();
        for (String attributeId : attributeIds) {
            if (Texts.isBlank(attributeId)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(attributeId).append('=');

            Double snapshotValue = snapshot == null ? null : snapshot.values().get(attributeId);
            if (snapshotValue != null) {
                builder.append(ownerRef.debugNumber(snapshotValue));
                double spread = ownerRef.readSnapshotAttribute(snapshot, AttributeSnapshot.rangeSpreadKey(attributeId));
                if (spread != 0D) {
                    builder.append("(+spread ").append(ownerRef.debugNumber(spread)).append(')');
                }
                builder.append(" from ").append(ownerRef.debugAttributeSources(traces, attributeId));
                continue;
            }
            Double contextValue = variables == null
                    ? null
                    : Numbers.tryParseDouble(variables.get(attributeId), null);
            if (contextValue != null) {
                builder.append(ownerRef.debugNumber(contextValue)).append(" from context_variable");
                continue;
            }
            builder.append(ownerRef.debugNumber(0D)).append(" from absent(no_snapshot_no_context)");
        }
        return builder.isEmpty() ? DamageCalculationService.DEBUG_NONE : builder.toString();
    }

    String debugAttributeSources(Map<String, List<AttributeContributionTrace>> traces, String attributeId) {
        if (traces == null) {
            return "snapshot(non_player_no_item_layer_trace)";
        }
        List<AttributeContributionTrace> matched = traces.get(attributeId);
        if (matched == null || matched.isEmpty()) {
            return "snapshot(unattributed)";
        }
        StringBuilder builder = new StringBuilder();
        for (AttributeContributionTrace trace : matched) {
            if (trace == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" + ");
            }
            builder.append('[')
                    .append(ownerRef.debugField(trace.slot()))
                    .append('/')
                    .append(ownerRef.debugField(trace.layer()))
                    .append('/')
                    .append(ownerRef.debugField(trace.sourceLabel()))
                    .append(" module=").append(ownerRef.debugField(trace.sourceModule()))
                    .append(" type=").append(ownerRef.debugField(trace.sourceType()))
                    .append(" id=").append(ownerRef.debugField(trace.sourceId()))
                    .append(" item=").append(ownerRef.debugField(trace.itemId()))
                    .append(" raw=").append(ownerRef.debugNumber(trace.rawValue()))
                    .append(" value=").append(ownerRef.debugNumber(trace.value()))
                    .append(" passed=").append(trace.conditionPassed())
                    .append(']');
        }
        return builder.isEmpty() ? "snapshot(unattributed)" : builder.toString();
    }

    Map<String, List<AttributeContributionTrace>> debugContributionIndex(LivingEntity entity) {
        if (!(entity instanceof Player player) || ownerRef.service.attributeTraceService() == null) {
            return null;
        }
        List<AttributeContributionTrace> contributions =
                ownerRef.service.attributeTraceService().trace(player, null).contributions();
        if (contributions.isEmpty()) {
            return Map.of();
        }
        Map<String, List<AttributeContributionTrace>> index = new LinkedHashMap<>();
        for (AttributeContributionTrace trace : contributions) {
            if (trace == null) {
                continue;
            }
            index.computeIfAbsent(Texts.normalizeId(trace.attributeId()), _ -> new ArrayList<>()).add(trace);
        }
        return index;
    }

    String debugEntityLabel(Entity entity) {
        if (entity == null) {
            return DamageCalculationService.DEBUG_NONE;
        }
        return Texts.toStringSafe(entity.getName())
                + "/" + entity.getType().name()
                + "/" + entity.getUniqueId();
    }

    String debugSignature(AttributeSnapshot snapshot) {
        if (snapshot == null || Texts.isBlank(snapshot.sourceSignature())) {
            return DamageCalculationService.DEBUG_NONE;
        }
        return snapshot.sourceSignature();
    }

    String debugField(String value) {
        String safe = Texts.toStringSafe(value).trim();
        return safe.isEmpty() ? DamageCalculationService.DEBUG_NONE : safe;
    }

    String debugNumber(double value) {
        return Numbers.formatNumber(value, "0.####");
    }

    String debugStageOutputs(DamageResult result) {
        if (result == null || result.stageValues().isEmpty()) {
            return DamageCalculationService.DEBUG_NONE;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Double> entry : result.stageValues().entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(Texts.toStringSafe(entry.getKey()))
                    .append(" -> ")
                    .append(ownerRef.debugNumber(entry.getValue() == null ? 0D : entry.getValue()));
        }
        return builder.isEmpty() ? DamageCalculationService.DEBUG_NONE : builder.toString();
    }

    boolean shouldDebugDamage(DamageContext damageContext) {
        if (damageContext == null
                || ownerRef.service.plugin() == null
                || ownerRef.service.plugin().debugLogger() == null) {
            return false;
        }
        Player subject = ownerRef.debugDamageSubject(damageContext);
        if (subject == null && !DebugLogger.isGlobalAllEnabled()) {
            return false;
        }
        return ownerRef.service.plugin().debugLogger().shouldLog(DamageCalculationService.DEBUG_MODULE_COMBAT, subject);
    }

    Player debugDamageSubject(DamageContext damageContext) {
        if (damageContext == null) {
            return null;
        }
        if (damageContext.attacker() instanceof Player attackerPlayer) {
            return attackerPlayer;
        }
        if (damageContext.target() instanceof Player targetPlayer) {
            return targetPlayer;
        }
        return null;
    }

    void debugDamage(DamageContext damageContext, String langKey, Map<String, ?> replacements) {
        if (damageContext == null
                || ownerRef.service.plugin() == null
                || ownerRef.service.plugin().debugLogger() == null) {
            return;
        }
        ownerRef.service.plugin().debugLogger().log(
                DamageCalculationService.DEBUG_MODULE_COMBAT,
                debugDamageSubject(damageContext),
                langKey,
                replacements
        );
    }

    boolean shouldDebugCombat(DamageContext damageContext) {
        if (damageContext == null) {
            return false;
        }
        return ownerRef.shouldDebugCombat(damageContext.attacker(), damageContext.target(), damageContext.projectile());
    }

    boolean shouldDebugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile) {
        var debugService = ownerRef.service.combatDebug();
        return projectile != null
                ? debugService.shouldTrace(projectile, target)
                : debugService.shouldTrace(attacker, target);
    }

    void debugCombat(DamageContext damageContext, String phase, String messageKey) {
        ownerRef.debugCombat(damageContext, phase, messageKey, Map.of());
    }

    void debugCombat(DamageContext damageContext, String phase, String messageKey, Map<String, ?> replacements) {
        if (damageContext == null) {
            ownerRef.debugCombat((LivingEntity) null, null, null, phase, messageKey, replacements);
            return;
        }
        ownerRef.debugCombat(damageContext.attacker(), damageContext.target(), damageContext.projectile(), phase, messageKey, replacements);
    }

    void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey) {
        ownerRef.debugCombat(attacker, target, projectile, phase, messageKey, Map.of());
    }

    void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey, Map<String, ?> replacements) {
        if (!ownerRef.shouldDebugCombat(attacker, target, projectile) || Texts.isBlank(messageKey)) {
            return;
        }
        ownerRef.service.combatDebug().logMessage(phase, messageKey, replacements);
    }

}

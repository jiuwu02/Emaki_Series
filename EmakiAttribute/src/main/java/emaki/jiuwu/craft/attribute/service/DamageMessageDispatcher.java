package emaki.jiuwu.craft.attribute.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

final class DamageMessageDispatcher {

    private final AttributeService service;
    private volatile Map<EntityDamageEvent.DamageCause, String> causeDisplayNameCache = Map.of();
    private volatile String environmentDisplayName = "environment";

    DamageMessageDispatcher(AttributeService service) {
        this.service = service;
        refreshCaches();
    }

    void refreshCaches() {
        EnumMap<EntityDamageEvent.DamageCause, String> resolved = new EnumMap<>(EntityDamageEvent.DamageCause.class);
        for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
            resolved.put(cause, resolveCauseDisplayName(cause));
        }
        causeDisplayNameCache = Map.copyOf(resolved);
        environmentDisplayName = messageOrFallback("damage.cause.environment", "environment");
    }

    void notifyDamageMessages(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        if (damageContext == null || damageType == null || result == null) {
            return;
        }
        Map<String, Object> replacements = buildDamageMessageReplacements(damageContext, damageType, result, finalDamage);
        Player attackerPlayer = damageContext.attacker() instanceof Player player ? player : null;
        Player targetPlayer = damageContext.target() instanceof Player player ? player : null;
        if (attackerPlayer != null && targetPlayer != null && attackerPlayer.getUniqueId().equals(targetPlayer.getUniqueId())) {
            sendDamageMessage(attackerPlayer, firstNonBlank(damageType.attackerMessage(), damageType.targetMessage()), replacements);
            return;
        }
        sendDamageMessage(attackerPlayer, damageType.attackerMessage(), replacements);
        sendDamageMessage(targetPlayer, damageType.targetMessage(), replacements);
    }

    String entityLabel(LivingEntity entity, EntityDamageEvent.DamageCause cause, String fallback) {
        if (entity == null) {
            return cause != null ? causeDisplayName(cause) : fallback;
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return Texts.isBlank(name) ? fallback : name;
    }

    String causeDisplayName(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return environmentDisplayName;
        }
        String cached = causeDisplayNameCache.get(cause);
        return Texts.isBlank(cached) ? resolveCauseDisplayName(cause) : cached;
    }

    private Map<String, Object> buildDamageMessageReplacements(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        Map<String, Object> replacements = new HashMap<>(40);
        String attackerLabel = entityLabel(damageContext.attacker(), damageContext.cause(), messageOrFallback("damage.environment", "environment"));
        String targetLabel = entityLabel(damageContext.target(), null, messageOrFallback("damage.target", "target"));
        String damageTypeLabel = Texts.isBlank(damageType.displayName()) ? damageType.id() : damageType.displayName();
        String sourceDamageText = Numbers.formatNumber(damageContext.sourceDamage(), "0.##");
        String baseDamageText = Numbers.formatNumber(damageContext.baseDamage(), "0.##");
        String finalDamageText = Numbers.formatNumber(finalDamage, "0.##");
        String rollText = Numbers.formatNumber(result.roll(), "0.##");
        String causeName = causeDisplayName(damageContext.cause());
        String attackerType = damageContext.attacker() == null ? causeName : damageContext.attacker().getType().name();
        String attackerUuid = damageContext.attacker() == null ? "" : damageContext.attacker().getUniqueId().toString();
        String targetType = damageContext.target() == null ? "" : damageContext.target().getType().name();
        String targetUuid = damageContext.target() == null ? "" : damageContext.target().getUniqueId().toString();
        boolean critical = result.critical();
        replacements.put("attacker", attackerLabel);
        replacements.put("attacker_name", attackerLabel);
        replacements.put("attacker_type", attackerType);
        replacements.put("attacker_uuid", attackerUuid);
        replacements.put("source", attackerLabel);
        replacements.put("source_name", attackerLabel);
        replacements.put("source_type", attackerType);
        replacements.put("source_uuid", attackerUuid);
        replacements.put("target", targetLabel);
        replacements.put("target_name", targetLabel);
        replacements.put("target_type", targetType);
        replacements.put("target_uuid", targetUuid);
        replacements.put("damage_type", damageTypeLabel);
        replacements.put("damage_type_name", damageTypeLabel);
        replacements.put("damage_type_id", damageType.id());
        replacements.put("source_damage", sourceDamageText);
        replacements.put("input_damage", sourceDamageText);
        replacements.put("base_damage", baseDamageText);
        replacements.put("final_damage", finalDamageText);
        replacements.put("damage", finalDamageText);
        replacements.put("cause", damageContext.causeName());
        replacements.put("cause_name", causeName);
        replacements.put("cause_id", damageContext.causeId());
        replacements.put("damage_cause", damageContext.causeName());
        replacements.put("damage_cause_name", causeName);
        replacements.put("damage_cause_id", damageContext.causeId());
        replacements.put("critical", critical);
        replacements.put("critical_text", critical ? messageOrFallback("damage.critical_text", "critical") : "");
        replacements.put("critical_suffix", critical ? messageOrFallback("damage.critical_suffix", " <red>critical</red>") : "");
        replacements.put("roll", rollText);
        return replacements;
    }

    private void sendDamageMessage(Player player, String template, Map<String, Object> replacements) {
        if (player == null || Texts.isBlank(template)) {
            return;
        }
        String rendered = Texts.formatTemplate(template, replacements);
        if (Texts.isBlank(rendered)) {
            return;
        }
        AdventureSupport.sendMiniMessage(service.plugin(), player, rendered);
    }

    private String resolveCauseDisplayName(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return messageOrFallback("damage.cause.environment", "environment");
        }
        return switch (cause) {
            case CONTACT -> messageOrFallback("damage.cause.contact", "contact");
            case ENTITY_ATTACK -> messageOrFallback("damage.cause.entity_attack", "attack");
            case PROJECTILE -> messageOrFallback("damage.cause.projectile", "projectile");
            case SUFFOCATION -> messageOrFallback("damage.cause.suffocation", "suffocation");
            case FALL -> messageOrFallback("damage.cause.fall", "fall");
            case FIRE -> messageOrFallback("damage.cause.fire", "fire");
            case FIRE_TICK -> messageOrFallback("damage.cause.fire_tick", "burning");
            case MELTING -> messageOrFallback("damage.cause.melting", "melting");
            case LAVA -> messageOrFallback("damage.cause.lava", "lava");
            case DROWNING -> messageOrFallback("damage.cause.drowning", "drowning");
            case BLOCK_EXPLOSION -> messageOrFallback("damage.cause.block_explosion", "block explosion");
            case ENTITY_EXPLOSION -> messageOrFallback("damage.cause.entity_explosion", "entity explosion");
            case VOID -> messageOrFallback("damage.cause.void", "void");
            case LIGHTNING -> messageOrFallback("damage.cause.lightning", "lightning");
            case WORLD_BORDER -> messageOrFallback("damage.cause.world_border", "world border");
            case STARVATION -> messageOrFallback("damage.cause.starvation", "starvation");
            case POISON -> messageOrFallback("damage.cause.poison", "poison");
            case MAGIC -> messageOrFallback("damage.cause.magic", "magic");
            case WITHER -> messageOrFallback("damage.cause.wither", "wither");
            case FALLING_BLOCK -> messageOrFallback("damage.cause.falling_block", "falling block");
            case DRAGON_BREATH -> messageOrFallback("damage.cause.dragon_breath", "dragon breath");
            case FLY_INTO_WALL -> messageOrFallback("damage.cause.fly_into_wall", "collision");
            case HOT_FLOOR -> messageOrFallback("damage.cause.hot_floor", "hot floor");
            case CAMPFIRE -> messageOrFallback("damage.cause.campfire", "campfire");
            case CRAMMING -> messageOrFallback("damage.cause.cramming", "cramming");
            case DRYOUT -> messageOrFallback("damage.cause.dryout", "dryout");
            case FREEZE -> messageOrFallback("damage.cause.freeze", "freeze");
            case SONIC_BOOM -> messageOrFallback("damage.cause.sonic_boom", "sonic boom");
            default -> messageOrFallback("damage.cause.unknown", cause.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        };
    }

    private String messageOrFallback(String key, String fallback) {
        if (service.plugin() == null || service.plugin().messageService() == null || Texts.isBlank(key)) {
            return fallback;
        }
        String value = service.plugin().messageService().message(key);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    private String firstNonBlank(String left, String right) {
        return Texts.isBlank(left) ? right : left;
    }
}

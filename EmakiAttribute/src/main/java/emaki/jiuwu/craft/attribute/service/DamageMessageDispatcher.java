package emaki.jiuwu.craft.attribute.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import net.kyori.adventure.text.Component;

import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class DamageMessageDispatcher {

    record MessageIntent(String template, Map<String, Object> replacements) {

        MessageIntent {
            template = template == null ? "" : template;
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }
    }

    record MessagePlan(MessageIntent attacker, MessageIntent target, boolean samePlayer) {
    }

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
        if (damageContext == null) {
            return;
        }
        MessagePlan plan = prepareMessages(damageContext, damageType, result, finalDamage);
        Player attackerPlayer = damageContext.attacker() instanceof Player player ? player : null;
        Player targetPlayer = damageContext.target() instanceof Player player ? player : null;
        if (plan.samePlayer()) {
            dispatch(attackerPlayer == null ? targetPlayer : attackerPlayer, plan.attacker());
            return;
        }
        dispatch(attackerPlayer, plan.attacker());
        dispatch(targetPlayer, plan.target());
    }

    MessagePlan prepareMessages(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        if (damageContext == null || damageType == null || result == null) {
            return new MessagePlan(null, null, false);
        }
        Map<String, Object> replacements = buildDamageMessageReplacements(damageContext, damageType, result, finalDamage);
        String attackerUuid = damageContext.variables().string("attacker_uuid", "");
        String targetUuid = damageContext.variables().string("target_uuid", "");
        boolean samePlayer = Texts.isNotBlank(attackerUuid) && attackerUuid.equals(targetUuid);
        if (samePlayer) {
            return new MessagePlan(
                    new MessageIntent(firstNonBlank(damageType.attackerMessage(), damageType.targetMessage()), replacements),
                    null,
                    true
            );
        }
        return new MessagePlan(
                new MessageIntent(damageType.attackerMessage(), replacements),
                new MessageIntent(damageType.targetMessage(), replacements),
                false
        );
    }

    void dispatch(Player player, MessageIntent intent) {
        if (intent != null) {
            sendDamageMessage(player, intent.template(), intent.replacements());
        }
    }

    String entityLabel(LivingEntity entity, EntityDamageEvent.DamageCause cause, String fallback) {
        if (entity == null) {
            return cause != null ? causeDisplayName(cause) : fallback;
        }
        if (entity instanceof Player player) {
            return player.getName();
        }
        String mythicName = mythicDisplayName(entity);
        if (Texts.isNotBlank(mythicName)) {
            return mythicName;
        }
        Component customName = entity.customName();
        if (customName != null) {
            String serialized = MiniMessages.serialize(customName).trim();
            if (Texts.isNotBlank(serialized)) {
                return serialized;
            }
        }
        String translationKey = entity.getType() == null ? "" : entity.getType().translationKey();
        if (Texts.isNotBlank(translationKey)) {
            return MiniMessages.serialize(Component.translatable(translationKey));
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name) && entity.getType() != null) {
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
        String attackerLabel = damageContext.variables().string(
                "attacker_name",
                damageContext.variables().string("attacker", messageOrFallback("damage.environment", "environment"))
        );
        String targetLabel = damageContext.variables().string(
                "target_name",
                damageContext.variables().string("target", messageOrFallback("damage.target", "target"))
        );
        String damageTypeLabel = Texts.isBlank(damageType.displayName()) ? damageType.id() : damageType.displayName();
        String sourceDamageText = Numbers.formatNumber(damageContext.sourceDamage(), "0.##");
        String baseDamageText = Numbers.formatNumber(damageContext.baseDamage(), "0.##");
        String finalDamageText = Numbers.formatNumber(finalDamage, "0.##");
        String rollText = Numbers.formatNumber(result.roll(), "0.##");
        String causeName = causeDisplayName(damageContext.cause());
        String attackerType = damageContext.variables().string("attacker_type", causeName);
        String attackerUuid = damageContext.variables().string("attacker_uuid", "");
        String targetType = damageContext.variables().string("target_type", "");
        String targetUuid = damageContext.variables().string("target_uuid", "");
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
        replacements.put("critical_text", critical ? messageOrFallback("damage.critical_text", "暴击") : "");
        replacements.put("critical_suffix", critical ? messageOrFallback("damage.critical_suffix", " <red>暴击</red>") : "");
        replacements.put("roll", rollText);
        double attackerHealth = damageContext.variables().getDouble("attacker_health", 0D);
        double attackerMaxHealth = damageContext.variables().getDouble("attacker_max_health", 0D);
        double targetHealth = damageContext.variables().getDouble("target_health", 0D);
        double targetMaxHealth = damageContext.variables().getDouble("target_max_health", 0D);
        replacements.put("attacker_health", Numbers.formatNumber(attackerHealth, "0.##"));
        replacements.put("attacker_max_health", Numbers.formatNumber(attackerMaxHealth, "0.##"));
        replacements.put("target_health", Numbers.formatNumber(targetHealth, "0.##"));
        replacements.put("target_max_health", Numbers.formatNumber(targetMaxHealth, "0.##"));
        replacements.put("distance", resolveDistance(damageContext));
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
        player.sendMessage(MiniMessages.parse(rendered));
    }

    private String resolveCauseDisplayName(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return messageOrFallback("damage.cause.environment", "环境");
        }
        return switch (cause) {
            case CONTACT -> messageOrFallback("damage.cause.contact", "接触");
            case ENTITY_ATTACK -> messageOrFallback("damage.cause.entity_attack", "攻击");
            case PROJECTILE -> messageOrFallback("damage.cause.projectile", "弹射物");
            case SUFFOCATION -> messageOrFallback("damage.cause.suffocation", "窒息");
            case FALL -> messageOrFallback("damage.cause.fall", "摔落");
            case FIRE -> messageOrFallback("damage.cause.fire", "火焰");
            case FIRE_TICK -> messageOrFallback("damage.cause.fire_tick", "燃烧");
            case MELTING -> messageOrFallback("damage.cause.melting", "融化");
            case LAVA -> messageOrFallback("damage.cause.lava", "岩浆");
            case DROWNING -> messageOrFallback("damage.cause.drowning", "溺水");
            case BLOCK_EXPLOSION -> messageOrFallback("damage.cause.block_explosion", "方块爆炸");
            case ENTITY_EXPLOSION -> messageOrFallback("damage.cause.entity_explosion", "爆炸");
            case VOID -> messageOrFallback("damage.cause.void", "虚空");
            case LIGHTNING -> messageOrFallback("damage.cause.lightning", "雷击");
            case WORLD_BORDER -> messageOrFallback("damage.cause.world_border", "世界边界");
            case STARVATION -> messageOrFallback("damage.cause.starvation", "饥饿");
            case POISON -> messageOrFallback("damage.cause.poison", "中毒");
            case MAGIC -> messageOrFallback("damage.cause.magic", "魔法");
            case WITHER -> messageOrFallback("damage.cause.wither", "凋零");
            case FALLING_BLOCK -> messageOrFallback("damage.cause.falling_block", "落块");
            case DRAGON_BREATH -> messageOrFallback("damage.cause.dragon_breath", "龙息");
            case FLY_INTO_WALL -> messageOrFallback("damage.cause.fly_into_wall", "碰撞");
            case HOT_FLOOR -> messageOrFallback("damage.cause.hot_floor", "高温");
            case CAMPFIRE -> messageOrFallback("damage.cause.campfire", "营火");
            case CRAMMING -> messageOrFallback("damage.cause.cramming", "挤压");
            case DRYOUT -> messageOrFallback("damage.cause.dryout", "脱水");
            case FREEZE -> messageOrFallback("damage.cause.freeze", "冻结");
            case SONIC_BOOM -> messageOrFallback("damage.cause.sonic_boom", "音爆");
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

    private String resolveDistance(DamageContext damageContext) {
        return damageContext == null
                ? "0"
                : Numbers.formatNumber(damageContext.variables().getDouble("distance", 0D), "0.##");
    }

    private String mythicDisplayName(LivingEntity entity) {
        if (entity == null) {
            return "";
        }
        MythicMobBridge.MythicMobSnapshot snapshot =
                EmakiCoreLibPlugin.lookup().mythicMobBridge().snapshot(entity);
        return snapshot == null ? "" : MiniMessages.legacyAmpersandToMiniMessage(snapshot.displayName());
    }
}

package emaki.jiuwu.craft.attribute.service;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import net.kyori.adventure.text.Component;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

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
        String mythicName = MythicMobNames.displayName(entity);
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
        replacements.put("critical_text", critical ? messageOrFallback("damage.critical_text", "critical") : "");
        replacements.put("critical_suffix", critical ? messageOrFallback("damage.critical_suffix", " <red>critical</red>") : "");
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

    private String resolveDistance(DamageContext damageContext) {
        return damageContext == null
                ? "0"
                : Numbers.formatNumber(damageContext.variables().getDouble("distance", 0D), "0.##");
    }

    private static final class MythicMobNames {

        private static volatile boolean unavailable;
        private static volatile Method instMethod;
        private static volatile Method getMobManagerMethod;
        private static volatile Method getActiveMobMethod;
        private static volatile Method getDisplayNameMethod;
        private static volatile Method activeMobGetTypeMethod;
        private static volatile Method mythicMobGetDisplayNameMethod;
        private static volatile Method placeholderGetMethod;

        private MythicMobNames() {
        }

        static String displayName(LivingEntity entity) {
            if (entity == null || unavailable || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                return "";
            }
            try {
                Object activeMob = activeMob(entity);
                if (activeMob == null) {
                    return "";
                }
                String activeName = Texts.toStringSafe(getDisplayNameMethod.invoke(activeMob)).trim();
                if (Texts.isNotBlank(activeName)) {
                    return activeName;
                }
                Object mythicMob = activeMobGetTypeMethod.invoke(activeMob);
                if (mythicMob == null) {
                    return "";
                }
                Object placeholder = mythicMobGetDisplayNameMethod.invoke(mythicMob);
                String configuredName = placeholder == null ? "" : Texts.toStringSafe(placeholderGetMethod.invoke(placeholder)).trim();
                return Texts.isBlank(configuredName) ? "" : configuredName;
            } catch (ReflectiveOperationException | LinkageError exception) {
                // The unavailable flag keeps this warning to a single emission per server run.
                unavailable = true;
                Bukkit.getLogger().warning("[EmakiAttribute] MythicMobs display-name reflection failed;"
                        + " damage messages will fall back to vanilla entity names: "
                        + describe(exception));
                return "";
            } catch (RuntimeException _) {
                return "";
            }
        }

        private static Object activeMob(LivingEntity entity) throws ReflectiveOperationException {
            ensureResolved();
            Object mythicBukkit = instMethod.invoke(null);
            Object mobManager = getMobManagerMethod.invoke(mythicBukkit);
            Object activeMob = getActiveMobMethod.invoke(mobManager, entity.getUniqueId());
            if (activeMob instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return activeMob;
        }

        private static void ensureResolved() throws ReflectiveOperationException {
            if (instMethod != null) {
                return;
            }
            synchronized (MythicMobNames.class) {
                if (instMethod != null) {
                    return;
                }
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Class<?> mobExecutorClass = Class.forName("io.lumine.mythic.core.mobs.MobExecutor");
                Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
                Class<?> mythicMobClass = Class.forName("io.lumine.mythic.api.mobs.MythicMob");
                Class<?> placeholderStringClass = Class.forName("io.lumine.mythic.api.skills.placeholders.PlaceholderString");
                instMethod = mythicBukkitClass.getMethod("inst");
                getMobManagerMethod = mythicBukkitClass.getMethod("getMobManager");
                getActiveMobMethod = mobExecutorClass.getMethod("getActiveMob", java.util.UUID.class);
                getDisplayNameMethod = activeMobClass.getMethod("getDisplayName");
                activeMobGetTypeMethod = activeMobClass.getMethod("getType");
                mythicMobGetDisplayNameMethod = mythicMobClass.getMethod("getDisplayName");
                placeholderGetMethod = placeholderStringClass.getMethod("get");
            }
        }

        private static String describe(Throwable throwable) {
            String message = throwable == null ? null : throwable.getMessage();
            return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
        }
    }
}

package emaki.jiuwu.craft.mobs.service;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ComponentMapper {

    private final Map<String, BiConsumer<LivingEntity, Object>> handlers;

    public ComponentMapper() {
        handlers = new HashMap<>();
        registerAll();
    }

    public void apply(LivingEntity entity, Map<String, Object> components) {
        if (components == null || components.isEmpty()) {
            return;
        }
        components.forEach((key, value) -> {
            var handler = handlers.get(key);
            if (handler != null && value != null) {
                handler.accept(entity, value);
            }
        });
    }

    private void registerAll() {
        registerAttribute("max_health", Attribute.MAX_HEALTH, true);
        registerAttribute("movement_speed", Attribute.MOVEMENT_SPEED, false);
        registerAttribute("attack_damage", Attribute.ATTACK_DAMAGE, false);
        registerAttribute("follow_range", Attribute.FOLLOW_RANGE, false);
        registerAttribute("knockback_resistance", Attribute.KNOCKBACK_RESISTANCE, false);
        registerAttribute("armor", Attribute.ARMOR, false);
        registerAttribute("armor_toughness", Attribute.ARMOR_TOUGHNESS, false);
        registerAttribute("attack_knockback", Attribute.ATTACK_KNOCKBACK, false);
        registerAttribute("attack_speed", Attribute.ATTACK_SPEED, false);
        registerAttribute("flying_speed", Attribute.FLYING_SPEED, false);
        handlers.put("custom_name", (entity, value) ->
                entity.customName(MiniMessage.miniMessage().deserialize(String.valueOf(value))));
        handlers.put("custom_name_visible", (entity, value) ->
                entity.setCustomNameVisible(parseBoolean(value)));
        handlers.put("silent", (entity, value) ->
                entity.setSilent(parseBoolean(value)));
        handlers.put("glowing", (entity, value) ->
                entity.setGlowing(parseBoolean(value)));
        handlers.put("no_ai", (entity, value) ->
                entity.setAI(!parseBoolean(value)));
        handlers.put("invulnerable", (entity, value) ->
                entity.setInvulnerable(parseBoolean(value)));
        handlers.put("persistent", (entity, value) ->
                entity.setPersistent(parseBoolean(value)));
        handlers.put("gravity", (entity, value) ->
                entity.setGravity(parseBoolean(value)));
        handlers.put("collidable", (entity, value) ->
                entity.setCollidable(parseBoolean(value)));
    }

    private void registerAttribute(String key, Attribute attribute, boolean fullHeal) {
        handlers.put(key, (entity, value) -> {
            var instance = entity.getAttribute(attribute);
            if (instance == null) {
                return;
            }
            instance.setBaseValue(parseDouble(value));
            if (fullHeal) {
                entity.setHealth(instance.getValue());
            }
        });
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

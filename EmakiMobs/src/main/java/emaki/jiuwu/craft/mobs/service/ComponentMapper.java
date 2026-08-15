package emaki.jiuwu.craft.mobs.service;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // === Bukkit Attributes ===
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
        registerAttribute("luck", Attribute.LUCK, false);
        registerAttribute("scale", Attribute.SCALE, false);
        registerAttribute("step_height", Attribute.STEP_HEIGHT, false);
        registerAttribute("safe_fall_distance", Attribute.SAFE_FALL_DISTANCE, false);
        registerAttribute("fall_damage_multiplier", Attribute.FALL_DAMAGE_MULTIPLIER, false);
        registerAttribute("burning_time", Attribute.BURNING_TIME, false);
        registerAttribute("explosion_knockback_resistance", Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, false);
        registerAttribute("jump_strength", Attribute.JUMP_STRENGTH, false);
        registerAttribute("spawn_reinforcements", Attribute.SPAWN_REINFORCEMENTS, false);
        registerAttribute("oxygen_bonus", Attribute.OXYGEN_BONUS, false);
        registerAttribute("water_movement_efficiency", Attribute.WATER_MOVEMENT_EFFICIENCY, false);
        registerAttribute("movement_efficiency", Attribute.MOVEMENT_EFFICIENCY, false);

        // === General behavior ===
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
        handlers.put("can_pick_up_loot", (entity, value) ->
                entity.setCanPickupItems(parseBoolean(value)));
        handlers.put("remove_when_far", (entity, value) ->
                entity.setRemoveWhenFarAway(parseBoolean(value)));
        handlers.put("absorption", (entity, value) ->
                entity.setAbsorptionAmount(parseDouble(value)));
        handlers.put("visual_fire", (entity, value) ->
                entity.setVisualFire(parseBoolean(value)));
        handlers.put("freeze_ticks", (entity, value) ->
                entity.setFreezeTicks(parseInt(value)));
        handlers.put("fire_ticks", (entity, value) ->
                entity.setFireTicks(parseInt(value)));
        handlers.put("potion_effect", (entity, value) ->
                applyPotionEffect(entity, value));

        // === Equipment ===
        handlers.put("helmet", (e, v) -> applyEquipment(e, v, EquipmentSlot.HEAD));
        handlers.put("chestplate", (e, v) -> applyEquipment(e, v, EquipmentSlot.CHEST));
        handlers.put("leggings", (e, v) -> applyEquipment(e, v, EquipmentSlot.LEGS));
        handlers.put("boots", (e, v) -> applyEquipment(e, v, EquipmentSlot.FEET));
        handlers.put("main_hand", (e, v) -> applyEquipment(e, v, EquipmentSlot.HAND));
        handlers.put("off_hand", (e, v) -> applyEquipment(e, v, EquipmentSlot.OFF_HAND));

        // === Type-specific ===
        handlers.put("is_baby", (entity, value) -> {
            boolean baby = parseBoolean(value);
            if (entity instanceof Ageable a) {
                if (baby) a.setBaby(); else a.setAdult();
            } else if (entity instanceof Zombie z) { z.setBaby(baby); }
        });
        handlers.put("can_break_doors", (entity, value) -> {
            if (entity instanceof Zombie z) z.setCanBreakDoors(parseBoolean(value));
        });
        handlers.put("slime_size", (entity, value) -> {
            if (entity instanceof Slime s) s.setSize(parseInt(value));
        });
        handlers.put("cat_type", (entity, value) -> {
            if (entity instanceof Cat cat)
                parseKeyed(Registry.CAT_VARIANT, String.valueOf(value)).ifPresent(cat::setCatType);
        });
        handlers.put("villager_profession", (entity, value) -> {
            if (entity instanceof Villager v)
                parseKeyed(Registry.VILLAGER_PROFESSION, String.valueOf(value)).ifPresent(v::setProfession);
        });
        handlers.put("villager_type", (entity, value) -> {
            if (entity instanceof Villager v)
                parseKeyed(Registry.VILLAGER_TYPE, String.valueOf(value)).ifPresent(v::setVillagerType);
        });
        handlers.put("villager_level", (entity, value) -> {
            if (entity instanceof Villager v) v.setVillagerLevel(parseInt(value));
        });
        handlers.put("horse_color", (entity, value) -> {
            if (entity instanceof Horse h)
                parseEnum(Horse.Color.class, String.valueOf(value)).ifPresent(h::setColor);
        });
        handlers.put("horse_style", (entity, value) -> {
            if (entity instanceof Horse h)
                parseEnum(Horse.Style.class, String.valueOf(value)).ifPresent(h::setStyle);
        });
        handlers.put("llama_color", (entity, value) -> {
            if (entity instanceof Llama ll)
                parseEnum(Llama.Color.class, String.valueOf(value)).ifPresent(ll::setColor);
        });
        handlers.put("llama_strength", (entity, value) -> {
            if (entity instanceof Llama ll) ll.setStrength(parseInt(value));
        });
        handlers.put("parrot_variant", (entity, value) -> {
            if (entity instanceof Parrot p)
                parseEnum(Parrot.Variant.class, String.valueOf(value)).ifPresent(p::setVariant);
        });
        handlers.put("axolotl_variant", (entity, value) -> {
            if (entity instanceof Axolotl ax)
                parseEnum(Axolotl.Variant.class, String.valueOf(value)).ifPresent(ax::setVariant);
        });
        handlers.put("fox_type", (entity, value) -> {
            if (entity instanceof Fox f)
                parseEnum(Fox.Type.class, String.valueOf(value)).ifPresent(f::setFoxType);
        });
        handlers.put("creeper_powered", (entity, value) -> {
            if (entity instanceof Creeper c) c.setPowered(parseBoolean(value));
        });
        handlers.put("creeper_explosion_radius", (entity, value) -> {
            if (entity instanceof Creeper c) c.setExplosionRadius(parseInt(value));
        });
        handlers.put("angry", (entity, value) -> {
            boolean angry = parseBoolean(value);
            if (entity instanceof Wolf w) w.setAngry(angry);
        });
        handlers.put("panda_main_gene", (entity, value) -> {
            if (entity instanceof Panda p)
                parseEnum(Panda.Gene.class, String.valueOf(value)).ifPresent(p::setMainGene);
        });
        handlers.put("panda_hidden_gene", (entity, value) -> {
            if (entity instanceof Panda p)
                parseEnum(Panda.Gene.class, String.valueOf(value)).ifPresent(p::setHiddenGene);
        });
        handlers.put("frog_variant", (entity, value) -> {
            if (entity instanceof Frog f)
                parseKeyed(Registry.FROG_VARIANT, String.valueOf(value)).ifPresent(f::setVariant);
        });
        handlers.put("mushroom_type", (entity, value) -> {
            if (entity instanceof MushroomCow mc)
                parseEnum(MushroomCow.Variant.class, String.valueOf(value)).ifPresent(mc::setVariant);
        });
        handlers.put("rabbit_type", (entity, value) -> {
            if (entity instanceof Rabbit r)
                parseEnum(Rabbit.Type.class, String.valueOf(value)).ifPresent(r::setRabbitType);
        });
    }

    private void registerAttribute(String key, Attribute attribute, boolean fullHeal) {
        handlers.put(key, (entity, value) -> {
            var instance = entity.getAttribute(attribute);
            if (instance == null) return;
            instance.setBaseValue(parseDouble(value));
            if (fullHeal) entity.setHealth(instance.getValue());
        });
    }

    private static void applyEquipment(LivingEntity entity, Object value, EquipmentSlot slot) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        Material mat = Material.matchMaterial(String.valueOf(value).toUpperCase());
        if (mat == null || !mat.isItem()) return;
        eq.setItem(slot, new ItemStack(mat));
    }

    private static void applyPotionEffect(LivingEntity entity, Object value) {
        if (value instanceof List<?> list) {
            list.forEach(v -> parsePotionEffect(String.valueOf(v)).ifPresent(entity::addPotionEffect));
        } else {
            parsePotionEffect(String.valueOf(value)).ifPresent(entity::addPotionEffect);
        }
    }

    private static Optional<PotionEffect> parsePotionEffect(String spec) {
        String[] parts = spec.split(":");
        if (parts.length < 2) return Optional.empty();
        var type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase().trim()));
        if (type == null) return Optional.empty();
        try {
            int duration = Integer.parseInt(parts[1].trim());
            int amplifier = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 0;
            return Optional.of(new PotionEffect(type, duration, amplifier));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static <T extends Enum<T>> Optional<T> parseEnum(Class<T> cls, String value) {
        try {
            return Optional.of(Enum.valueOf(cls, value.toUpperCase().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static <T extends Keyed> Optional<T> parseKeyed(Registry<T> registry, String value) {
        String v = value.toLowerCase().trim();
        NamespacedKey key = v.contains(":") ? NamespacedKey.fromString(v) : NamespacedKey.minecraft(v);
        if (key == null) return Optional.empty();
        return Optional.ofNullable(registry.get(key));
    }


    private static double parseDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

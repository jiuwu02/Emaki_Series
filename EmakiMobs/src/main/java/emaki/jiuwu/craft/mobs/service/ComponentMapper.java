package emaki.jiuwu.craft.mobs.service;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
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

    private enum RefreshMode {
        ALWAYS,
        SPAWN_ONLY,
        MAX_HEALTH_PRESERVE_CURRENT
    }

    private record ComponentBinding(BiConsumer<LivingEntity, Object> handler, RefreshMode refreshMode) { }

    private static final String MAX_HEALTH_KEY = "max_health";

    private final Map<String, ComponentBinding> handlers;
    private final MobIdentifier mobIdentifier;

    public ComponentMapper(MobIdentifier mobIdentifier) {
        this.mobIdentifier = mobIdentifier;
        handlers = new HashMap<>();
        registerAll();
    }

    public void applyForSpawn(LivingEntity entity, Map<String, Object> components) {
        apply(entity, components, false);
    }

    public void applyForRefresh(LivingEntity entity, Map<String, Object> components) {
        apply(entity, components, true);
    }

    public void fillHealth(LivingEntity entity, Map<String, Object> components) {
        if (components == null || !components.containsKey(MAX_HEALTH_KEY)) {
            return;
        }
        if (!entity.isValid() || entity.isDead()) {
            return;
        }
        var instance = entity.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null || instance.getValue() <= 0) {
            return;
        }
        entity.setHealth(instance.getValue());
    }

    private void apply(LivingEntity entity, Map<String, Object> components, boolean refresh) {
        if (components == null || components.isEmpty()) {
            return;
        }
        components.forEach((key, value) -> {
            ComponentBinding binding = handlers.get(key);
            if (binding == null || value == null) {
                return;
            }
            if (refresh && binding.refreshMode() == RefreshMode.SPAWN_ONLY) {
                return;
            }
            if (refresh && binding.refreshMode() == RefreshMode.MAX_HEALTH_PRESERVE_CURRENT) {
                applyMaxHealthPreservingCurrent(entity, value);
                return;
            }
            binding.handler().accept(entity, value);
        });
    }

    private void applyMaxHealthPreservingCurrent(LivingEntity entity, Object value) {
        var instance = entity.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double oldHealth = entity.getHealth();
        instance.setBaseValue(parseDouble(value));
        double newMax = instance.getValue();
        if (newMax <= 0) {
            return;
        }
        entity.setHealth(Math.min(oldHealth, newMax));
    }

    private void registerAll() {
        registerBukkitAttributes();
        registerGeneralBehavior();
        registerEquipment();
        registerTypeSpecific();
    }

    private void registerBukkitAttributes() {
        registerAttribute(MAX_HEALTH_KEY, Attribute.MAX_HEALTH, RefreshMode.MAX_HEALTH_PRESERVE_CURRENT);
        registerAttribute("movement_speed", Attribute.MOVEMENT_SPEED, RefreshMode.ALWAYS);
        registerAttribute("attack_damage", Attribute.ATTACK_DAMAGE, RefreshMode.ALWAYS);
        registerAttribute("follow_range", Attribute.FOLLOW_RANGE, RefreshMode.ALWAYS);
        registerAttribute("knockback_resistance", Attribute.KNOCKBACK_RESISTANCE, RefreshMode.ALWAYS);
        registerAttribute("armor", Attribute.ARMOR, RefreshMode.ALWAYS);
        registerAttribute("armor_toughness", Attribute.ARMOR_TOUGHNESS, RefreshMode.ALWAYS);
        registerAttribute("attack_knockback", Attribute.ATTACK_KNOCKBACK, RefreshMode.ALWAYS);
        registerAttribute("attack_speed", Attribute.ATTACK_SPEED, RefreshMode.ALWAYS);
        registerAttribute("flying_speed", Attribute.FLYING_SPEED, RefreshMode.ALWAYS);
        registerAttribute("luck", Attribute.LUCK, RefreshMode.ALWAYS);
        registerAttribute("scale", Attribute.SCALE, RefreshMode.ALWAYS);
        registerAttribute("step_height", Attribute.STEP_HEIGHT, RefreshMode.ALWAYS);
        registerAttribute("safe_fall_distance", Attribute.SAFE_FALL_DISTANCE, RefreshMode.ALWAYS);
        registerAttribute("fall_damage_multiplier", Attribute.FALL_DAMAGE_MULTIPLIER, RefreshMode.ALWAYS);
        registerAttribute("burning_time", Attribute.BURNING_TIME, RefreshMode.ALWAYS);
        registerAttribute("explosion_knockback_resistance", Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, RefreshMode.ALWAYS);
        registerAttribute("jump_strength", Attribute.JUMP_STRENGTH, RefreshMode.ALWAYS);
        registerAttribute("spawn_reinforcements", Attribute.SPAWN_REINFORCEMENTS, RefreshMode.ALWAYS);
        registerAttribute("oxygen_bonus", Attribute.OXYGEN_BONUS, RefreshMode.ALWAYS);
        registerAttribute("water_movement_efficiency", Attribute.WATER_MOVEMENT_EFFICIENCY, RefreshMode.ALWAYS);
        registerAttribute("movement_efficiency", Attribute.MOVEMENT_EFFICIENCY, RefreshMode.ALWAYS);
    }

    private void registerGeneralBehavior() {
        register("custom_name", RefreshMode.ALWAYS, (entity, value) ->
                entity.customName(MiniMessage.miniMessage().deserialize(String.valueOf(value))));
        register("custom_name_visible", RefreshMode.ALWAYS, (entity, value) ->
                entity.setCustomNameVisible(parseBoolean(value)));
        register("silent", RefreshMode.ALWAYS, (entity, value) ->
                entity.setSilent(parseBoolean(value)));
        register("glowing", RefreshMode.ALWAYS, (entity, value) ->
                entity.setGlowing(parseBoolean(value)));
        register("no_ai", RefreshMode.ALWAYS, (entity, value) ->
                entity.setAI(!parseBoolean(value)));
        register("invulnerable", RefreshMode.ALWAYS, (entity, value) ->
                entity.setInvulnerable(parseBoolean(value)));
        register("persistent", RefreshMode.ALWAYS, (entity, value) ->
                entity.setPersistent(parseBoolean(value)));
        register("gravity", RefreshMode.ALWAYS, (entity, value) ->
                entity.setGravity(parseBoolean(value)));
        register("collidable", RefreshMode.ALWAYS, (entity, value) ->
                entity.setCollidable(parseBoolean(value)));
        register("can_pick_up_loot", RefreshMode.ALWAYS, (entity, value) ->
                entity.setCanPickupItems(parseBoolean(value)));
        register("remove_when_far", RefreshMode.ALWAYS, (entity, value) ->
                entity.setRemoveWhenFarAway(parseBoolean(value)));
        register("absorption", RefreshMode.SPAWN_ONLY, (entity, value) ->
                entity.setAbsorptionAmount(parseDouble(value)));
        register("visual_fire", RefreshMode.ALWAYS, (entity, value) ->
                entity.setVisualFire(parseBoolean(value)));
        register("freeze_ticks", RefreshMode.SPAWN_ONLY, (entity, value) ->
                entity.setFreezeTicks(parseInt(value)));
        register("fire_ticks", RefreshMode.SPAWN_ONLY, (entity, value) ->
                entity.setFireTicks(parseInt(value)));
        register("fire_immune", RefreshMode.ALWAYS, (entity, value) ->
                mobIdentifier.setFireImmune(entity, parseBoolean(value)));
        register("loot_table", RefreshMode.ALWAYS, (entity, value) -> {
            String s = String.valueOf(value);
            NamespacedKey key = s.contains(":") ? NamespacedKey.fromString(s) : NamespacedKey.minecraft(s);
            if (key == null) return;
            LootTable lt = Bukkit.getLootTable(key);
            if (lt != null && entity instanceof Mob mob) mob.setLootTable(lt);
        });
        register("potion_effect", RefreshMode.SPAWN_ONLY, (entity, value) ->
                applyPotionEffect(entity, value));
    }

    private void registerEquipment() {
        register("helmet", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.HEAD));
        register("chestplate", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.CHEST));
        register("leggings", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.LEGS));
        register("boots", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.FEET));
        register("main_hand", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.HAND));
        register("off_hand", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipment(e, v, EquipmentSlot.OFF_HAND));
        register("equipment", RefreshMode.SPAWN_ONLY, (e, v) -> applyEquipmentMap(e, v));
    }

    private void registerTypeSpecific() {
        register("is_baby", RefreshMode.ALWAYS, (entity, value) -> {
            boolean baby = parseBoolean(value);
            if (entity instanceof Ageable a) {
                if (baby) a.setBaby(); else a.setAdult();
            } else if (entity instanceof Zombie z) { z.setBaby(baby); }
        });
        register("can_break_doors", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Zombie z) z.setCanBreakDoors(parseBoolean(value));
        });
        register("slime_size", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Slime s) s.setSize(parseInt(value));
        });
        register("cat_type", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Cat cat)
                parseKeyed(Registry.CAT_VARIANT, String.valueOf(value)).ifPresent(cat::setCatType);
        });
        register("villager_profession", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Villager v)
                parseKeyed(Registry.VILLAGER_PROFESSION, String.valueOf(value)).ifPresent(v::setProfession);
        });
        register("villager_type", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Villager v)
                parseKeyed(Registry.VILLAGER_TYPE, String.valueOf(value)).ifPresent(v::setVillagerType);
        });
        register("villager_level", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Villager v) v.setVillagerLevel(parseInt(value));
        });
        register("horse_color", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Horse h)
                parseEnum(Horse.Color.class, String.valueOf(value)).ifPresent(h::setColor);
        });
        register("horse_style", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Horse h)
                parseEnum(Horse.Style.class, String.valueOf(value)).ifPresent(h::setStyle);
        });
        register("llama_color", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Llama ll)
                parseEnum(Llama.Color.class, String.valueOf(value)).ifPresent(ll::setColor);
        });
        register("llama_strength", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Llama ll) ll.setStrength(parseInt(value));
        });
        register("parrot_variant", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Parrot p)
                parseEnum(Parrot.Variant.class, String.valueOf(value)).ifPresent(p::setVariant);
        });
        register("axolotl_variant", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Axolotl ax)
                parseEnum(Axolotl.Variant.class, String.valueOf(value)).ifPresent(ax::setVariant);
        });
        register("fox_type", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Fox f)
                parseEnum(Fox.Type.class, String.valueOf(value)).ifPresent(f::setFoxType);
        });
        register("creeper_powered", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Creeper c) c.setPowered(parseBoolean(value));
        });
        register("creeper_explosion_radius", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Creeper c) c.setExplosionRadius(parseInt(value));
        });
        register("angry", RefreshMode.ALWAYS, (entity, value) -> {
            boolean angry = parseBoolean(value);
            if (entity instanceof Wolf w) w.setAngry(angry);
        });
        register("panda_main_gene", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Panda p)
                parseEnum(Panda.Gene.class, String.valueOf(value)).ifPresent(p::setMainGene);
        });
        register("panda_hidden_gene", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Panda p)
                parseEnum(Panda.Gene.class, String.valueOf(value)).ifPresent(p::setHiddenGene);
        });
        register("frog_variant", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Frog f)
                parseKeyed(Registry.FROG_VARIANT, String.valueOf(value)).ifPresent(f::setVariant);
        });
        register("mushroom_type", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof MushroomCow mc)
                parseEnum(MushroomCow.Variant.class, String.valueOf(value)).ifPresent(mc::setVariant);
        });
        register("rabbit_type", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Rabbit r)
                parseEnum(Rabbit.Type.class, String.valueOf(value)).ifPresent(r::setRabbitType);
        });
        register("wolf_variant", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Wolf w)
                parseKeyed(Registry.WOLF_VARIANT, String.valueOf(value)).ifPresent(w::setVariant);
        });
        register("phantom_size", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Phantom p) p.setSize(parseInt(value));
        });
        register("goat_screaming", RefreshMode.ALWAYS, (entity, value) -> {
            if (entity instanceof Goat g) g.setScreaming(parseBoolean(value));
        });
    }

    private void registerAttribute(String key, Attribute attribute, RefreshMode refreshMode) {
        register(key, refreshMode, (entity, value) -> {
            var instance = entity.getAttribute(attribute);
            if (instance == null) return;
            instance.setBaseValue(parseDouble(value));
        });
    }

    private void register(String key, RefreshMode refreshMode, BiConsumer<LivingEntity, Object> handler) {
        handlers.put(key, new ComponentBinding(handler, refreshMode));
    }

    private static void applyEquipmentMap(LivingEntity entity, Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) return;
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        rawMap.forEach((slotKey, slotVal) -> {
            EquipmentSlot slot = parseEquipmentSlot(String.valueOf(slotKey));
            if (slot == null) return;
            if (slotVal instanceof Map<?, ?> slotMap) {
                Object itemVal = slotMap.get("item");
                Object dropChanceVal = slotMap.get("drop_chance");
                if (itemVal != null) {
                    Material mat = Material.matchMaterial(String.valueOf(itemVal).toUpperCase());
                    if (mat != null && mat.isItem()) eq.setItem(slot, new ItemStack(mat));
                }
                if (dropChanceVal != null) {
                    float chance = (float) parseDouble(dropChanceVal);
                    setSlotDropChance(eq, slot, chance);
                }
            } else {
                Material mat = Material.matchMaterial(String.valueOf(slotVal).toUpperCase());
                if (mat != null && mat.isItem()) eq.setItem(slot, new ItemStack(mat));
            }
        });
    }

    private static EquipmentSlot parseEquipmentSlot(String name) {
        return switch (name.toLowerCase().trim()) {
            case "main_hand", "hand" -> EquipmentSlot.HAND;
            case "off_hand" -> EquipmentSlot.OFF_HAND;
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private static void setSlotDropChance(EntityEquipment eq, EquipmentSlot slot, float chance) {
        switch (slot) {
            case HAND -> eq.setItemInHandDropChance(chance);
            case OFF_HAND -> eq.setItemInOffHandDropChance(chance);
            case HEAD -> eq.setHelmetDropChance(chance);
            case CHEST -> eq.setChestplateDropChance(chance);
            case LEGS -> eq.setLeggingsDropChance(chance);
            case FEET -> eq.setBootsDropChance(chance);
        }
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

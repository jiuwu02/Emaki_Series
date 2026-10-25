package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Tameable;

public final class TargetFactsReader {

    public static final String STATE_SNEAKING = "sneaking";
    public static final String STATE_SPRINTING = "sprinting";
    public static final String STATE_FLYING = "flying";
    public static final String STATE_GLIDING = "gliding";
    public static final String STATE_SWIMMING = "swimming";
    public static final String STATE_IN_WATER = "in_water";
    public static final String STATE_ON_GROUND = "on_ground";

    private static final List<String> KNOWN_STATES = List.of(
            STATE_SNEAKING, STATE_SPRINTING, STATE_FLYING, STATE_GLIDING,
            STATE_SWIMMING, STATE_IN_WATER, STATE_ON_GROUND);

    private final TargetIdentityRegistry identities;

    public TargetFactsReader(TargetIdentityRegistry identities) {
        this.identities = identities == null ? new TargetIdentityRegistry() : identities;
    }

    public static List<String> knownStates() {
        return KNOWN_STATES;
    }

    public static final String FLAG_ON_FIRE = "on_fire";
    public static final String FLAG_INVULNERABLE = "invulnerable";
    public static final String FLAG_SILENT = "silent";
    public static final String FLAG_GLOWING = "glowing";
    public static final String FLAG_HAS_GRAVITY = "has_gravity";
    public static final String FLAG_TAMED = "tamed";
    public static final String FLAG_BABY = "baby";
    public static final String FLAG_LEASHED = "leashed";
    public static final String FLAG_HAS_AI = "has_ai";
    public static final String FLAG_BREATHING = "breathing";

    private static final List<String> KNOWN_FLAGS = List.of(
            FLAG_ON_FIRE, FLAG_INVULNERABLE, FLAG_SILENT, FLAG_GLOWING,
            FLAG_HAS_GRAVITY, FLAG_TAMED, FLAG_BABY, FLAG_LEASHED,
            FLAG_HAS_AI, FLAG_BREATHING);

    public static List<String> knownFlags() {
        return KNOWN_FLAGS;
    }

    public TargetFacts read(CoreActionSubject subject, Location origin) {
        Entity entity = subject == null ? null : subject.entityOrNull();
        if (entity == null) {
            return TargetFacts.absent();
        }
        LivingEntity living = entity instanceof LivingEntity livingEntity ? livingEntity : null;
        Player player = entity instanceof Player playerEntity ? playerEntity : null;
        return new TargetFacts(
                true,
                living != null,
                player != null,
                entity.isDead(),
                entity.getType().name(),
                entity.getWorld() == null ? "" : entity.getWorld().getName(),
                Texts.toStringSafe(entity.getName()),
                living == null ? 0D : living.getHealth(),
                living == null ? 0D : living.getMaxHealth(),
                distance(entity.getLocation(), origin),
                player == null ? 0 : player.getLevel(),
                player == null ? 0 : player.getFoodLevel(),
                player == null ? "" : player.getGameMode().name().toLowerCase(Locale.ROOT),
                entity.getScoreboardTags(),
                effects(living),
                states(entity, player),
                identities.systemIds(),
                identities.identifyAll(living),
                player == null ? node -> null : player::hasPermission,
                entity.getWorld() == null ? 0L : entity.getWorld().getTime(),
                flags(entity, living),
                entity.getLocation().getChunk().getX(),
                entity.getLocation().getChunk().getZ(),
                weather(entity),
                slotEmpties(player));
    }

    private static String weather(Entity entity) {
        if (entity.getWorld() == null) {
            return "";
        }
        if (entity.getWorld().hasStorm()) {
            return entity.getWorld().isThundering() ? "thunder" : "rain";
        }
        return "clear";
    }

    private static Map<Integer, Boolean> slotEmpties(Player player) {
        if (player == null) {
            return Map.of();
        }
        Map<Integer, Boolean> empties = new LinkedHashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            empties.put(slot, player.getInventory().getItem(slot) == null);
        }
        return empties;
    }

    private static Map<String, Boolean> flags(Entity entity, LivingEntity living) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put(FLAG_ON_FIRE, entity.getFireTicks() > 0);
        flags.put(FLAG_INVULNERABLE, entity.isInvulnerable());
        flags.put(FLAG_SILENT, entity.isSilent());
        flags.put(FLAG_GLOWING, entity.isGlowing());
        flags.put(FLAG_HAS_GRAVITY, entity.hasGravity());
        if (living != null) {
            flags.put(FLAG_HAS_AI, living.hasAI());
            flags.put(FLAG_BREATHING, living.getRemainingAir() > 0);
            flags.put(FLAG_LEASHED, living.isLeashed());
        }
        if (entity instanceof Tameable tameable) {
            flags.put(FLAG_TAMED, tameable.isTamed());
        }
        if (entity instanceof Ageable ageable) {
            flags.put(FLAG_BABY, !ageable.isAdult());
        }
        return flags;
    }

    private static Map<String, Integer> effects(LivingEntity living) {
        if (living == null) {
            return Map.of();
        }
        Map<String, Integer> effects = new LinkedHashMap<>();
        for (PotionEffect effect : living.getActivePotionEffects()) {
            PotionEffectType type = effect.getType();
            if (type == null || type.getKey() == null) {
                continue;
            }
            effects.put(type.getKey().toString(), effect.getAmplifier());
        }
        return effects;
    }

    private static Map<String, Boolean> states(Entity entity, Player player) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        states.put(STATE_SNEAKING, entity.isSneaking());
        states.put(STATE_IN_WATER, entity.isInWater());
        states.put(STATE_ON_GROUND, entity.isOnGround());
        if (player != null) {
            states.put(STATE_SPRINTING, player.isSprinting());
            states.put(STATE_FLYING, player.isFlying());
            states.put(STATE_GLIDING, player.isGliding());
            states.put(STATE_SWIMMING, player.isSwimming());
            return states;
        }
        if (entity instanceof LivingEntity living) {
            states.put(STATE_GLIDING, living.isGliding());
            states.put(STATE_SWIMMING, living.isSwimming());
        }
        return states;
    }

    private static double distance(Location from, Location origin) {
        if (from == null || origin == null || from.getWorld() == null || origin.getWorld() == null
                || !from.getWorld().equals(origin.getWorld())) {
            return -1D;
        }
        return from.distance(origin);
    }
}

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
                player == null ? node -> null : player::hasPermission);
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
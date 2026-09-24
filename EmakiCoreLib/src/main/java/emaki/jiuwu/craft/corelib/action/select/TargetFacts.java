package emaki.jiuwu.craft.corelib.action.select;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;

public record TargetFacts(
        boolean present,
        boolean living,
        boolean player,
        boolean dead,
        String entityType,
        String worldName,
        String plainName,
        double health,
        double maxHealth,
        double distance,
        int experienceLevel,
        int foodLevel,
        String gameMode,
        Set<String> scoreboardTags,
        Map<String, Integer> potionEffects,
        Map<String, Boolean> states,
        Set<String> identitySystems,
        Map<String, CoreTargetIdentity> identities,
        Function<String, Boolean> permissions) {

    public TargetFacts {
        entityType = entityType == null ? "" : entityType;
        worldName = worldName == null ? "" : worldName;
        plainName = plainName == null ? "" : plainName;
        gameMode = gameMode == null ? "" : gameMode;
        scoreboardTags = scoreboardTags == null ? Set.of() : Set.copyOf(scoreboardTags);
        potionEffects = potionEffects == null ? Map.of() : Map.copyOf(potionEffects);
        states = states == null ? Map.of() : Map.copyOf(states);
        identitySystems = identitySystems == null ? Set.of() : Set.copyOf(identitySystems);
        identities = identities == null ? Map.of() : Map.copyOf(identities);
        permissions = permissions == null ? node -> null : permissions;
    }

    public static TargetFacts absent() {
        return new TargetFacts(false, false, false, true, "", "", "",
                0D, 0D, -1D, 0, 0, "", Set.of(), Map.of(), Map.of(), Set.of(), Map.of(), node -> null);
    }

    public double healthPercent() {
        return maxHealth <= 0D ? 0D : health / maxHealth * 100D;
    }

    public boolean entityPresent() {
        return present && living;
    }

    public Integer potionAmplifier(String effectKey) {
        return effectKey == null ? null : potionEffects.get(effectKey);
    }

    public Boolean state(String key) {
        return key == null ? null : states.get(key);
    }

    public CoreTargetIdentity identity(String systemId) {
        return systemId == null ? null : identities.get(systemId);
    }

    public boolean identitySystemKnown(String systemId) {
        return systemId != null && identitySystems.contains(systemId);
    }
}
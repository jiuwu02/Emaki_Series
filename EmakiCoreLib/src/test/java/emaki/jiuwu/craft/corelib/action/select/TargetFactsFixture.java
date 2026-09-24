package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;

final class TargetFactsFixture {

    boolean present = true;
    boolean living = true;
    boolean player;
    boolean dead;
    String entityType = "ZOMBIE";
    String worldName = "world";
    String plainName = "Test";
    double health = 20D;
    double maxHealth = 20D;
    double distance = -1D;
    int level;
    int food = 20;
    String gameMode = "";
    Set<String> tags = Set.of();
    Map<String, Integer> effects = Map.of();
    Map<String, Boolean> states = Map.of();
    Set<String> identitySystems = Set.of();
    Map<String, CoreTargetIdentity> identities = Map.of();
    Function<String, Boolean> permissions = node -> null;

    static TargetFactsFixture mob() {
        return new TargetFactsFixture();
    }

    static TargetFactsFixture player() {
        TargetFactsFixture fixture = new TargetFactsFixture();
        fixture.player = true;
        fixture.entityType = "PLAYER";
        fixture.plainName = "Steve";
        fixture.gameMode = "survival";
        fixture.permissions = node -> Boolean.FALSE;
        return fixture;
    }

    static TargetFactsFixture absent() {
        TargetFactsFixture fixture = new TargetFactsFixture();
        fixture.present = false;
        fixture.living = false;
        fixture.dead = true;
        fixture.entityType = "";
        return fixture;
    }

    TargetFactsFixture health(double current, double maximum) {
        health = current;
        maxHealth = maximum;
        return this;
    }

    TargetFactsFixture level(int value) {
        level = value;
        return this;
    }

    TargetFactsFixture food(int value) {
        food = value;
        return this;
    }

    TargetFactsFixture gameMode(String value) {
        gameMode = value;
        return this;
    }

    TargetFactsFixture distance(double value) {
        distance = value;
        return this;
    }

    TargetFactsFixture type(String value) {
        entityType = value;
        return this;
    }

    TargetFactsFixture world(String value) {
        worldName = value;
        return this;
    }

    TargetFactsFixture tags(String... values) {
        tags = new LinkedHashSet<>(Set.of(values));
        return this;
    }

    TargetFactsFixture effect(String key, int amplifier) {
        Map<String, Integer> updated = new LinkedHashMap<>(effects);
        updated.put(key, amplifier);
        effects = Map.copyOf(updated);
        return this;
    }

    TargetFactsFixture state(String key, boolean value) {
        Map<String, Boolean> updated = new LinkedHashMap<>(states);
        updated.put(key, value);
        states = Map.copyOf(updated);
        return this;
    }

    TargetFactsFixture identity(String systemId, String mobId) {
        return identity(systemId, mobId, 0D);
    }

    TargetFactsFixture identity(String systemId, String mobId, double mobLevel) {
        Set<String> systems = new LinkedHashSet<>(identitySystems);
        systems.add(systemId);
        Map<String, CoreTargetIdentity> updated = new LinkedHashMap<>(identities);
        updated.put(systemId, new CoreTargetIdentity(systemId, mobId, mobLevel));
        identitySystems = Set.copyOf(systems);
        identities = Map.copyOf(updated);
        return this;
    }

    TargetFactsFixture knownSystem(String systemId) {
        Set<String> systems = new LinkedHashSet<>(identitySystems);
        systems.add(systemId);
        identitySystems = Set.copyOf(systems);
        return this;
    }

    TargetFactsFixture permission(String node, boolean granted) {
        Function<String, Boolean> previous = permissions;
        permissions = candidate -> node.equals(candidate) ? Boolean.valueOf(granted) : previous.apply(candidate);
        return this;
    }

    TargetFacts build() {
        return new TargetFacts(present, living, player, dead, entityType, worldName, plainName,
                health, maxHealth, distance, level, food, gameMode, tags, effects, states,
                identitySystems, identities, permissions);
    }
}
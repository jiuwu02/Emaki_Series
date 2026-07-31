package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the search that the v1 {@code KillEntityAction} carried inside its own body.
 *
 * <p>These are the rules a server owner depends on when writing {@code nearby radius=5 limit=3}, so the migration
 * has to preserve them exactly: dead entities excluded, players excluded unless asked for, type filter honoured,
 * nearest first, then truncated.</p>
 *
 * <p>Temporary asset for phase 3 verification.</p>
 */
class NearbyFilterTest {

    private static final World WORLD = world("test_world");

    private static World world(String name) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getName", "toString" -> name;
            case "getUID" -> UUID.nameUUIDFromBytes(name.getBytes());
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> name.hashCode();
            default -> throw new UnsupportedOperationException("world." + method.getName());
        };
        return (World) Proxy.newProxyInstance(NearbyFilterTest.class.getClassLoader(),
                new Class<?>[] {World.class}, handler);
    }

    /**
     * Builds a proxied entity.
     *
     * @param name diagnostic name
     * @param distance distance from the origin along X
     * @param type reported entity type
     * @param dead whether the entity reports itself dead
     * @param player whether the proxy also implements {@code Player}
     * @return the entity
     */
    private static Entity entity(String name, double distance, EntityType type, boolean dead, boolean player) {
        Location location = new Location(WORLD, distance, 0D, 0D);
        UUID id = UUID.randomUUID();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getLocation" -> location.clone();
            case "getType" -> type;
            case "isDead" -> dead;
            case "isValid" -> !dead;
            case "getUniqueId" -> id;
            case "getName", "toString" -> name;
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> id.hashCode();
            default -> throw new UnsupportedOperationException("entity." + method.getName());
        };
        Class<?>[] interfaces = player ? new Class<?>[] {Player.class} : new Class<?>[] {Entity.class};
        return (Entity) Proxy.newProxyInstance(NearbyFilterTest.class.getClassLoader(), interfaces, handler);
    }

    private static Entity mob(String name, double distance) {
        return entity(name, distance, EntityType.ZOMBIE, false, false);
    }

    private static Location origin() {
        return new Location(WORLD, 0D, 0D, 0D);
    }

    private static List<String> names(List<Entity> entities) {
        List<String> names = new ArrayList<>(entities.size());
        entities.forEach(entity -> names.add(entity.getName()));
        return names;
    }

    @Test
    void ordersByDistanceNearestFirst() {
        List<Entity> candidates = List.of(mob("far", 10D), mob("near", 1D), mob("mid", 5D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, false, 10, false);

        assertEquals(List.of("near", "mid", "far"), names(result));
    }

    @Test
    void truncatesToTheLimitAfterOrdering() {
        // Order then truncate, not the reverse: `limit 1` has to mean the closest one, not an arbitrary one.
        List<Entity> candidates = List.of(mob("far", 10D), mob("near", 1D), mob("mid", 5D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, false, 1, false);

        assertEquals(List.of("near"), names(result));
    }

    @Test
    void excludesDeadEntities() {
        List<Entity> candidates = List.of(
                entity("dead", 1D, EntityType.ZOMBIE, true, false),
                mob("alive", 5D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, false, 10, false);

        assertEquals(List.of("alive"), names(result));
    }

    @Test
    void excludesPlayersByDefault() {
        List<Entity> candidates = List.of(
                entity("player", 1D, EntityType.PLAYER, false, true),
                mob("mob", 5D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, false, 10, false);

        assertEquals(List.of("mob"), names(result));
    }

    @Test
    void includesPlayersWhenAsked() {
        List<Entity> candidates = List.of(
                entity("player", 1D, EntityType.PLAYER, false, true),
                mob("mob", 5D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, true, 10, false);

        assertEquals(List.of("player", "mob"), names(result));
    }

    @Test
    void honoursTheTypeFilter() {
        List<Entity> candidates = List.of(
                entity("zombie", 1D, EntityType.ZOMBIE, false, false),
                entity("skeleton", 2D, EntityType.SKELETON, false, false));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), EntityType.SKELETON, false, 10, false);

        assertEquals(List.of("skeleton"), names(result));
    }

    @Test
    void playersOnlyModeKeepsOnlyPlayers() {
        // This is the single difference between `nearby` and `nearby_players`; the rest of the chain is shared.
        List<Entity> candidates = List.of(
                entity("player", 5D, EntityType.PLAYER, false, true),
                mob("mob", 1D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, true, 10, true);

        assertEquals(List.of("player"), names(result));
    }

    @Test
    void playersOnlyModeStillExcludesDeadPlayers() {
        List<Entity> candidates = List.of(
                entity("ghost", 1D, EntityType.PLAYER, true, true),
                entity("live", 5D, EntityType.PLAYER, false, true));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, true, 10, true);

        assertEquals(List.of("live"), names(result));
    }

    @Test
    void treatsALimitBelowOneAsOne() {
        List<Entity> candidates = List.of(mob("near", 1D), mob("far", 9D));

        assertEquals(1, NearbyFilter.apply(candidates, origin(), null, false, 0, false).size());
        assertEquals(1, NearbyFilter.apply(candidates, origin(), null, false, -5, false).size());
    }

    @Test
    void returnsEmptyForNoCandidatesOrNoOrigin() {
        assertTrue(NearbyFilter.apply(List.of(), origin(), null, false, 10, false).isEmpty());
        assertTrue(NearbyFilter.apply(null, origin(), null, false, 10, false).isEmpty());
        assertTrue(NearbyFilter.apply(List.of(mob("a", 1D)), null, null, false, 10, false).isEmpty());
    }

    @Test
    void skipsNullCandidatesWithoutFailing() {
        List<Entity> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(mob("real", 1D));

        assertEquals(List.of("real"), names(NearbyFilter.apply(candidates, origin(), null, false, 10, false)));
    }

    @Test
    void sortsEntitiesFromAnotherWorldLast() {
        // The comparator has to stay total. An entity that cannot be measured against the origin sorts last
        // rather than throwing out of the sort.
        Location otherWorld = new Location(world("elsewhere"), 0D, 0D, 0D);
        UUID id = UUID.randomUUID();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getLocation" -> otherWorld.clone();
            case "getType" -> EntityType.ZOMBIE;
            case "isDead" -> false;
            case "getUniqueId" -> id;
            case "getName", "toString" -> "elsewhere";
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> id.hashCode();
            default -> throw new UnsupportedOperationException("entity." + method.getName());
        };
        Entity remote = (Entity) Proxy.newProxyInstance(NearbyFilterTest.class.getClassLoader(),
                new Class<?>[] {Entity.class}, handler);
        List<Entity> candidates = List.of(remote, mob("here", 9D));

        List<Entity> result = NearbyFilter.apply(candidates, origin(), null, false, 10, false);

        assertEquals(List.of("here", "elsewhere"), names(result));
    }
}

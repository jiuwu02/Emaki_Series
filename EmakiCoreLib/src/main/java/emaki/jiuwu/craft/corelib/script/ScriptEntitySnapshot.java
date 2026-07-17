package emaki.jiuwu.craft.corelib.script;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;

/** Captures script-visible entity data before work crosses into a Graal worker. */
public final class ScriptEntitySnapshot {

    private ScriptEntitySnapshot() {
    }

    public static EntityView capture(Entity entity) {
        if (entity == null) {
            return EntityView.empty();
        }
        Location location = entity.getLocation().clone();
        World world = location.getWorld();
        String worldName = world == null ? "" : world.getName();
        Map<String, Object> locationSnapshot = ScriptSnapshots.immutableMap(Map.of(
                "world", worldName,
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch()
        ));
        return new EntityView(
                entity.isValid(),
                entity instanceof LivingEntity,
                entity instanceof Player,
                entity.getName(),
                entity.getUniqueId().toString(),
                entity.getType().name().toLowerCase(Locale.ROOT),
                new WorldView(world != null, worldName),
                locationSnapshot,
                entity instanceof LivingEntity living ? living.getHealth() : 0D,
                entity instanceof LivingEntity living ? living.getMaxHealth() : 0D
        );
    }

    public record WorldView(boolean exists, String name) {

        public WorldView {
            name = name == null ? "" : name;
        }

        @Override
        @HostAccess.Export
        public boolean exists() {
            return exists;
        }

        @Override
        @HostAccess.Export
        public String name() {
            return name;
        }
    }

    public record EntityView(
            boolean exists,
            boolean living,
            boolean player,
            String name,
            String uuid,
            String type,
            WorldView world,
            Map<String, Object> location,
            double health,
            double maxHealth) {

        public EntityView {
            name = name == null ? "" : name;
            uuid = uuid == null ? "" : uuid;
            type = type == null ? "" : type;
            world = world == null ? new WorldView(false, "") : world;
            location = location == null ? Map.of() : ScriptSnapshots.immutableMap(location);
        }

        public static EntityView empty() {
            return new EntityView(false, false, false, "", "", "", new WorldView(false, ""), Map.of(), 0D, 0D);
        }

        @Override
        @HostAccess.Export
        public boolean exists() {
            return exists;
        }

        @Override
        @HostAccess.Export
        public boolean living() {
            return living;
        }

        @Override
        @HostAccess.Export
        public boolean player() {
            return player;
        }

        @Override
        @HostAccess.Export
        public String name() {
            return name;
        }

        @Override
        @HostAccess.Export
        public String uuid() {
            return uuid;
        }

        @Override
        @HostAccess.Export
        public String type() {
            return type;
        }

        @Override
        @HostAccess.Export
        public WorldView world() {
            return world;
        }

        @Override
        @HostAccess.Export
        public Map<String, Object> location() {
            return location;
        }

        @Override
        @HostAccess.Export
        public double health() {
            return health;
        }

        @Override
        @HostAccess.Export
        public double maxHealth() {
            return maxHealth;
        }

        public Map<String, Object> asMap() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("exists", exists);
            snapshot.put("living", living);
            snapshot.put("player", player);
            snapshot.put("name", name);
            snapshot.put("uuid", uuid);
            snapshot.put("type", type);
            snapshot.put("world", world.name());
            snapshot.put("location", location);
            snapshot.put("health", health);
            snapshot.put("max_health", maxHealth);
            return ScriptSnapshots.immutableMap(snapshot);
        }
    }
}

package emaki.jiuwu.craft.dungeon.flow;

import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class MobSource {

    public record SpawnResult(boolean success, List<LivingEntity> spawned) {
    }

    public SpawnResult spawn(String instanceId, FlowPoint point, String mobId, int count) {
        World world = Bukkit.getWorld(instanceId);
        if (world == null || mobId == null || mobId.isBlank()) {
            return new SpawnResult(false, List.of());
        }
        Location location = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        List<LivingEntity> spawned = new ArrayList<>(count);
        if (mobId.startsWith("mythic:")) {
            String mythicId = mobId.substring("mythic:".length());
            for (int i = 0; i < count; i++) {
                LivingEntity entity = spawnMythic(location, mythicId);
                if (entity != null) {
                    spawned.add(entity);
                } else {
                    LivingEntity fallback = spawnVanilla(location, "ZOMBIE");
                    if (fallback != null) {
                        spawned.add(fallback);
                    }
                }
            }
            return new SpawnResult(!spawned.isEmpty(), spawned);
        }
        if (mobId.startsWith("emakimobs:")) {
            String mobsId = mobId.substring("emakimobs:".length());
            for (int i = 0; i < count; i++) {
                LivingEntity entity = spawnEmakiMobs(location, mobsId);
                if (entity != null) {
                    spawned.add(entity);
                } else {
                    LivingEntity fallback = spawnVanilla(location, "ZOMBIE");
                    if (fallback != null) {
                        spawned.add(fallback);
                    }
                }
            }
            return new SpawnResult(!spawned.isEmpty(), spawned);
        }
        String vanillaType = mobId.toUpperCase(Locale.ROOT);
        for (int i = 0; i < count; i++) {
            LivingEntity entity = spawnVanilla(location, vanillaType);
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return new SpawnResult(!spawned.isEmpty(), spawned);
    }

    private LivingEntity spawnVanilla(Location location, String entityType) {
        try {
            EntityType type = EntityType.valueOf(entityType);
            if (!type.isAlive() || !type.isSpawnable()) {
                return null;
            }
            return (LivingEntity) location.getWorld().spawnEntity(location, type);
        } catch (IllegalArgumentException | ClassCastException exception) {
            return null;
        }
    }

    private LivingEntity spawnMythic(Location location, String mythicId) {
        try {
            return MythicCalls.spawn(location, mythicId);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private LivingEntity spawnEmakiMobs(Location location, String mobsId) {
        try {
            return MobsCalls.spawn(location, mobsId);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static final class MythicCalls {

        private static LivingEntity spawn(Location location, String mythicId) throws Exception {
            var helper = MythicBukkit.inst().getAPIHelper();
            var spawned = helper.spawnMythicMob(mythicId, location);
            return spawned instanceof LivingEntity living ? living : null;
        }
    }

    private static final class MobsCalls {

        private static LivingEntity spawn(Location location, String mobsId) {
            return EmakiMobsApi.operations()
                    .spawn(location, mobsId)
                    .orElse(null);
        }
    }
}

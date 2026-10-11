package emaki.jiuwu.craft.structure.spawn;

import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class MythicSpawnBridge {

    public SpawnOutcome spawn(Location location, String mythicId, Integer level) {
        try {
            var helper = MythicBukkit.inst().getAPIHelper();
            Entity spawned = level == null
                    ? helper.spawnMythicMob(mythicId, location)
                    : helper.spawnMythicMob(mythicId, location, level);
            if (spawned instanceof LivingEntity living) {
                return SpawnOutcome.ok(living);
            }
            return SpawnOutcome.failed("error.spawn.entry_failed", "MythicMobs 未返回生物实体");
        } catch (InvalidMobTypeException exception) {
            return SpawnOutcome.failed("error.spawn.entry_failed", exception.getMessage());
        }
    }
}

package emaki.jiuwu.craft.structure.spawn;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;

import java.util.Locale;

public final class VanillaSpawnBridge {

    private VanillaSpawnBridge() {
    }

    public static SpawnOutcome spawn(Location location, String typeId) {
        NamespacedKey key = NamespacedKey.fromString(typeId);
        if (key == null) {
            return SpawnOutcome.failed("error.spawn.entry_failed", "非法实体 id: " + typeId);
        }
        EntityType type = Registry.ENTITY_TYPE.get(key);
        if (type == null) {
            return SpawnOutcome.failed("error.spawn.entry_failed", "注册表中不存在: " + typeId);
        }
        if (location.getWorld() == null) {
            return SpawnOutcome.failed("error.spawn.entry_failed", "世界不可用");
        }
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (entity instanceof LivingEntity living) {
            return SpawnOutcome.ok(living);
        }
        return SpawnOutcome.failed("error.spawn.entry_failed", "生成结果不是生物实体: " + typeId);
    }

    public static boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster) {
            return true;
        }
        return entity.getScoreboardTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains("boss"));
    }
}

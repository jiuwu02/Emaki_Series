package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Optional;

public final class EmakiMobsSpawnBridge {

    public SpawnOutcome spawn(Location location, String mobId) {
        Optional<LivingEntity> spawned = EmakiMobsApi.operations().spawn(location, mobId);
        return spawned
                .map(SpawnOutcome::ok)
                .orElseGet(() -> SpawnOutcome.failed("error.spawn.entry_failed",
                        "EmakiMobs 未生成生物: " + mobId));
    }
}

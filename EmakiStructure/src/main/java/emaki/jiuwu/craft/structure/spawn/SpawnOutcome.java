package emaki.jiuwu.craft.structure.spawn;

import org.bukkit.entity.LivingEntity;

public record SpawnOutcome(LivingEntity entity, String errorKey, String errorDetail) {

    public static SpawnOutcome ok(LivingEntity spawned) {
        return new SpawnOutcome(spawned, null, null);
    }

    public static SpawnOutcome failed(String errorKey, String errorDetail) {
        return new SpawnOutcome(null, errorKey, errorDetail);
    }

    public boolean spawned() {
        return entity != null;
    }
}

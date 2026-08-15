package emaki.jiuwu.craft.mobs.spawner;

import org.bukkit.generator.structure.Structure;

import java.util.List;

public record StructureSpawnRule(
        String mobId,
        List<Structure> structures,
        int maxNearby,
        CountRange count,
        ActiveSpawnConfig activeSpawn
) implements SpawnRule {

    @Override
    public String type() {
        return "structure";
    }
}

package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import org.bukkit.World;

public record SpawnContext(World world,
                           StructureTemplate template,
                           StructureFacing facing,
                           int anchorX,
                           int anchorY,
                           int anchorZ,
                           float templateYaw) {
}

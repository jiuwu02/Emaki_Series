package emaki.jiuwu.craft.structure.command;

import java.util.List;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.structure.generate.StructureFacing;

public record MultiblockPlaceSpec(
        CommandSender sender,
        Player player,
        String id,
        World world,
        int anchorX,
        int anchorY,
        int anchorZ,
        StructureFacing facing,
        int placed,
        int failed,
        List<String> failures) {
}

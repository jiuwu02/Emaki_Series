package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class TeleportOperation extends BaseOperation {

    public TeleportOperation() {
        super(
            "teleport",
            "player",
            "Teleport the player.",
            OperationParameter.required("x", OperationParameterType.STRING, "X"),
            OperationParameter.required("y", OperationParameterType.STRING, "Y"),
            OperationParameter.required("z", OperationParameterType.STRING, "Z"),
            OperationParameter.optional("world", OperationParameterType.STRING, "", "World"),
            OperationParameter.optional("yaw", OperationParameterType.DOUBLE, "0", "Yaw"),
            OperationParameter.optional("pitch", OperationParameterType.DOUBLE, "0", "Pitch")
        );
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        Location base = context.player().getLocation();
        World world = Texts.isBlank(arguments.get("world")) ? base.getWorld() : Bukkit.getWorld(arguments.get("world"));
        if (world == null) {
            return OperationResult.failure(OperationErrorType.WORLD_NOT_FOUND, "Unknown world for teleport operation.");
        }
        Location target = new Location(
            world,
            OperationParsers.parseCoordinate(arguments.get("x"), base.getX()),
            OperationParsers.parseCoordinate(arguments.get("y"), base.getY()),
            OperationParsers.parseCoordinate(arguments.get("z"), base.getZ()),
            (float) OperationParsers.parseDouble(arguments.get("yaw"), base.getYaw()),
            (float) OperationParsers.parseDouble(arguments.get("pitch"), base.getPitch())
        );
        context.player().teleport(target);
        return OperationResult.ok();
    }
}

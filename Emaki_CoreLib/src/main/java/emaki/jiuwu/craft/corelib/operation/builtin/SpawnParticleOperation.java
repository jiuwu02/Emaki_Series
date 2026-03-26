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
import org.bukkit.Particle;
import org.bukkit.World;

public final class SpawnParticleOperation extends BaseOperation {

    public SpawnParticleOperation() {
        super(
            "spawn_particle",
            "feedback",
            "Spawn particles.",
            OperationParameter.required("particle", OperationParameterType.STRING, "Particle key"),
            OperationParameter.optional("count", OperationParameterType.INTEGER, "1", "Particle count"),
            OperationParameter.optional("target", OperationParameterType.STRING, "player", "Target"),
            OperationParameter.optional("world", OperationParameterType.STRING, "", "World"),
            OperationParameter.optional("x", OperationParameterType.STRING, "", "X"),
            OperationParameter.optional("y", OperationParameterType.STRING, "", "Y"),
            OperationParameter.optional("z", OperationParameterType.STRING, "", "Z"),
            OperationParameter.optional("offset_x", OperationParameterType.DOUBLE, "0", "Offset x"),
            OperationParameter.optional("offset_y", OperationParameterType.DOUBLE, "0", "Offset y"),
            OperationParameter.optional("offset_z", OperationParameterType.DOUBLE, "0", "Offset z"),
            OperationParameter.optional("extra", OperationParameterType.DOUBLE, "0", "Extra")
        );
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        Particle particle = OperationParsers.parseParticle(stringArg(arguments, "particle"));
        if (particle == null) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Unknown particle: " + stringArg(arguments, "particle"));
        }
        Location location;
        String target = Texts.lower(arguments.get("target"));
        if (!"location".equals(target)) {
            OperationResult playerCheck = requirePlayerResult(context);
            if (!playerCheck.success()) {
                return playerCheck;
            }
            location = context.player().getLocation();
        } else {
            World world = Texts.isBlank(arguments.get("world"))
                ? (context.player() == null ? null : context.player().getWorld())
                : Bukkit.getWorld(arguments.get("world"));
            if (world == null) {
                return OperationResult.failure(OperationErrorType.WORLD_NOT_FOUND, "Unknown world for particle operation.");
            }
            double baseX = context.player() == null ? 0D : context.player().getLocation().getX();
            double baseY = context.player() == null ? 0D : context.player().getLocation().getY();
            double baseZ = context.player() == null ? 0D : context.player().getLocation().getZ();
            location = new Location(
                world,
                OperationParsers.parseCoordinate(arguments.get("x"), baseX),
                OperationParsers.parseCoordinate(arguments.get("y"), baseY),
                OperationParsers.parseCoordinate(arguments.get("z"), baseZ)
            );
        }
        location.getWorld().spawnParticle(
            particle,
            location,
            OperationParsers.parseInt(arguments.get("count"), 1),
            OperationParsers.parseDouble(arguments.get("offset_x"), 0D),
            OperationParsers.parseDouble(arguments.get("offset_y"), 0D),
            OperationParsers.parseDouble(arguments.get("offset_z"), 0D),
            OperationParsers.parseDouble(arguments.get("extra"), 0D)
        );
        return OperationResult.ok();
    }
}

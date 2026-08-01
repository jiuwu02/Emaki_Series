package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Spawns entities at the target position.
 *
 * <p>An unknown type name is {@code Failure} because it is a typo, while a known but non-spawnable type is
 * {@code Skipped} because the configuration is well-formed and the engine simply refuses it.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: adds entities to a region.</p>
 */
public final class SpawnEntityStage extends BaseStage {

    public SpawnEntityStage() {
        super("spawn_entity", "entity", "Spawns entities at the target position.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.required("type", CoreStageParameterType.ENTITY_TYPE, "Entity type"),
                CoreStageParameter.optional("count", CoreStageParameterType.INTEGER, "1", "Entity count"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        EntityType type = arguments.getEntityType("type").orElse(null);
        if (type == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.spawn_entity.unknown_entity_type",
                    Map.of("type", arguments.getString("type")));
        }
        if (!type.isSpawnable()) {
            return CoreActionOutcome.skipped("action.v2.stage.spawn_entity.not_spawnable");
        }
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.no_location");
        }
        World world = location.getWorld();
        int count = Math.max(1, arguments.getInt("count", 1));
        Entity last = null;
        for (int index = 0; index < count; index++) {
            last = world.spawnEntity(location, type);
        }
        return CoreActionOutcome.success(Map.of(
                "type", type.name().toLowerCase(Locale.ROOT),
                "count", count,
                "last_uuid", last == null ? "" : last.getUniqueId().toString()));
    }
}

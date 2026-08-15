package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

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
                    "action.stage.spawn_entity.unknown_entity_type",
                    Map.of("type", arguments.getString("type")));
        }
        if (!type.isSpawnable()) {
            return CoreActionOutcome.skipped("action.stage.spawn_entity.not_spawnable");
        }
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
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

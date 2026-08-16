package emaki.jiuwu.craft.mobs.action.stage;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * DSL Stage：在目标位置召唤 EmakiMobs 定义的生物。
 * 
 * <p>参数：
 * <ul>
 *   <li>{@code mob_id}（必需）- EmakiMobs 生物定义 ID</li>
 *   <li>{@code count}（可选，默认 1）- 召唤数量</li>
 * </ul>
 * 
 * <p>示例：
 * <pre>
 * self | summon_mob mob_id=fire_guardian count=2
 * killer | summon_mob mob_id=boss_minion
 * </pre>
 */
public final class SummonMobStage extends BaseStage {

    private final Supplier<Map<String, MobSpec>> mobRegistrySupplier;
    private final MobFactory mobFactory;

    public SummonMobStage(Supplier<Map<String, MobSpec>> mobRegistrySupplier, MobFactory mobFactory) {
        super("summon_mob", "emakimobs", "Summons an EmakiMobs-defined entity at the target location.",
                CoreTargetRequirement.REQUIRED_ANY, CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.required("mob_id", CoreStageParameterType.STRING, "EmakiMobs mob definition ID"),
                CoreStageParameter.optional("count", CoreStageParameterType.INTEGER, "1", "Spawn count"));
        this.mobRegistrySupplier = mobRegistrySupplier;
        this.mobFactory = mobFactory;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
                                               @NotNull CoreResolvedArguments arguments) {
        // 获取 mob_id 参数
        String mobId = arguments.getString("mob_id");
        if (mobId == null || mobId.isBlank()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.summon_mob.missing_mob_id", Map.of());
        }

        // 检查生物定义是否存在
        Map<String, MobSpec> registry = mobRegistrySupplier.get();
        MobSpec mobSpec = registry.get(mobId);
        if (mobSpec == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.summon_mob.unknown_mob_id",
                    Map.of("mob_id", mobId));
        }

        // 获取目标位置
        Location location = context.currentTarget().location();
        if (location == null || location.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.common.no_location");
        }

        // 获取召唤数量
        int count = Math.max(1, arguments.getInt("count", 1));

        // 召唤生物
        LivingEntity lastSpawned = null;
        for (int i = 0; i < count; i++) {
            var result = mobFactory.spawn(location, mobId);
            if (result.isPresent()) {
                lastSpawned = result.get();
            }
        }

        if (lastSpawned == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.summon_mob.spawn_failed",
                    Map.of("mob_id", mobId));
        }

        return CoreActionOutcome.success(Map.of(
                "mob_id", mobId,
                "count", count,
                "last_uuid", lastSpawned.getUniqueId().toString()));
    }
}

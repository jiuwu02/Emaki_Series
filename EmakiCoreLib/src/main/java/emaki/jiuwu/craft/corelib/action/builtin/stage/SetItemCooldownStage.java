package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetItemCooldownStage extends BaseStage {

    public SetItemCooldownStage() {
        super("set_item_cooldown", "player", "为玩家的指定材质物品设置冷却。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("material", CoreStageParameterType.MATERIAL, "物品材质"),
                CoreStageParameter.required("seconds", CoreStageParameterType.INTEGER, "冷却秒数"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof Player player)) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        Material material = StageSupport.material(arguments.getString("material"));
        if (material == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.set_item_cooldown.unknown_material",
                    java.util.Map.of("material", arguments.getString("material")));
        }
        player.setCooldown(material, Math.max(0, arguments.getInt("seconds", 0)) * 20);
        return CoreActionOutcome.success();
    }
}

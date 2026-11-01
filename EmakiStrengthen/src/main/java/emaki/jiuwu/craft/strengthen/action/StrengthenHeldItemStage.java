package emaki.jiuwu.craft.strengthen.action;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

public final class StrengthenHeldItemStage implements CoreActionStage {

    public enum Operation {

        RERENDER("strengthen_rerender", "重新渲染目标手持物品上的强化层。"),

        SET_STAR("strengthen_set_star", "设置目标手持物品的星级。"),

        ADD_STAR("strengthen_add_star", "为目标手持物品增加星级。"),

        REMOVE_STAR("strengthen_remove_star", "移除目标手持物品的星级。"),

        RESET_STAR("strengthen_reset_star", "将目标手持物品的星级重置为零。"),

        CLEAR_LAYER("strengthen_clear_layer", "移除目标手持物品上的强化层。");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiStrengthenPlugin plugin;
    private final Operation operation;

    public StrengthenHeldItemStage(@NotNull EmakiStrengthenPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "strengthen";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case RERENDER, RESET_STAR, CLEAR_LAYER -> List.of();
            case SET_STAR -> List.of(CoreStageParameter.required("star",
                    CoreStageParameterType.INTEGER, "目标星级"));
            case ADD_STAR -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "增加的星级"));
            case REMOVE_STAR -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "移除的星级"));
        };
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull Set<CoreActionKey<?>> requiredContext() {
        return Set.of();
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (plugin.attemptService() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.strengthen.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        ItemStack original = target.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return CoreActionOutcome.skipped("action.stage.strengthen.empty_hand");
        }
        StrengthenState before = plugin.attemptService().readState(original);
        ItemStack updated = apply(target, original, before, arguments);
        if (updated == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.strengthen.rebuild_failed");
        }
        target.getInventory().setItemInMainHand(updated);
        StrengthenState after = plugin.attemptService().readState(updated);
        return CoreActionOutcome.success(Map.of(
                "old_star", before.currentStar(),
                "new_star", after.currentStar(),
                "has_layer", after.hasLayer()));
    }

    private ItemStack apply(Player target, ItemStack original, StrengthenState before, CoreResolvedArguments arguments) {
        return switch (operation) {
            case RERENDER -> plugin.attemptService().rebuild(target, original);
            case SET_STAR -> plugin.attemptService().applyAdminState(target, original,
                    arguments.getInt("star", before.currentStar()), null, null);
            case ADD_STAR -> plugin.attemptService().applyAdminState(target, original,
                    before.currentStar() + arguments.getInt("amount", 0), null, null);
            case REMOVE_STAR -> plugin.attemptService().applyAdminState(target, original,
                    Math.max(0, before.currentStar() - Math.max(0, arguments.getInt("amount", 1))), null, null);
            case RESET_STAR -> plugin.attemptService().applyAdminState(target, original, 0, null, null);
            case CLEAR_LAYER -> plugin.attemptService().clearStrengthenLayer(original);
        };
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}

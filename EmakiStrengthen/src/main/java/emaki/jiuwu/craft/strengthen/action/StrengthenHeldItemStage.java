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

/**
 * Reads and rewrites the strengthen layer on the target's held item.
 *
 * <p>Replaces the legacy {@code StrengthenHeldItemAction}. Two things move out of the implementation and
 * into declarations: the target is supplied by the pipeline's target flow rather than read from a context
 * player, and the thread domain is stated up front instead of inherited from a base class default.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY} for all six operations: each one reads the target's main hand, asks
 * {@code StrengthenAttemptService} for a rebuilt stack, and writes it back. That is single-entity inventory
 * state, which only the thread owning that entity may touch.</p>
 */
public final class StrengthenHeldItemStage implements CoreActionStage {

    /** Which strengthen mutation a stage instance performs. */
    public enum Operation {

        /** Re-render the layer without changing its values. */
        RERENDER("strengthen_rerender", "Re-renders the strengthen layer on the target's held item."),

        /** Set the star count to an absolute value. */
        SET_STAR("strengthen_set_star", "Sets the star count on the target's held item."),

        /** Add to the current star count. */
        ADD_STAR("strengthen_add_star", "Adds stars to the target's held item."),

        /** Subtract from the current star count, floored at zero. */
        REMOVE_STAR("strengthen_remove_star", "Removes stars from the target's held item."),

        /** Reset the star count to zero. */
        RESET_STAR("strengthen_reset_star", "Resets the star count on the target's held item to zero."),

        /** Strip the strengthen layer entirely. */
        CLEAR_LAYER("strengthen_clear_layer", "Removes the strengthen layer from the target's held item.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiStrengthenPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the attempt service
     * @param operation which mutation this instance performs
     */
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
                    CoreStageParameterType.INTEGER, "Target star count"));
            case ADD_STAR -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "Stars to add"));
            case REMOVE_STAR -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "Stars to remove"));
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
        ItemStack updated = apply(original, before, arguments);
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

    private ItemStack apply(ItemStack original, StrengthenState before, CoreResolvedArguments arguments) {
        return switch (operation) {
            case RERENDER -> plugin.attemptService().rebuild(original);
            case SET_STAR -> plugin.attemptService().applyAdminState(original,
                    arguments.getInt("star", before.currentStar()), null, null);
            case ADD_STAR -> plugin.attemptService().applyAdminState(original,
                    before.currentStar() + arguments.getInt("amount", 0), null, null);
            case REMOVE_STAR -> plugin.attemptService().applyAdminState(original,
                    Math.max(0, before.currentStar() - Math.max(0, arguments.getInt("amount", 1))), null, null);
            case RESET_STAR -> plugin.attemptService().applyAdminState(original, 0, null, null);
            case CLEAR_LAYER -> plugin.attemptService().clearStrengthenLayer(original);
        };
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}

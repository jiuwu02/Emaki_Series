package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class TakeItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;
    private final ActionAuditLogger auditLogger;

    public TakeItemStage(ItemSourceService itemSourceService, ActionAuditLogger auditLogger) {
        super("take_item", "item", "Removes matching items from the target's inventory.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "",
                        "Expected item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Amount to take"));
        this.itemSourceService = itemSourceService;
        this.auditLogger = auditLogger;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        ItemSourceRef source = StageSupport.itemSource(arguments.getString("item_source"));
        if (source == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.invalid_item_source",
                    Map.of("item_source", arguments.getString("item_source")));
        }
        if (itemSourceService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.item.service_unavailable");
        }
        int requestedAmount = arguments.getInt("amount", 1);
        if (requestedAmount <= 0) {
            Map<String, Object> args = Map.of("amount", requestedAmount);
            auditLogger.logFailure(id(), target, OperationType.DECREASE, requestedAmount,
                    "action.stage.common.invalid_positive_amount", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.common.invalid_positive_amount", args);
        }
        long amount = requestedAmount;
        long available = InventoryItemUtil.countItems(target, itemSourceService, source);
        if (available < amount) {
            CoreActionOutcome failure = CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.item.insufficient",
                    Map.of("required", amount, "available", available));
            auditLogger.logFailure(id(), target, OperationType.DECREASE, amount,
                    "action.stage.item.insufficient", Map.of("required", amount, "available", available), context);
            return failure;
        }
        InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                target.getInventory(), itemSourceService, source, amount);
        if (!plan.complete() || !InventoryItemUtil.applyRemoval(target.getInventory(), plan)) {
            auditLogger.logFailure(id(), target, OperationType.DECREASE, amount,
                    "action.stage.item.remove_failed", Map.of(), context);
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.item.remove_failed");
        }
        auditLogger.logSuccess(id(), target, OperationType.DECREASE, available, available - amount,
                amount, context);
        return CoreActionOutcome.success(Map.of(
                "item_source", StageSupport.shorthand(source),
                "amount", amount,
                "available_before", available));
    }
}

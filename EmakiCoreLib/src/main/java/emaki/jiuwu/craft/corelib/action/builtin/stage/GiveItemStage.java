package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class GiveItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;
    private final ActionAuditLogger auditLogger;

    public GiveItemStage(ItemSourceService itemSourceService, ActionAuditLogger auditLogger) {
        super("give_item", "item", "Gives an item source to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "", "Item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Item amount"));
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
        int amount = arguments.getInt("amount", 1);
        if (amount <= 0) {
            Map<String, Object> args = Map.of("amount", amount);
            auditLogger.logFailure(id(), target, OperationType.INCREASE, amount,
                    "action.stage.common.invalid_positive_amount", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.common.invalid_positive_amount", args);
        }
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.create_failed",
                    Map.of("item_source", StageSupport.shorthand(source)));
        }
        if (itemStack.getType().isAir()) {
            return CoreActionOutcome.skipped("action.stage.item.created_air");
        }
        int dropped = 0;
        for (ItemStack leftover : InventoryItemUtil.addOrDrop(target, itemStack).values()) {
            if (!StageSupport.isEmpty(leftover)) {
                dropped += leftover.getAmount();
            }
        }
        auditLogger.logSuccess(id(), target, OperationType.INCREASE, null, null, itemStack.getAmount(), context);
        return CoreActionOutcome.success(Map.of(
                "item_source", StageSupport.shorthand(source),
                "amount", itemStack.getAmount(),
                "dropped", dropped));
    }
}
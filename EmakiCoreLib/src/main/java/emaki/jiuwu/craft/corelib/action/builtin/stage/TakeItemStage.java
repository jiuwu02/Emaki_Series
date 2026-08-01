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
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

/**
 * Removes matching items from the target's inventory.
 *
 * <p>All or nothing: the removal is planned first and only applied when the full amount is available, so a
 * partial take cannot leave a player charged half the cost of something they did not receive.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's inventory.</p>
 */
public final class TakeItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;

    public TakeItemStage(ItemSourceService itemSourceService) {
        super("take_item", "item", "Removes matching items from the target's inventory.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "",
                        "Expected item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Amount to take"));
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        ItemSource source = StageSupport.itemSource(arguments.getString("item_source"));
        if (source == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.invalid_item_source",
                    Map.of("item_source", arguments.getString("item_source")));
        }
        if (itemSourceService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.item.service_unavailable");
        }
        long amount = Math.max(1L, arguments.getInt("amount", 1));
        long available = InventoryItemUtil.countItems(target, itemSourceService, source);
        if (available < amount) {
            return CoreActionOutcome.skipped("action.stage.item.not_enough");
        }
        InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                target.getInventory(), itemSourceService, source, amount);
        if (!plan.complete() || !InventoryItemUtil.applyRemoval(target.getInventory(), plan)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.item.remove_failed");
        }
        return CoreActionOutcome.success(Map.of(
                "item_source", StageSupport.shorthand(source),
                "amount", amount,
                "available_before", available));
    }
}

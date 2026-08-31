package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

public final class SendItemStage extends BaseStage {

    public SendItemStage() {
        super("send_item", "item", "Gives the pipeline item to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull Set<CoreActionKey<?>> requiredContext() {
        return Set.of(CoreActionKeys.ITEM);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        Optional<ItemStack> item = context.get(CoreActionKeys.ITEM);
        if (item.isEmpty() || StageSupport.isEmpty(item.get())) {
            return CoreActionOutcome.skipped("action.stage.item.no_pipeline_item");
        }
        ItemStack payload = item.get().clone();
        int dropped = 0;
        for (ItemStack leftover : InventoryItemUtil.addOrDrop(target, payload).values()) {
            if (!StageSupport.isEmpty(leftover)) {
                dropped += leftover.getAmount();
            }
        }
        return CoreActionOutcome.success(Map.of("amount", payload.getAmount(), "dropped", dropped));
    }
}

package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

/**
 * Builds an item from a source and gives it to the target, dropping any overflow.
 *
 * <p>The v1 aliases {@code source} / {@code item} / {@code item_source} are collapsed into {@code item_source};
 * the legacy converter rewrites existing configuration.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one player's inventory and may drop at their feet.</p>
 */
public final class GiveItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;

    public GiveItemStage(ItemSourceService itemSourceService) {
        super("give_item", "item", "Gives an item source to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "", "Item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Item amount"));
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        ItemSource source = StageSupport.itemSource(arguments.getString("item_source"));
        if (source == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.item.invalid_item_source",
                    Map.of("item_source", arguments.getString("item_source")));
        }
        if (itemSourceService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.item.service_unavailable");
        }
        int amount = Math.max(1, arguments.getInt("amount", 1));
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.item.create_failed",
                    Map.of("item_source", StageSupport.shorthand(source)));
        }
        if (itemStack.getType().isAir()) {
            return CoreActionOutcome.skipped("action.v2.stage.item.created_air");
        }
        int dropped = 0;
        for (ItemStack leftover : InventoryItemUtil.addOrDrop(target, itemStack).values()) {
            if (!StageSupport.isEmpty(leftover)) {
                dropped += leftover.getAmount();
            }
        }
        return CoreActionOutcome.success(Map.of(
                "item_source", StageSupport.shorthand(source),
                "amount", itemStack.getAmount(),
                "dropped", dropped));
    }
}

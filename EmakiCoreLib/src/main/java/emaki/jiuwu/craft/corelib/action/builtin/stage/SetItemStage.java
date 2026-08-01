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
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

/**
 * Replaces the contents of one of the target's inventory slots.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one player's inventory.</p>
 */
public final class SetItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;

    public SetItemStage(ItemSourceService itemSourceService) {
        super("set_item", "item", "Sets one of the target's inventory slots to an item source.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "mainhand",
                        "Inventory slot"),
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "", "Item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Item amount"));
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        StageSupport.Slot slot = StageSupport.slot(arguments.getString("slot"), "mainhand");
        if (slot == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.unknown_slot", Map.of("slot", arguments.getString("slot")));
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
        int amount = Math.max(1, arguments.getInt("amount", 1));
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.create_failed",
                    Map.of("item_source", StageSupport.shorthand(source)));
        }
        if (itemStack.getType().isAir()) {
            return CoreActionOutcome.skipped("action.stage.item.created_air");
        }
        ItemStack replaced = slot.get(target.getInventory());
        slot.set(target.getInventory(), itemStack.clone());
        return CoreActionOutcome.success(Map.of(
                "slot", slot.id(),
                "item_source", StageSupport.shorthand(source),
                "amount", itemStack.getAmount(),
                "replaced", !StageSupport.isEmpty(replaced)));
    }
}

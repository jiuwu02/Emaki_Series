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
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ClearItemStage extends BaseStage {

    private final ItemSourceService itemSourceService;

    public ClearItemStage(ItemSourceService itemSourceService) {
        super("clear_item", "item", "Empties one of the target's inventory slots.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("slot", CoreStageParameterType.STRING, "Inventory slot"),
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "",
                        "Only clear when the slot holds this source"));
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        StageSupport.Slot slot = StageSupport.slot(arguments.getString("slot"), "");
        if (slot == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.unknown_slot", Map.of("slot", arguments.getString("slot")));
        }
        ItemStack current = slot.get(target.getInventory());
        if (StageSupport.isEmpty(current)) {
            return CoreActionOutcome.skipped("action.stage.item.slot_empty");
        }
        String requested = arguments.getString("item_source");
        if (Texts.isNotBlank(requested)) {
            ItemSourceRef expected = StageSupport.itemSource(requested);
            if (expected == null) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                        "action.stage.item.invalid_item_source", Map.of("item_source", requested));
            }
            ItemSourceRef actual = itemSourceService == null ? null : itemSourceService.identifyItem(current);
            if (!ItemSourceUtil.matches(expected, actual)) {
                return CoreActionOutcome.skipped("action.stage.item.source_mismatch");
            }
        }
        slot.clear(target.getInventory());
        return CoreActionOutcome.success(Map.of("slot", slot.id()));
    }
}

package emaki.jiuwu.craft.corelib.action.builtin.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseGate;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class CreateItemGate extends BaseGate {

    private final ItemSourceService itemSourceService;

    public CreateItemGate(ItemSourceService itemSourceService) {
        super("create_item", "Builds an item and publishes it as the pipeline item.", CoreGateThread.PURE,
                CoreStageParameter.optional("item_source", CoreStageParameterType.STRING, "", "Item source"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1", "Item amount"));
        this.itemSourceService = itemSourceService;
    }

    @Override
    public @NotNull Set<CoreActionKey<?>> providedContext() {
        return Set.of(CoreActionKeys.ITEM);
    }

    @Override
    public @NotNull Set<String> providedVariables() {
        return Set.of("item_source", "item_amount");
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        ItemSourceRef source = StageSupport.itemSource(arguments.getString("item_source"));
        if (source == null) {
            return CoreGateResult.invalid("action.stage.item.invalid_item_source",
                    Map.of("item_source", arguments.getString("item_source")));
        }
        if (itemSourceService == null) {
            return CoreGateResult.invalid("action.stage.item.service_unavailable");
        }
        int amount = Math.max(1, arguments.getInt("amount", 1));
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (StageSupport.isEmpty(itemStack)) {
            return CoreGateResult.invalid("action.stage.item.create_failed",
                    Map.of("item_source", StageSupport.shorthand(source)));
        }
        Map<CoreActionKey<?>, Object> data = Map.of(CoreActionKeys.ITEM, itemStack.clone());
        return CoreGateResult.passed(new ArrayList<>(inbound),
                Map.of("item_source", StageSupport.shorthand(source),
                        "item_amount", String.valueOf(itemStack.getAmount())),
                data);
    }
}

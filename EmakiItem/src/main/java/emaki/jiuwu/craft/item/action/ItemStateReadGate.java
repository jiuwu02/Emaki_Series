package emaki.jiuwu.craft.item.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateType;
import emaki.jiuwu.craft.item.service.EmakiItemStateService;

/** Reads one item state and publishes stable pipeline variables. */
public final class ItemStateReadGate implements CoreActionGate {
    private final EmakiItemStateService state;

    public ItemStateReadGate(EmakiItemStateService state) {
        this.state = state;
    }

    @Override public @NotNull String id() { return "item_state_read"; }
    @Override public @NotNull String description() { return "Reads one persistent item state into pipeline variables."; }
    @Override public @NotNull String category() { return "item"; }
    @Override public @NotNull CoreGateThread threadNeed() { return CoreGateThread.NEEDS_ENTITY_READ; }
    @Override public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("key", CoreStageParameterType.STRING, "State key"),
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "State type"),
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "", "Inventory slot"));
    }
    @Override public @NotNull java.util.Set<String> providedVariables() {
        return java.util.Set.of("item_state.value", "item_state.exists", "item_state.key", "item_state.type");
    }

    @Override
    public @NotNull CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        ItemStack item = resolveItem(context, arguments);
        if (item == null || item.getType().isAir()) {
            return CoreGateResult.halted("action.stage.item.no_target_item");
        }
        ItemStateType type;
        try {
            type = ItemStateType.valueOf(arguments.getString("type", "STRING").trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return CoreGateResult.invalid("action.stage.item_state.bad_type");
        }
        ItemStateKey<Object> key;
        try {
            key = new ItemStateKey<>(ItemStateSchema.NAMESPACE, ItemStateSchema.PARTITION,
                    arguments.getString("key"), type);
        } catch (RuntimeException exception) {
            return CoreGateResult.invalid("action.stage.item_state.bad_key");
        }
        Object value = state.get(item, key).orElse(null);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("item_state.key", key.key());
        variables.put("item_state.type", type.name().toLowerCase());
        variables.put("item_state.exists", Boolean.toString(value != null));
        variables.put("item_state.value", value == null ? "" : String.valueOf(value));
        return CoreGateResult.passed(inbound, variables, Map.of());
    }

    private ItemStack resolveItem(CoreStageContext context, CoreResolvedArguments arguments) {
        Player player = context.currentTarget().entityOrNull() instanceof Player p ? p
                : context.caster().entityOrNull() instanceof Player p ? p : null;
        String rawSlot = arguments.getString("slot", "");
        if (!rawSlot.isBlank()) {
            StageSupport.Slot slot = StageSupport.slot(rawSlot, "mainhand");
            return slot == null || player == null ? null : slot.get(player.getInventory());
        }
        ItemStack pipeline = context.get(CoreActionKeys.ITEM).orElse(null);
        return pipeline != null ? pipeline : player == null ? null : player.getInventory().getItemInMainHand();
    }
}

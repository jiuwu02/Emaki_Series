package emaki.jiuwu.craft.item.action;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMutation;
import emaki.jiuwu.craft.item.api.ItemStateSchema;
import emaki.jiuwu.craft.item.api.ItemStateType;
import emaki.jiuwu.craft.item.service.EmakiItemStateService;

public final class ItemStateStage implements CoreActionStage {
    public enum Operation {
        SET("item_state_set", "Sets a typed persistent item state."),
        ADD("item_state_add", "Adds to a numeric persistent item state."),
        REMOVE("item_state_remove", "Removes a persistent item state."),
        REFRESH("item_state_refresh", "Refreshes the target item while preserving persistent state.");
        private final String id;
        private final String description;
        Operation(String id, String description) { this.id = id; this.description = description; }
    }

    private final EmakiItemPlugin plugin;
    private final EmakiItemStateService state;
    private final Operation operation;

    public ItemStateStage(EmakiItemPlugin plugin, EmakiItemStateService state, Operation operation) {
        this.plugin = plugin;
        this.state = state;
        this.operation = operation;
    }

    @Override public @NotNull String id() { return operation.id; }
    @Override public @NotNull String description() { return operation.description; }
    @Override public @NotNull String category() { return "item"; }
    @Override public @NotNull List<CoreStageParameter> parameters() {
        if (operation == Operation.REFRESH) {
            return List.of(CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "", "Inventory slot"));
        }
        if (operation == Operation.REMOVE) {
            return List.of(CoreStageParameter.required("key", CoreStageParameterType.STRING, "State key"),
                    CoreStageParameter.required("type", CoreStageParameterType.STRING, "State type"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "", "Inventory slot"));
        }
        if (operation == Operation.ADD) {
            return List.of(CoreStageParameter.required("key", CoreStageParameterType.STRING, "State key"),
                    CoreStageParameter.required("type", CoreStageParameterType.STRING, "State type"),
                    CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Numeric delta"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "", "Inventory slot"));
        }
        return List.of(CoreStageParameter.required("key", CoreStageParameterType.STRING, "State key"),
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "State type"),
                CoreStageParameter.required("value", CoreStageParameterType.STRING, "State value"),
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "", "Inventory slot"));
    }
    @Override public @NotNull CoreTargetRequirement targetRequirement() { return CoreTargetRequirement.OPTIONAL; }
    @Override public @NotNull Set<CoreActionKey<?>> requiredContext() { return Set.of(); }
    @Override public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context, @NotNull CoreResolvedArguments arguments) {
        Target target = resolveTarget(context, arguments);
        if (target == null || target.item() == null || target.item().getType().isAir()) {
            return CoreActionOutcome.skipped("action.stage.item.no_target_item");
        }
        if (operation == Operation.REFRESH) {
            var snapshot = state.snapshot(target.item());
            ItemStack refreshed = plugin.updateService().forceUpdate(target.item());
            if (refreshed == null) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR, "action.stage.item.refresh_failed");
            }
            boolean preserved = state.restoreSnapshot(refreshed, snapshot);
            if (!preserved) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                        "action.stage.item_state.refresh_state_lost");
            }
            target.commit(refreshed);
            return CoreActionOutcome.success(Map.of("target", target.id(), "refreshed", true, "state_preserved", true));
        }
        ItemStateType type;
        try { type = ItemStateType.valueOf(arguments.getString("type", "STRING").trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, "action.stage.item_state.bad_type"); }
        ItemStateKey<Object> key;
        try { key = new ItemStateKey<>(ItemStateSchema.NAMESPACE, ItemStateSchema.PARTITION, arguments.getString("key"), type); }
        catch (RuntimeException exception) { return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, "action.stage.item_state.bad_key"); }
        Player holder = resolveHolder(context);
        ItemStateMutation<Object> result;
        if (operation == Operation.REMOVE) {
            result = state.remove(target.item(), key, holder);
        } else if (operation == Operation.ADD) {
            result = state.add(target.item(), key, arguments.getDouble("amount", 0D), holder);
        } else {
            Object value = parse(type, arguments.getString("value"));
            if (value == null) return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, "action.stage.item_state.bad_value");
            result = state.set(target.item(), key, value, holder);
        }
        if (result.rejected()) return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED, "action.stage.item_state.rejected", Map.of("reason", result.reason()));
        target.commit(target.item());
        return CoreActionOutcome.success(Map.of("key", key.key(), "old", String.valueOf(result.oldValue()), "new", String.valueOf(result.newValue()), "delta", String.valueOf(result.delta()), "committed", result.committed(), "changed", result.changed()));
    }

    private Object parse(ItemStateType type, String raw) {
        try {
            return switch (type) {
                case INTEGER -> Integer.valueOf(raw);
                case LONG -> Long.valueOf(raw);
                case DOUBLE -> Double.valueOf(raw);
                case BOOLEAN -> parseBoolean(raw);
                case STRING -> raw;
            };
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return null;
    }

    private Player resolveHolder(CoreStageContext context) {
        return context.currentTarget().entityOrNull() instanceof Player target
                ? target
                : context.caster().entityOrNull() instanceof Player caster ? caster : null;
    }

    private Target resolveTarget(CoreStageContext context, CoreResolvedArguments arguments) {
        Player player = resolveHolder(context);
        String rawSlot = arguments.getString("slot", "");
        if (!rawSlot.isBlank()) {
            StageSupport.Slot slot = StageSupport.slot(rawSlot, "mainhand");
            return slot == null || player == null ? null : new SlotTarget(player, slot);
        }
        ItemStack pipeline = context.get(CoreActionKeys.ITEM).orElse(null);
        return pipeline != null ? new PipelineTarget(pipeline) : player == null ? null : new SlotTarget(player, StageSupport.slot("mainhand", "mainhand"));
    }

    private interface Target { String id(); ItemStack item(); void commit(ItemStack item); }
    private record SlotTarget(Player player, StageSupport.Slot slot) implements Target {
        public String id() { return slot.id(); } public ItemStack item() { return slot.get(player.getInventory()); }
        public void commit(ItemStack item) { slot.set(player.getInventory(), item); }
    }
    private record PipelineTarget(ItemStack original) implements Target {
        public String id() { return "pipeline:item"; } public ItemStack item() { return original; }
        public void commit(ItemStack item) {
            if (item == original) {
                return;
            }
            original.copyDataFrom(item, ignored -> true);
            original.setAmount(item.getAmount());
        }
    }
}

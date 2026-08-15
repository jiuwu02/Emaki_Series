package emaki.jiuwu.craft.item.action;

import java.util.List;
import java.util.Locale;
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
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentInspector.ComponentValueParseResult;

public final class ItemComponentStage implements CoreActionStage {

    public enum Operation {

        ADD("item_component_add", "Adds a data component to the target item."),

        MODIFY("item_component_modify", "Modifies a data component on the target item."),

        REMOVE("item_component_remove", "Removes a data component from the target item.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final ItemComponentInspector inspector;
    private final Operation operation;

    public ItemComponentStage(ItemComponentInspector inspector, @NotNull Operation operation) {
        this.inspector = inspector;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "item";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        if (operation == Operation.REMOVE) {
            return List.of(
                    CoreStageParameter.required("component", CoreStageParameterType.STRING, "Component id"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "",
                            "Inventory slot; empty uses the pipeline item or the main hand"));
        }
        return List.of(
                CoreStageParameter.required("component", CoreStageParameterType.STRING, "Component id"),
                CoreStageParameter.required("value", CoreStageParameterType.STRING, "Component value"),
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "",
                        "Inventory slot; empty uses the pipeline item or the main hand"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.OPTIONAL;
    }

    @Override
    public @NotNull Set<CoreActionKey<?>> requiredContext() {
        return Set.of();
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (inspector == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.item.inspector_unavailable");
        }
        String componentId = inspector.normalizeComponentId(arguments.getString("component"));
        if (componentId.isBlank()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.component_required");
        }
        ItemComponentPatch patch;
        if (operation == Operation.REMOVE) {
            patch = ItemComponentPatch.unset();
        } else {
            ComponentValueParseResult parsed = inspector.parseComponentValue(arguments.getString("value"));
            if (!parsed.success()) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                        "action.stage.item.bad_component_value",
                        Map.of("error", String.valueOf(parsed.errorMessage())));
            }
            patch = ItemComponentPatch.set(parsed.value());
        }
        Target target = resolveTarget(context, arguments);
        if (target == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.item.no_target_item");
        }
        return patch(target, componentId, patch);
    }

    private CoreActionOutcome patch(Target target, String componentId, ItemComponentPatch patch) {
        try {
            ItemStack original = target.itemStack();
            if (original == null || original.getType().isAir()) {
                return CoreActionOutcome.skipped("action.stage.item.target_empty");
            }
            boolean existedBefore = inspector.contains(original, componentId);
            CoreActionOutcome existence = checkExistence(existedBefore);
            if (existence != null) {
                return existence;
            }
            ItemBuildResult buildResult = EmakiCoreLibApi.applyConfiguredItem(original,
                    new ConfiguredItemDefinition(null, original.getAmount(), Map.of(componentId, patch)));
            if (buildResult.hasErrors()) {
                return buildFailure(buildResult);
            }
            ItemStack updated = buildResult.itemStack();
            if (updated == null) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                        "action.stage.item.patch_returned_nothing");
            }
            boolean changed = !updated.equals(original);
            target.commit(updated);
            return CoreActionOutcome.success(Map.of(
                    "component", componentId,
                    "target", target.id(),
                    "changed", changed,
                    "existed_before", existedBefore));
        } catch (RuntimeException | LinkageError exception) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.item.patch_error",
                    Map.of("error", String.valueOf(exception.getMessage())));
        }
    }

    private CoreActionOutcome checkExistence(boolean existedBefore) {
        return switch (operation) {
            case ADD -> existedBefore
                    ? CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.stage.item.component_exists")
                    : null;
            case MODIFY -> existedBefore
                    ? null
                    : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.stage.item.component_missing");
            case REMOVE -> existedBefore
                    ? null
                    : CoreActionOutcome.skipped("action.stage.item.component_absent");
        };
    }

    private CoreActionOutcome buildFailure(ItemBuildResult result) {
        boolean unavailable = result.issues().stream()
                .map(ItemBuildIssue::message)
                .map(message -> message.toLowerCase(Locale.ROOT))
                .anyMatch(message -> message.contains("unavailable"));
        return CoreActionOutcome.failure(
                unavailable ? CoreActionFailureKind.MISSING_CONTEXT : CoreActionFailureKind.INVALID_CONFIG,
                unavailable
                        ? "action.stage.item.component_unsupported"
                        : "action.stage.item.patch_failed",
                Map.of("issues", result.issues().stream().map(ItemBuildIssue::message).toList()));
    }

    private Target resolveTarget(CoreStageContext context, CoreResolvedArguments arguments) {
        Player player = player(context.currentTarget());
        String rawSlot = arguments.getString("slot", "");
        if (!rawSlot.isBlank()) {
            StageSupport.Slot slot = StageSupport.slot(rawSlot, "mainhand");
            return slot == null || player == null ? null : new SlotTarget(player, slot);
        }
        ItemStack fromPipeline = context.get(CoreActionKeys.ITEM).orElse(null);
        if (fromPipeline != null) {
            return new PipelineTarget(fromPipeline);
        }
        return player == null ? null : new SlotTarget(player, StageSupport.slot("mainhand", "mainhand"));
    }

    private interface Target {

        String id();

        ItemStack itemStack();

        void commit(ItemStack itemStack);
    }

    private record SlotTarget(Player player, StageSupport.Slot slot) implements Target {

        @Override
        public String id() {
            return slot.id();
        }

        @Override
        public ItemStack itemStack() {
            return slot.get(player.getInventory());
        }

        @Override
        public void commit(ItemStack itemStack) {
            slot.set(player.getInventory(), itemStack);
        }
    }

    private record PipelineTarget(ItemStack original) implements Target {

        @Override
        public String id() {
            return "pipeline:item";
        }

        @Override
        public ItemStack itemStack() {
            return original;
        }

        @Override
        public void commit(ItemStack itemStack) {
            original.copyDataFrom(itemStack, _ -> true);
            original.setAmount(itemStack.getAmount());
        }
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}

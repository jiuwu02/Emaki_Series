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

/**
 * Adds, modifies or removes a data component on an item.
 *
 * <p>Replaces the legacy {@code ItemComponentAction}, and the place where this module's item-target guessing
 * ends. v1 walked a fallback chain of seven weakly-typed context keys ({@code item}, {@code itemStack},
 * {@code item_stack}, {@code resultItem}, {@code result_item}, {@code targetItem}, {@code target_item}) looking
 * for something that happened to be an {@code ItemStack}. Four of those keys had no writer anywhere in the
 * repository. This stage reads exactly two sources instead:</p>
 * <ol>
 *   <li>an explicit {@code slot} argument, which names an inventory slot on the target;</li>
 *   <li>{@link CoreActionKeys#ITEM}, the typed pipeline key.</li>
 * </ol>
 *
 * <p>With neither present it falls back to the target's main hand, as v1 did. The gain is that a missing item
 * is now a load-time contract question rather than a silent walk down a chain of guesses.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the slot path and the main-hand fallback both write one player's
 * inventory.</p>
 */
public final class ItemComponentStage implements CoreActionStage {

    /** Which component mutation a stage instance performs. */
    public enum Operation {

        /** Set a component that is not present yet. */
        ADD("item_component_add", "Adds a data component to the target item."),

        /** Overwrite a component that is already present. */
        MODIFY("item_component_modify", "Modifies a data component on the target item."),

        /** Unset a component. */
        REMOVE("item_component_remove", "Removes a data component from the target item.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final ItemComponentInspector inspector;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param inspector resolves component ids and parses component values
     * @param operation which mutation this instance performs
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>{@code OPTIONAL} because the pipeline item key alone is enough to run: a flow that produced an item
     * without a player subject can still patch it.</p>
     */
    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.OPTIONAL;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not declared as a hard requirement: the key is one of three ways to find the item, so demanding it
     * would reject the slot and main-hand forms at load time.</p>
     */
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

    /**
     * Enforces the add/modify/remove precondition.
     *
     * <p>{@code null} means the precondition holds and the patch may proceed. Adding over an existing component
     * and modifying a missing one are both refused rather than silently corrected, because either would hide a
     * configuration mistake behind a working-looking result.</p>
     */
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

    /**
     * Finds the item to patch.
     *
     * <p>Order is explicit-slot, then the typed pipeline key, then the target's main hand. An explicit slot
     * wins over the pipeline item because naming a slot is a deliberate instruction, not a fallback.</p>
     */
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

    /** Where the patched item lives, and how to write it back. */
    private interface Target {

        String id();

        ItemStack itemStack();

        void commit(ItemStack itemStack);
    }

    /** An inventory slot on a player. */
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

    /**
     * The item the pipeline is carrying.
     *
     * <p>Patched in place: the stack came from the pipeline context, and whoever put it there holds the
     * reference that later stages will read, so replacing it would leave them looking at the unpatched
     * copy.</p>
     */
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

package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenAttemptEvent;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenPreAttemptEvent;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.api.model.AttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenBranchNode;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenConditionGroup;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenConditionNode;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;

final class StrengthenAttemptServiceDelegate {

    private final StrengthenAttemptService ownerRef;

    StrengthenAttemptServiceDelegate(StrengthenAttemptService ownerRef) {
        this.ownerRef = ownerRef;
    }

    boolean canStrengthen(ItemStack itemStack) {
        return ownerRef.readState(itemStack).eligible();
    }

    StrengthenState readState(ItemStack itemStack) {
        return ownerRef.resolveState(itemStack).state();
    }

    AttemptResult internalFailure(Player player, AttemptContext context, AttemptPreview resolvedPreview, String operationId) {
        try {
            AttemptPreview failedPreview = resolvedPreview == null ? ownerRef.preview(player, context) : resolvedPreview;
            return AttemptResult.failure("strengthen.error.internal", failedPreview,
                    ownerRef.replacements(failedPreview, failedPreview.currentStar()), operationId);
        } catch (RuntimeException | LinkageError ignored) {
            return AttemptResult.failure("strengthen.error.internal", null, Map.of(), operationId);
        }
    }

    AttemptResult finishAttempt(Player player, AttemptResult result) {
        if (ownerRef.isPlayerOwned(player)) {
            try {
                Bukkit.getPluginManager().callEvent(new StrengthenAttemptEvent(player, result));
            } catch (RuntimeException | LinkageError exception) {
                ownerRef.plugin.messageService().warning("console.strengthen_result_dispatch_failed", Map.of(
                        "operation_id", Texts.toStringSafe(result.operationId()),
                        "error", String.valueOf(exception.getMessage())));
            }
        }
        return result;
    }

    boolean isPlayerOwned(Player player) {
        return ownerRef.threadOwnership != null && player != null && ownerRef.threadOwnership.isEntityOwned(player);
    }

    boolean accepting() {
        return ownerRef.accepting.get();
    }

    void freezeAccepting() {
        ownerRef.accepting.set(false);
    }

    void resumeAccepting() {
        ownerRef.accepting.set(true);
    }

    boolean drain(long timeout, TimeUnit unit) {
        return ownerRef.operationJournal.drain(timeout, unit);
    }

    Map<String, String> journalSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        ownerRef.operationJournal.snapshot().forEach((operationId, entry) -> snapshot.put(operationId, entry.phase()));
        return Map.copyOf(snapshot);
    }

    void finishInFlight(String journalKey) {
        ownerRef.operationJournal.release(journalKey);
    }

    void pruneJournal() {
        ownerRef.operationJournal.prune(entry -> {
            AttemptResult result = entry.payload() == null ? null : entry.payload().result();
            return result != null && !result.compensationPending();
        });
    }

    String resolveOperationId(AttemptContext context) {
        String supplied = context == null ? "" : Texts.trim(context.operationId());
        return Texts.isBlank(supplied) ? UUID.randomUUID().toString() : supplied;
    }

    String journalKey(Player player, String operationId) {
        return (player == null ? "-" : player.getUniqueId().toString()) + ":" + operationId;
    }

    int attemptFingerprint(AttemptContext context) {
        return context == null ? 0 : Objects.hash(context.targetItem(), context.materialInputs());
    }

    CompletableFuture<Boolean> triggerSuccessActions(Player player, StrengthenRecipe recipe, String resultSlotId, CoreActionItemTarget itemTarget, int star, int temper) {
        return ownerRef.triggerSuccessActions(player, recipe, resultSlotId, itemTarget, star, temper, "");
    }

    CompletableFuture<Boolean> triggerSuccessActions(Player player, StrengthenRecipe recipe, String resultSlotId, CoreActionItemTarget itemTarget, int star, int temper, String operationId) {
        return ownerRef.actionCoordinator.triggerSuccessActions(
                player, recipe, resultSlotId, itemTarget, star, temper, operationId);
    }

    CompletableFuture<Boolean> triggerFailureActions(Player player, StrengthenRecipe recipe, String resultSlotId, CoreActionItemTarget itemTarget, int wasStar, int resultStar, int temper, boolean dropped, boolean protectionApplied) {
        return ownerRef.triggerFailureActions(player, recipe, resultSlotId, itemTarget, wasStar, resultStar, temper,
                dropped, protectionApplied, "");
    }

    CompletableFuture<Boolean> triggerFailureActions(Player player, StrengthenRecipe recipe, String resultSlotId, CoreActionItemTarget itemTarget, int wasStar, int resultStar, int temper, boolean dropped, boolean protectionApplied, String operationId) {
        return ownerRef.actionCoordinator.triggerFailureActions(player, recipe, resultSlotId, itemTarget, wasStar, resultStar,
                temper, dropped, protectionApplied, operationId);
    }

    AttemptPreview ineligiblePreview(String errorKey, StrengthenState state) {
        return ownerRef.ineligiblePreview(errorKey, state, null);
    }

    AttemptPreview ineligiblePreview(String errorKey, StrengthenState state, StrengthenRecipe recipe) {
        int currentStar = state == null ? 0 : state.currentStar();
        int temper = state == null ? 0 : state.temperLevel();
        return new AttemptPreview(false, errorKey, state, recipe, currentStar, currentStar, 0D, List.of(),
                currentStar, temper, false, 0, Map.of(), Set.of(), List.of(), List.of());
    }

    Set<Integer> parseFlagSet(Object raw) {
        Set<Integer> flags = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Integer value = Numbers.tryParseInt(entry, null);
                if (value != null) {
                    flags.add(value);
                }
            }
        } else if (raw != null) {
            Integer value = Numbers.tryParseInt(raw, null);
            if (value != null) {
                flags.add(value);
            }
        }
        return flags;
    }

    void applyStrengthenOperations(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state, ActionContext actionContext) {
        Object nameActions = recipe.cumulativeNameActions(state.currentStar(), state.branchPath());
        Object loreActions = recipe.cumulativeLoreActions(state.currentStar(), state.branchPath());
        if ((nameActions instanceof List<?> nameList && nameList.isEmpty())
                && (loreActions instanceof List<?> loreList && loreList.isEmpty())) {
            return;
        }
        String operationId = StrengthenAttemptService.OPERATION_NAMESPACE + ":" + recipe.id() + ":star_" + state.currentStar();
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> mixedVariables = recipe.cumulativeMixedVariables(state.currentStar(), state.branchPath());
        if (mixedVariables != null) {
            variables.putAll(mixedVariables);
        }
        variables.put("star", state.currentStar());
        variables.put("temper", state.temperLevel());
        variables.put("max_temper", recipe.limits().maxTemper());
        ownerRef.operationLedger.revertAll(itemStack, StrengthenAttemptService.OPERATION_NAMESPACE);
        ownerRef.operationLedger.apply(actionContext, itemStack, operationId, StrengthenAttemptService.OPERATION_NAMESPACE, nameActions, loreActions, variables);
    }

    String buildMaterialsSignature(AttemptPreview preview) {
        return SignatureUtil.stableSignature(StrengthenAttemptService.materialSignatureRows(
                preview == null ? List.of() : preview.optionalMaterials()));
    }

    static List<Object> materialSignatureRows(List<AttemptMaterial> materials) {
        Map<String, Integer> amountsByIdentity = new TreeMap<>();
        Map<String, MaterialSignatureEntry> identities = new TreeMap<>();
        if (materials != null) {
            for (AttemptMaterial material : materials) {
                if (material == null || material.consumedAmount() <= 0) {
                    continue;
                }
                MaterialSignatureEntry identity = new MaterialSignatureEntry(
                        material.materialId(), material.countKey(), 0);
                String key = identity.materialId() + "\u0000" + identity.countKey();
                identities.putIfAbsent(key, identity);
                amountsByIdentity.merge(key, material.consumedAmount(), Integer::sum);
            }
        }
        List<Object> signatureData = new ArrayList<>();
        for (Map.Entry<String, MaterialSignatureEntry> entry : identities.entrySet()) {
            MaterialSignatureEntry identity = entry.getValue();
            signatureData.add(new MaterialSignatureEntry(identity.materialId(), identity.countKey(),
                    amountsByIdentity.getOrDefault(entry.getKey(), 0)).row());
        }
        return List.copyOf(signatureData);
    }

    String renderCosts(List<AttemptCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return ownerRef.freeCostLabel();
        }
        List<String> parts = new ArrayList<>();
        for (AttemptCost cost : costs) {
            parts.add(cost.amount() + " " + cost.displayName());
        }
        return String.join(", ", parts);
    }

    String freeCostLabel() {
        var message = ownerRef.plugin.messageService() == null
                ? ""
                : ownerRef.plugin.messageService().message("strengthen.misc.free_cost");
        return Texts.isBlank(message) ? "免费" : message;
    }

    Set<Integer> collectFirstReach(Set<Integer> currentFlags, int targetStar) {
        if (targetStar <= 0 || currentFlags != null && currentFlags.contains(targetStar)) {
            return Set.of();
        }
        return Set.of(targetStar);
    }

    void applyPdcAttributes(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state) {
        ownerRef.pdcAttributeWriter.applyPdcAttributes(itemStack, recipe, state);
    }

    void clearPdcAttributes(ItemStack itemStack) {
        ownerRef.pdcAttributeWriter.clearPdcAttributes(itemStack);
    }

    void preserveOtherAttributePayloads(ItemStack original, ItemStack rebuilt) {
        ownerRef.pdcAttributeWriter.preserveOtherAttributePayloads(original, rebuilt);
    }

    static StrengthenAttemptService.StarProgress collectStarProgress(Set<Integer> currentFlags, Set<Integer> reachedNow) {
        Set<Integer> updated = new LinkedHashSet<>(currentFlags == null ? Set.of() : currentFlags);
        Set<Integer> newlyReached = new LinkedHashSet<>();
        if (reachedNow != null) {
            for (Integer stage : reachedNow) {
                if (stage != null && updated.add(stage)) {
                    newlyReached.add(stage);
                }
            }
        }
        return new StrengthenAttemptService.StarProgress(Set.copyOf(updated), Set.copyOf(newlyReached));
    }

    static ConditionGroup toCoreConditionGroup(StrengthenConditionGroup group) {
        if (group == null) {
            return ConditionGroup.empty();
        }
        List<ConditionNode> nodes = new ArrayList<>();
        for (StrengthenConditionNode node : group.conditions()) {
            ConditionNode converted = StrengthenAttemptService.toCoreConditionNode(node);
            if (converted != null) {
                nodes.add(converted);
            }
        }
        return new ConditionGroup(group.conditionType(), group.requiredCount(), nodes);
    }

    static ConditionNode toCoreConditionNode(StrengthenConditionNode node) {
        if (node == null) {
            return null;
        }
        if (node.groupNode()) {
            return ConditionNode.group(StrengthenAttemptService.toCoreConditionGroup(node.group()));
        }
        return new ConditionNode(node.type(), node.expression(), null, node.data());
    }

}

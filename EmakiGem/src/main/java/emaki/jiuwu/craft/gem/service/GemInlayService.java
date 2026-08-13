package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.event.GemExtractCompletedEvent;
import emaki.jiuwu.craft.gem.api.event.GemExtractEvent;
import emaki.jiuwu.craft.gem.api.event.GemInlayCompletedEvent;
import emaki.jiuwu.craft.gem.api.event.GemInlayEvent;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.model.ResonanceEffects;

public final class GemInlayService {

    public record Result(boolean success, String messageKey, Map<String, Object> placeholders, boolean inputConsumed) {

        public Result {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static Result success(String messageKey, Map<String, Object> placeholders) {
            return new Result(true, messageKey, placeholders, true);
        }

        public static Result failure(String messageKey, Map<String, Object> placeholders) {
            return new Result(false, messageKey, placeholders, false);
        }

        public static Result failure(String messageKey, Map<String, Object> placeholders, boolean inputConsumed) {
            return new Result(false, messageKey, placeholders, inputConsumed);
        }
    }

    public record InlayResult(Result result,
            ItemStack updatedEquipment,
            String operationId,
            Supplier<CompletionStage<Boolean>> commitAction) {

        public InlayResult(Result result, ItemStack updatedEquipment) {
            this(result, updatedEquipment, "", () -> CompletableFuture.completedFuture(false));
        }

        public InlayResult(Result result, ItemStack updatedEquipment, String operationId) {
            this(result, updatedEquipment, operationId, () -> CompletableFuture.completedFuture(false));
        }

        public InlayResult {
            result = result == null ? Result.failure("general.unknown_error", Map.of()) : result;
            operationId = operationId == null ? "" : operationId;
            commitAction = commitAction == null
                    ? () -> CompletableFuture.completedFuture(false)
                    : commitAction;
        }

        public CompletionStage<Boolean> commit() {
            return commitAction.get();
        }
    }

    private static final String OPERATION_NAMESPACE = "gem";

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final GemItemMatcher itemMatcher;
    private final GemStateService stateService;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;
    private final ItemOperationLedger operationLedger;
    private final GemOperationJournal operationJournal;

    public GemInlayService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemStateService stateService,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.itemMatcher = itemMatcher;
        this.stateService = stateService;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
        this.operationLedger = new ItemOperationLedger(plugin::debugLogger);
        this.operationJournal = GemOperationJournal.forPlugin(plugin, scheduling);
    }

    public InlayResult inlayDirect(Player actor,
            ItemStack equipment,
            ItemStack gemItem,
            int slotIndex,
            boolean bypassCost,
            boolean preserveInputOnFailure) {
        if (actor == null) {
            return new InlayResult(Result.failure("general.player_not_found", Map.of()), equipment);
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return new InlayResult(Result.failure("gem.error.invalid_equipment", Map.of("player", actor.getName())), equipment);
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        GemItemDefinition.SocketSlot slot = itemDefinition.slot(slotIndex);
        if (slot == null) {
            return new InlayResult(Result.failure("command.inlay.slot_not_found", Map.of("slot", slotIndex)), equipment);
        }
        if (!currentState.isOpened(slotIndex)) {
            return new InlayResult(Result.failure("command.inlay.slot_not_opened", Map.of("slot", slotIndex)), equipment);
        }
        if (currentState.assignment(slotIndex) != null) {
            return new InlayResult(Result.failure("command.inlay.slot_occupied", Map.of("slot", slotIndex)), equipment);
        }
        GemItemInstance instance = itemMatcher.readGemInstance(gemItem);
        GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (gemDefinition == null) {
            return new InlayResult(Result.failure("command.inlay.hold_gem", Map.of()), equipment);
        }
        if (!itemDefinition.allowsGemType(gemDefinition.gemType())) {
            return new InlayResult(Result.failure("command.inlay.gem_type_blocked", Map.of("type", gemDefinition.gemType())), equipment);
        }
        if (!gemDefinition.supportsSocketType(slot.type())) {
            return new InlayResult(Result.failure("command.inlay.socket_incompatible", Map.of("slot", slotIndex, "type", slot.type())), equipment);
        }
        if (itemDefinition.maxSameType() > 0
                && stateService.countAssignmentsByType(itemDefinition, currentState).getOrDefault(gemDefinition.gemType(), 0) >= itemDefinition.maxSameType()) {
            return new InlayResult(Result.failure("command.inlay.max_same_type", Map.of("type", gemDefinition.gemType())), equipment);
        }
        if (stateService.countAssignmentsByGemId(currentState, gemDefinition.id()) >= itemDefinition.maxSameId()) {
            return new InlayResult(Result.failure("command.inlay.max_same_id", Map.of("gem", gemDefinition.id())), equipment);
        }
        GemStateService.RelationshipCheck relationshipCheck = stateService.validateInlayRelationships(currentState, gemDefinition);
        if (!relationshipCheck.allowed()) {
            return new InlayResult(Result.failure(relationshipCheck.messageKey(), relationshipCheck.placeholders()), equipment);
        }
        if (!evaluateConditions(actor)) {
            return new InlayResult(Result.failure("gem.error.condition_not_met", Map.of()), equipment);
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", actor.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        placeholders.put("level", instance.level());
        double successChance = resolveSuccessChance(gemDefinition);
        placeholders.put("success_rate", successChance);

        String operationId = UUID.randomUUID().toString();
        GemInlayEvent inlayEvent = new GemInlayEvent(operationId, actor, equipment, gemItem, slotIndex,
                gemDefinition.id(), instance.level(), successChance);

        if (scheduling.ownsEntity(actor)) {
            Bukkit.getPluginManager().callEvent(inlayEvent);
            if (inlayEvent.isCancelled()) {
                return new InlayResult(Result.failure("gem.operation.cancelled", placeholders), equipment, operationId);
            }
            successChance = inlayEvent.getSuccessChance();
            placeholders.put("success_rate", successChance);
        }

        operationJournal.begin(operationId, "inlay", actor.getUniqueId());
        String failureAction = Texts.lower(plugin.appConfig().inlaySuccess().failureAction());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost && shouldChargeBeforeRoll(failureAction)) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, Map.of());
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                if (chargeResult.compensationComplete()) {
                    fireInlayCompleted(operationId, actor, false, equipment, false, slotIndex,
                            gemDefinition.id(), instance.level(), chargeResult.errorKey());
                }
                return new InlayResult(Result.failure(chargeResult.errorKey(), chargeResult.placeholders()),
                        equipment, operationId);
            }
            operationJournal.charged(operationId, chargeResult);
        } else if (bypassCost) {
            operationJournal.advance(operationId, GemOperationJournal.Phase.CHARGED);
        }
        if (!rollSuccess(successChance)) {
            boolean transactionComplete;
            if (preserveInputOnFailure && chargeResult != null) {
                GemEconomyService.RefundResult refundResult = economyService.refundDetailed(actor, chargeResult);
                operationJournal.completeAfterRefund(operationId, "inlay_chance_refund_failed", refundResult);
                transactionComplete = refundResult.success();
            } else {
                operationJournal.advance(operationId, GemOperationJournal.Phase.COMPLETED);
                transactionComplete = true;
            }
            boolean inputConsumed = !preserveInputOnFailure && shouldConsumeGemOnFailure(failureAction);
            if (transactionComplete) {
                fireInlayCompleted(operationId, actor, false, equipment, inputConsumed, slotIndex,
                        gemDefinition.id(), instance.level(), "gem.inlay.chance_failed");
            }
            return new InlayResult(Result.failure("command.inlay.chance_failed", placeholders, inputConsumed),
                    equipment, operationId);
        }
        if (!bypassCost && chargeResult == null) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, Map.of());
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                if (chargeResult.compensationComplete()) {
                    fireInlayCompleted(operationId, actor, false, equipment, false, slotIndex,
                            gemDefinition.id(), instance.level(), chargeResult.errorKey());
                }
                return new InlayResult(Result.failure(chargeResult.errorKey(), chargeResult.placeholders()),
                        equipment, operationId);
            }
            operationJournal.charged(operationId, chargeResult);
        }
        GemState nextState = currentState.withAssignment(slotIndex, instance);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            GemEconomyService.RefundResult refundResult = chargeResult == null
                    ? GemEconomyService.RefundResult.complete()
                    : economyService.refundDetailed(actor, chargeResult);
            operationJournal.completeAfterRefund(operationId, "inlay_apply_refund_failed", refundResult);
            if (refundResult.success()) {
                fireInlayCompleted(operationId, actor, false, equipment, false, slotIndex,
                        gemDefinition.id(), instance.level(), "gem.inlay.apply_failed");
            }
            return new InlayResult(Result.failure("command.inlay.apply_failed", Map.of("player", actor.getName())),
                    equipment, operationId);
        }
        applyGemOperations(rebuilt, gemDefinition, instance, slotIndex, placeholders);
        Runnable completedEvent = () -> fireInlayCompleted(operationId, actor, true, rebuilt, true, slotIndex,
                gemDefinition.id(), instance.level(), "");
        return new InlayResult(Result.success("command.inlay.success", placeholders), rebuilt, operationId,
                commitAfterActions(operationId, actor, "gem_inlay_success",
                        gemDefinition.inlaySuccessActions(), placeholders, completedEvent));
    }

    private Supplier<CompletionStage<Boolean>> commitAfterActions(String operationId,
            Player actor,
            String phase,
            List<String> actions,
            Map<String, Object> placeholders,
            Runnable completedEvent) {
        AtomicBoolean committed = new AtomicBoolean(false);
        List<String> safeActions = actions == null ? List.of() : List.copyOf(actions);
        Map<String, Object> safePlaceholders = placeholders == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(placeholders));
        return () -> {
            if (!committed.compareAndSet(false, true)) {
                return CompletableFuture.completedFuture(false);
            }
            operationJournal.advance(operationId, GemOperationJournal.Phase.STATE_COMMITTED);
            CompletionStage<Boolean> completion = operationJournal.completeAfterActions(operationId,
                    actionCoordinator.executeAsync(actor, phase, safeActions, safePlaceholders));
            completion.thenAccept(completed -> {
                if (Boolean.TRUE.equals(completed) && completedEvent != null) {
                    completedEvent.run();
                }
            });
            return completion;
        };
    }

    public ExtractDirectResult extractDirect(Player actor,
            ItemStack equipment,
            int slotIndex,
            boolean bypassCost) {
        if (actor == null) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("general.player_not_found", Map.of()), equipment, null);
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("gem.error.invalid_equipment", Map.of("player", actor.getName())), equipment, null);
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        GemItemInstance instance = currentState.assignment(slotIndex);
        GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (instance == null || gemDefinition == null) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("command.extract.slot_empty", Map.of("slot", slotIndex)), equipment, null);
        }
        GemStateService.RelationshipCheck relationshipCheck = stateService.validateExtractionRelationships(currentState, slotIndex);
        if (!relationshipCheck.allowed()) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure(relationshipCheck.messageKey(), relationshipCheck.placeholders()), equipment, null);
        }
        if (!evaluateConditions(actor)) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("gem.error.condition_not_met", Map.of()), equipment, null);
        }

        String operationId = UUID.randomUUID().toString();
        if (scheduling.ownsEntity(actor)) {
            GemExtractEvent extractEvent = new GemExtractEvent(operationId, actor, equipment, slotIndex,
                    gemDefinition.id(), instance.level(), gemDefinition.extractReturn().mode());
            Bukkit.getPluginManager().callEvent(extractEvent);
            if (extractEvent.isCancelled()) {
                return new ExtractDirectResult(
                        GemExtractService.Result.failure("gem.operation.cancelled", Map.of()), equipment, null,
                        operationId);
            }
        }
        operationJournal.begin(operationId, "extract_direct", actor.getUniqueId());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            chargeResult = economyService.charge(actor, gemDefinition.extractCost(), costVariables(gemDefinition, instance.level(), instance.level()));
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                return new ExtractDirectResult(
                        GemExtractService.Result.failure(chargeResult.errorKey(), chargeResult.placeholders()),
                        equipment, null, operationId);
            }
            operationJournal.charged(operationId, chargeResult);
        } else {
            operationJournal.advance(operationId, GemOperationJournal.Phase.CHARGED);
        }
        GemState nextState = currentState.withAssignment(slotIndex, null);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            GemEconomyService.RefundResult refundResult = chargeResult == null
                    ? GemEconomyService.RefundResult.complete()
                    : economyService.refundDetailed(actor, chargeResult);
            operationJournal.completeAfterRefund(operationId, "extract_apply_refund_failed", refundResult);
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("command.extract.apply_failed", Map.of("player", actor.getName())),
                    equipment, null, operationId);
        }
        revertGemOperations(rebuilt, slotIndex);
        ItemStack returned = createReturnedGem(gemDefinition, instance);
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", actor.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        Runnable completedEvent = () -> fireExtractCompleted(operationId, actor, rebuilt, returned, slotIndex,
                gemDefinition.id(), instance.level(), gemDefinition.extractReturn().mode());
        return new ExtractDirectResult(
                GemExtractService.Result.success("command.extract.success", placeholders), rebuilt, returned,
                operationId,
                commitAfterActions(operationId, actor, "gem_extract_success",
                        gemDefinition.extractSuccessActions(), placeholders, completedEvent));
    }

    public record ExtractDirectResult(GemExtractService.Result result,
            ItemStack updatedEquipment,
            ItemStack returnedGem,
            String operationId,
            Supplier<CompletionStage<Boolean>> commitAction) {

        public ExtractDirectResult(GemExtractService.Result result,
                ItemStack updatedEquipment,
                ItemStack returnedGem) {
            this(result, updatedEquipment, returnedGem, "", () -> CompletableFuture.completedFuture(false));
        }

        public ExtractDirectResult(GemExtractService.Result result,
                ItemStack updatedEquipment,
                ItemStack returnedGem,
                String operationId) {
            this(result, updatedEquipment, returnedGem, operationId,
                    () -> CompletableFuture.completedFuture(false));
        }

        public ExtractDirectResult {
            operationId = operationId == null ? "" : operationId;
            commitAction = commitAction == null
                    ? () -> CompletableFuture.completedFuture(false)
                    : commitAction;
        }

        public CompletionStage<Boolean> commit() {
            return commitAction.get();
        }
    }

    private void fireInlayCompleted(String operationId,
            Player actor,
            boolean successful,
            ItemStack finalEquipment,
            boolean inputConsumed,
            int slotIndex,
            String gemId,
            int gemLevel,
            String reasonKey) {
        fireCompletedEvent(actor, () -> Bukkit.getPluginManager().callEvent(
                new GemInlayCompletedEvent(operationId, actor, successful, finalEquipment, inputConsumed,
                        slotIndex, gemId, gemLevel, reasonKey == null ? "" : reasonKey)));
    }

    private void fireExtractCompleted(String operationId,
            Player actor,
            ItemStack finalEquipment,
            ItemStack returnedGem,
            int slotIndex,
            String gemId,
            int gemLevel,
            String returnMode) {
        fireCompletedEvent(actor, () -> Bukkit.getPluginManager().callEvent(
                new GemExtractCompletedEvent(operationId, actor, finalEquipment, returnedGem,
                        slotIndex, gemId, gemLevel, returnMode)));
    }

    private void fireCompletedEvent(Player actor, Runnable eventCall) {
        if (actor == null || eventCall == null || plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (scheduling.ownsEntity(actor)) {
            eventCall.run();
            return;
        }
        if (!actor.isOnline()) {
            plugin.getLogger().warning("Skipped gem completed event because operation actor is offline: "
                    + actor.getUniqueId());
            return;
        }
        scheduling.runForEntity(plugin, actor, eventCall,
                () -> plugin.getLogger().warning("Skipped gem completed event because actor scheduling retired: "
                        + actor.getUniqueId()));
    }

    private ItemStack createReturnedGem(GemDefinition gemDefinition, GemItemInstance instance) {
        String mode = gemDefinition.extractReturn().mode();
        if ("destroy".equalsIgnoreCase(mode)) {
            return null;
        }
        int level = instance.level();
        if ("downgrade".equalsIgnoreCase(mode)
                && ThreadLocalRandom.current().nextDouble() < gemDefinition.extractReturn().degradedChance()) {
            level -= gemDefinition.extractReturn().downgradeLevels();
            if (level <= 0) {
                return null;
            }
        }
        return plugin.itemFactory().createGemItem(gemDefinition, level, 1);
    }

    private GemEconomyService.ChargeResult chargeInlayCost(Player actor,
            GemDefinition gemDefinition,
            GemItemInstance instance,
            Map<Integer, ItemStack> providedMaterials) {
        GemDefinition.CostConfig costConfig = gemDefinition == null ? null : gemDefinition.inlayCost();
        return economyService.charge(
                actor,
                costConfig == null ? List.of() : costConfig.currencies(),
                costConfig == null ? List.of() : costConfig.materials(),
                costVariables(gemDefinition, instance.level(), instance.level()),
                providedMaterials
        );
    }

    private double resolveSuccessChance(GemDefinition definition) {
        var config = plugin.appConfig().inlaySuccess();
        if (!config.enabled()) {
            return 100D;
        }
        double configuredChance = config.levelChances().getOrDefault(
                definition.level(),
                config.defaultChance()
        );
        if (Texts.isBlank(config.rateFormula())) {
            return clampChance(configuredChance);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("default_chance", config.defaultChance());
        variables.put("level_chance", configuredChance);
        variables.put("configured_chance", configuredChance);
        variables.put("level", definition.level());
        return clampChance(ExpressionEngine.evaluate(config.rateFormula(), variables));
    }

    private boolean rollSuccess(double chance) {
        return ThreadLocalRandom.current().nextDouble(100D) < clampChance(chance);
    }

    private boolean shouldChargeBeforeRoll(String failureAction) {
        return "destroy_gem".equalsIgnoreCase(failureAction);
    }

    private boolean shouldConsumeGemOnFailure(String failureAction) {
        return "destroy_gem".equalsIgnoreCase(failureAction) || "destroy_both".equalsIgnoreCase(failureAction);
    }

    private Map<String, Object> costVariables(GemDefinition definition, int currentLevel, int targetLevel) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("level", definition == null ? 1 : definition.level());
        variables.put("current_level", Math.max(1, currentLevel));
        variables.put("target_level", Math.max(1, targetLevel));
        return Map.copyOf(variables);
    }

    private double clampChance(double chance) {
        return Math.max(0D, Math.min(100D, chance));
    }

    private void applyGemOperations(ItemStack itemStack, GemDefinition gemDefinition, GemItemInstance instance, int slotIndex, Map<String, Object> placeholders) {
        Object nameActions = gemDefinition.nameActionsForLevel(instance.level());
        Object loreActions = gemDefinition.loreActionsForLevel(instance.level());
        if (nameActions != null || loreActions != null) {
            String operationId = OPERATION_NAMESPACE + ":slot_" + slotIndex;
            Map<String, Object> variables = new LinkedHashMap<>(placeholders);
            variables.putAll(plugin.itemFactory().gemPlaceholders(gemDefinition, instance.level(), null));
            operationLedger.apply(itemStack, operationId, OPERATION_NAMESPACE, nameActions, loreActions, variables);
        }
        applyResonanceOperations(itemStack);
    }

    private void applyResonanceOperations(ItemStack itemStack) {
        GemResonanceService resonanceService = plugin.resonanceService();
        if (resonanceService == null) {
            return;
        }
        operationLedger.revertAll(itemStack, OPERATION_NAMESPACE + ".resonance");
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(itemStack);
        if (itemDefinition == null) {
            return;
        }
        GemState state = stateService.resolveState(itemStack, itemDefinition);
        if (state == null) {
            return;
        }
        List<GemDefinition> inlaidGems = new ArrayList<>();
        for (GemItemInstance inst : state.socketAssignments().values()) {
            if (inst == null) {
                continue;
            }
            GemDefinition def = plugin.gemLoader().get(inst.gemId());
            if (def != null) {
                inlaidGems.add(def);
            }
        }
        List<GemResonanceDefinition> activeResonances = resonanceService.evaluate(inlaidGems);
        for (GemResonanceDefinition resonance : activeResonances) {
            ResonanceEffects effects = resonance.effects();
            if (effects == null) {
                continue;
            }
            Object resNameActions = effects.nameActions();
            Object resLoreActions = effects.loreActions();
            if (resNameActions == null && resLoreActions == null) {
                continue;
            }
            String resOperationId = OPERATION_NAMESPACE + ".resonance:" + resonance.id();
            operationLedger.apply(itemStack, resOperationId, OPERATION_NAMESPACE + ".resonance", resNameActions, resLoreActions, Map.of());
        }
    }

    private void revertGemOperations(ItemStack itemStack, int slotIndex) {
        String operationId = OPERATION_NAMESPACE + ":slot_" + slotIndex;
        operationLedger.revert(itemStack, operationId);
        applyResonanceOperations(itemStack);
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().emptyGroup()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                text -> PlaceholderRenderer.renderPapi(player, text, null, "gem_inlay"),
                config.invalidAsFailure(),
                ConditionContext.of(player)
        );
    }
}

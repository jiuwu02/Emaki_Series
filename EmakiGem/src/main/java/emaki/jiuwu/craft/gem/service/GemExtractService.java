package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.event.GemExtractCompletedEvent;
import emaki.jiuwu.craft.gem.api.event.GemExtractEvent;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemExtractService {

    public record Result(boolean success, String messageKey, Map<String, Object> placeholders) {

        public Result {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static Result success(String messageKey, Map<String, Object> placeholders) {
            return new Result(true, messageKey, placeholders);
        }

        public static Result failure(String messageKey, Map<String, Object> placeholders) {
            return new Result(false, messageKey, placeholders);
        }
    }

    private static final String OPERATION_NAMESPACE = "gem";

    private final EmakiGemPlugin plugin;
    private final EmakiScheduling scheduling;
    private final GemItemMatcher itemMatcher;
    private final GemItemFactory itemFactory;
    private final GemStateService stateService;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;
    private final ItemOperationLedger operationLedger;
    private final GemOperationJournal operationJournal;

    public GemExtractService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemItemFactory itemFactory,
            GemStateService stateService,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.itemMatcher = itemMatcher;
        this.itemFactory = itemFactory;
        this.stateService = stateService;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
        this.operationLedger = new ItemOperationLedger(plugin::debugLogger);
        this.operationJournal = GemOperationJournal.forPlugin(plugin, scheduling);
    }

    public Result extract(Player actor, Player target, int slotIndex, boolean bypassCost) {
        if (actor == null || target == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        ItemStack equipment = target.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Result.failure("gem.error.invalid_equipment", Map.of("player", target.getName()));
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        GemItemInstance instance = currentState.assignment(slotIndex);
        GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (instance == null || gemDefinition == null) {
            return Result.failure("command.extract.slot_empty", Map.of("slot", slotIndex));
        }
        GemStateService.RelationshipCheck relationshipCheck = stateService.validateExtractionRelationships(currentState, slotIndex);
        if (!relationshipCheck.allowed()) {
            return Result.failure(relationshipCheck.messageKey(), relationshipCheck.placeholders());
        }
        if (!evaluateConditions(actor)) {
            return Result.failure("gem.error.condition_not_met", Map.of());
        }

        String operationId = UUID.randomUUID().toString();
        if (scheduling.ownsEntity(target)) {
            GemExtractEvent extractEvent = new GemExtractEvent(operationId, target, equipment, slotIndex,
                    gemDefinition.id(), instance.level(), gemDefinition.extractReturn().mode());
            Bukkit.getPluginManager().callEvent(extractEvent);
            if (extractEvent.isCancelled()) {
                return Result.failure("gem.operation.cancelled", Map.of());
            }
        }
        operationJournal.begin(operationId, "extract", actor.getUniqueId());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            chargeResult = economyService.charge(actor, gemDefinition.extractCost(), costVariables(gemDefinition, instance.level()));
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                return Result.failure(chargeResult.errorKey(), chargeResult.placeholders());
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
            return Result.failure("command.extract.apply_failed", Map.of("player", target.getName()));
        }
        operationLedger.revert(rebuilt, OPERATION_NAMESPACE + ":slot_" + slotIndex);
        target.getInventory().setItemInMainHand(rebuilt);
        operationJournal.advance(operationId, GemOperationJournal.Phase.STATE_COMMITTED);
        ItemStack returned = createReturnedGem(gemDefinition, instance);
        if (returned != null) {
            InventoryItemUtil.giveOrDrop(target, returned);
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        operationJournal.completeAfterActions(operationId,
                actionCoordinator.executeAsync(target, "gem_extract_success", gemDefinition.extractSuccessActions(), placeholders))
                .thenAccept(completed -> {
                    if (Boolean.TRUE.equals(completed)) {
                        fireCompletedEvent(target, new GemExtractCompletedEvent(operationId, target, rebuilt, returned,
                                slotIndex, gemDefinition.id(), instance.level(), gemDefinition.extractReturn().mode()));
                    }
                });
        return Result.success("command.extract.success", placeholders);
    }

    private void fireCompletedEvent(Player target, GemExtractCompletedEvent event) {
        if (target == null || event == null || plugin == null || !plugin.isEnabled()) {
            return;
        }
        Runnable eventCall = () -> Bukkit.getPluginManager().callEvent(event);
        if (scheduling.ownsEntity(target)) {
            eventCall.run();
            return;
        }
        if (!target.isOnline()) {
            plugin.getLogger().warning("Skipped gem extraction completed event because target is offline: "
                    + target.getUniqueId());
            return;
        }
        scheduling.runForEntity(plugin, target, eventCall,
                () -> plugin.getLogger().warning("Skipped gem extraction completed event because scheduling retired: "
                        + target.getUniqueId()));
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
        return itemFactory.createGemItem(gemDefinition, level, 1);
    }

    private Map<String, Object> costVariables(GemDefinition definition, int level) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("level", definition == null ? 1 : definition.level());
        variables.put("current_level", Math.max(1, level));
        variables.put("target_level", Math.max(1, level));
        return Map.copyOf(variables);
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().emptyGroup()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                text -> PlaceholderRenderer.renderPapi(player, text, null, "gem_extract"),
                config.invalidAsFailure(),
                ConditionContext.of(player)
        );
    }
}

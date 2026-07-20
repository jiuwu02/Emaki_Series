package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.event.GemUpgradeEvent;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemUpgradeService {

    public record UpgradePreview(boolean eligible,
            String errorKey,
            GemDefinition definition,
            GemItemInstance instance,
            int targetLevel,
            GemDefinition.GemUpgradeLevel upgradeLevel) {

        public static UpgradePreview failure(String errorKey) {
            return new UpgradePreview(false, errorKey, null, null, 0, null);
        }

        public static UpgradePreview success(GemDefinition definition,
                GemItemInstance instance,
                int targetLevel,
                GemDefinition.GemUpgradeLevel upgradeLevel) {
            return new UpgradePreview(true, "", definition, instance, targetLevel, upgradeLevel);
        }
    }

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

    private final EmakiGemPlugin plugin;
    private final ThreadOwnership threadOwnership;
    private final GemItemFactory itemFactory;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;
    private final GemOperationJournal operationJournal;

    public GemUpgradeService(EmakiGemPlugin plugin,
            GemItemFactory itemFactory,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.threadOwnership = threadOwnership;
        this.itemFactory = itemFactory;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
        this.operationJournal = GemOperationJournal.forPlugin(plugin, executionDispatcher, threadOwnership);
    }

    public Result upgradeHeldGem(Player player, boolean bypassCost) {
        return upgradeHeldGem(player, bypassCost, Map.of());
    }

    public Result upgradeHeldGem(Player player, boolean bypassCost, Map<Integer, ItemStack> providedMaterials) {
        return upgradeHeldGem(player, bypassCost, providedMaterials, true);
    }

    public UpgradeItemResult upgradeGemItemWithGuiMaterials(Player player,
            ItemStack itemStack,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials) {
        return upgradeGemItemDirect(player, itemStack, bypassCost, providedMaterials, false);
    }

    public record UpgradeItemResult(Result result, ItemStack updatedItem, Runnable commitAction) {

        public UpgradeItemResult(Result result, ItemStack updatedItem) {
            this(result, updatedItem, () -> {
            });
        }

        public UpgradeItemResult {
            commitAction = commitAction == null ? () -> {
            } : commitAction;
        }

        public void commit() {
            commitAction.run();
        }
    }

    private Result upgradeHeldGem(Player player,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        if (player == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        return upgradeGemItem(player, player.getInventory().getItemInMainHand(), bypassCost, providedMaterials, allowInventoryFallback);
    }

    public Result upgradeGemItem(Player player, ItemStack itemStack, boolean bypassCost) {
        return upgradeGemItem(player, itemStack, bypassCost, Map.of());
    }

    public Result upgradeGemItem(Player player,
            ItemStack itemStack,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials) {
        return upgradeGemItem(player, itemStack, bypassCost, providedMaterials, true);
    }

    private Result upgradeGemItem(Player player,
            ItemStack itemStack,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        UpgradeItemResult direct = upgradeGemItemDirect(player, itemStack, bypassCost, providedMaterials, allowInventoryFallback);
        if (direct.updatedItem() != itemStack) {
            player.getInventory().setItemInMainHand(direct.updatedItem() == null || direct.updatedItem().getType().isAir() ? null : direct.updatedItem());
        }
        direct.commit();
        return direct.result();
    }

    private UpgradeItemResult upgradeGemItemDirect(Player player,
            ItemStack itemStack,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        if (player == null) {
            return new UpgradeItemResult(Result.failure("general.player_not_found", Map.of()), itemStack);
        }
        UpgradePreview preview = preview(itemStack);
        if (!preview.eligible()) {
            return new UpgradeItemResult(Result.failure(preview.errorKey(), Map.of()), itemStack);
        }
        if (!evaluateConditions(player)) {
            return new UpgradeItemResult(Result.failure("gem.error.condition_not_met", Map.of()), itemStack);
        }
        GemDefinition definition = preview.definition();
        GemItemInstance instance = preview.instance();
        GemDefinition.UpgradeConfig upgradeConfig = definition.upgrade();
        int targetLevel = preview.targetLevel();
        GemDefinition.GemUpgradeLevel upgradeLevel = preview.upgradeLevel();
        double successChance = effectiveSuccessChance(definition, targetLevel, upgradeLevel.successChance());
        // 宝石升级对外开放，可取消、可改成功率；在扣费前派发以保证取消即不扣费，物品形态 slotIndex 传 -1。
        if (threadOwnership.isEntityOwned(player)) {
            GemUpgradeEvent upgradeEvent = new GemUpgradeEvent(player, itemStack, definition.id(),
                    instance.level(), targetLevel, -1, successChance);
            org.bukkit.Bukkit.getPluginManager().callEvent(upgradeEvent);
            if (upgradeEvent.isCancelled()) {
                return new UpgradeItemResult(Result.failure("gem.error.condition_not_met", Map.of()), itemStack);
            }
            successChance = upgradeEvent.getSuccessChance();
        }
        String operationId = operationJournal.begin("upgrade_item", player.getUniqueId());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            List<GemDefinition.CurrencyCost> currencies = new ArrayList<>(effectiveCurrencies(upgradeConfig, upgradeLevel));
            List<GemDefinition.MaterialCost> materials = new ArrayList<>(upgradeLevel.materials());
            Map<String, Object> variables = costVariables(definition, instance.level(), targetLevel);
            chargeResult = allowInventoryFallback
                    ? economyService.charge(player, currencies, materials, variables, providedMaterials)
                    : economyService.chargeProvidedOnly(player, currencies, materials, variables, providedMaterials);
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                return new UpgradeItemResult(Result.failure(chargeResult.errorKey(), chargeResult.placeholders()), itemStack);
            }
            operationJournal.charged(operationId, chargeResult);
        } else {
            operationJournal.advance(operationId, GemOperationJournal.Phase.CHARGED);
        }
        Map<String, Object> placeholders = new LinkedHashMap<>(itemFactory.gemPlaceholders(definition, targetLevel, instance.level()));
        placeholders.put("player", player.getName());
        placeholders.put("current_level", instance.level());
        placeholders.put("target_level", targetLevel);
        placeholders.put("success_rate", successChance);
        if (ThreadLocalRandom.current().nextDouble(100D) >= successChance) {
            ItemStack penalized = applyFailurePenalty(definition, upgradeLevel, itemStack, instance);
            return new UpgradeItemResult(Result.failure("command.upgrade.failed", placeholders), penalized,
                    commitAfterActions(operationId, player, "gem_upgrade_failure",
                            upgradeLevel.failureActions(), placeholders));
        }
        ItemStack rebuilt = itemFactory.createGemItem(definition, targetLevel, Math.max(1, itemStack.getAmount()));
        if (rebuilt == null) {
            GemEconomyService.RefundResult refundResult = chargeResult == null
                    ? GemEconomyService.RefundResult.complete()
                    : economyService.refundDetailed(player, chargeResult);
            operationJournal.completeAfterRefund(operationId, "upgrade_apply_refund_failed", refundResult);
            return new UpgradeItemResult(Result.failure("command.upgrade.apply_failed", placeholders), itemStack);
        }
        return new UpgradeItemResult(Result.success("command.upgrade.success", placeholders), rebuilt,
                commitAfterActions(operationId, player, "gem_upgrade_success",
                        upgradeLevel.successActions(), placeholders));
    }

    private Runnable commitAfterActions(String operationId,
            Player player,
            String phase,
            List<String> actions,
            Map<String, Object> placeholders) {
        AtomicBoolean committed = new AtomicBoolean(false);
        List<String> safeActions = actions == null ? List.of() : List.copyOf(actions);
        Map<String, Object> safePlaceholders = placeholders == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(placeholders));
        return () -> {
            if (!committed.compareAndSet(false, true)) {
                return;
            }
            operationJournal.advance(operationId, GemOperationJournal.Phase.STATE_COMMITTED);
            operationJournal.completeAfterActions(operationId,
                    actionCoordinator.executeAsync(player, phase, safeActions, safePlaceholders));
        };
    }

    public Result upgradeEquippedGem(Player actor, Player target, int slotIndex, boolean bypassCost) {
        if (actor == null || target == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        ItemStack equipment = target.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = plugin.stateService() == null ? null : plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Result.failure("gem.error.invalid_equipment", Map.of("player", target.getName()));
        }
        GemState currentState = plugin.stateService().resolveState(equipment, itemDefinition);
        GemItemInstance instance = currentState == null ? null : currentState.assignment(slotIndex);
        if (instance == null) {
            return Result.failure("command.upgrade.slot_empty", Map.of("slot", slotIndex));
        }
        ItemStack previewItem = itemFactory.recreateGemItem(instance, 1);
        UpgradePreview preview = preview(previewItem);
        if (!preview.eligible()) {
            return Result.failure(preview.errorKey(), Map.of(
                    "slot", slotIndex,
                    "level", instance.level()
            ));
        }
        GemDefinition definition = preview.definition();
        GemDefinition.UpgradeConfig upgradeConfig = definition.upgrade();
        int targetLevel = preview.targetLevel();
        GemDefinition.GemUpgradeLevel upgradeLevel = preview.upgradeLevel();
        double successChance = effectiveSuccessChance(definition, targetLevel, upgradeLevel.successChance());
        // 宝石升级对外开放，可取消、可改成功率；在扣费前派发以保证取消即不扣费，传装备与目标槽位。
        if (threadOwnership.isEntityOwned(target)) {
            GemUpgradeEvent upgradeEvent = new GemUpgradeEvent(target, equipment, definition.id(),
                    instance.level(), targetLevel, slotIndex, successChance);
            org.bukkit.Bukkit.getPluginManager().callEvent(upgradeEvent);
            if (upgradeEvent.isCancelled()) {
                return Result.failure("gem.error.condition_not_met", Map.of("slot", slotIndex));
            }
            successChance = upgradeEvent.getSuccessChance();
        }
        String operationId = operationJournal.begin("upgrade_equipped", actor.getUniqueId());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            List<GemDefinition.CurrencyCost> currencies = new ArrayList<>(effectiveCurrencies(upgradeConfig, upgradeLevel));
            List<GemDefinition.MaterialCost> materials = new ArrayList<>(upgradeLevel.materials());
            chargeResult = economyService.charge(actor, currencies, materials, costVariables(definition, instance.level(), targetLevel));
            if (!chargeResult.success()) {
                operationJournal.failedCharge(operationId, chargeResult);
                Map<String, Object> placeholders = new LinkedHashMap<>(chargeResult.placeholders());
                placeholders.put("slot", slotIndex);
                return Result.failure(chargeResult.errorKey(), placeholders);
            }
            operationJournal.charged(operationId, chargeResult);
        } else {
            operationJournal.advance(operationId, GemOperationJournal.Phase.CHARGED);
        }
        Map<String, Object> placeholders = new LinkedHashMap<>(itemFactory.gemPlaceholders(definition, targetLevel, instance.level()));
        placeholders.put("player", target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("current_level", instance.level());
        placeholders.put("target_level", targetLevel);
        placeholders.put("success_rate", successChance);
        if (ThreadLocalRandom.current().nextDouble(100D) >= successChance) {
            GemState penalizedState = applyFailurePenalty(definition, upgradeLevel, currentState, slotIndex, instance);
            if (penalizedState != null && penalizedState != currentState) {
                ItemStack penalizedItem = plugin.stateService().applyState(equipment, itemDefinition, penalizedState);
                if (penalizedItem != null) {
                    target.getInventory().setItemInMainHand(penalizedItem);
                }
            }
            operationJournal.advance(operationId, GemOperationJournal.Phase.STATE_COMMITTED);
            operationJournal.completeAfterActions(operationId,
                    actionCoordinator.executeAsync(actor, "gem_upgrade_failure", upgradeLevel.failureActions(), placeholders));
            return Result.failure("command.upgrade.failed", placeholders);
        }
        GemItemInstance upgradedInstance = new GemItemInstance(instance.gemId(), targetLevel, System.currentTimeMillis());
        GemState nextState = currentState.withAssignment(slotIndex, upgradedInstance);
        ItemStack rebuilt = plugin.stateService().applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            GemEconomyService.RefundResult refundResult = chargeResult == null
                    ? GemEconomyService.RefundResult.complete()
                    : economyService.refundDetailed(actor, chargeResult);
            operationJournal.completeAfterRefund(operationId, "upgrade_equipped_refund_failed", refundResult);
            return Result.failure("command.upgrade.apply_failed", placeholders);
        }
        target.getInventory().setItemInMainHand(rebuilt);
        operationJournal.advance(operationId, GemOperationJournal.Phase.STATE_COMMITTED);
        operationJournal.completeAfterActions(operationId,
                actionCoordinator.executeAsync(actor, "gem_upgrade_success", upgradeLevel.successActions(), placeholders));
        return Result.success("command.upgrade.success", placeholders);
    }

    public UpgradePreview previewHeldGem(Player player) {
        return player == null ? UpgradePreview.failure("general.player_not_found") : preview(player.getInventory().getItemInMainHand());
    }

    public UpgradePreview preview(ItemStack itemStack) {
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(itemStack);
        GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (definition == null) {
            return UpgradePreview.failure("command.upgrade.hold_gem");
        }
        GemDefinition.UpgradeConfig upgradeConfig = definition.upgrade();
        int targetLevel = instance.level() + 1;
        GemDefinition.GemUpgradeLevel upgradeLevel = upgradeConfig.level(targetLevel);
        if (!upgradeConfig.enabled() || upgradeLevel == null || targetLevel > upgradeConfig.maxLevel()) {
            return UpgradePreview.failure("command.upgrade.max_level");
        }
        return UpgradePreview.success(definition, instance, targetLevel, upgradeLevel);
    }

    public double effectiveSuccessChance(GemDefinition definition, int targetLevel, double configuredChance) {
        if (configuredChance >= 0D) {
            return configuredChance;
        }
        if (definition != null) {
            Double gemRate = definition.upgrade().successRates().get(targetLevel);
            if (gemRate != null) {
                return gemRate;
            }
        }
        return plugin.appConfig().upgrade().globalSuccessRates().getOrDefault(targetLevel, 100D);
    }

    private Map<String, Object> costVariables(GemDefinition definition, int currentLevel, int targetLevel) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("level", definition == null ? 1 : definition.level());
        variables.put("current_level", Math.max(1, currentLevel));
        variables.put("target_level", Math.max(1, targetLevel));
        return Map.copyOf(variables);
    }

    private List<GemDefinition.CurrencyCost> effectiveCurrencies(GemDefinition.UpgradeConfig upgradeConfig,
            GemDefinition.GemUpgradeLevel upgradeLevel) {
        if (upgradeLevel != null && !upgradeLevel.currencies().isEmpty()) {
            return upgradeLevel.currencies();
        }
        return upgradeConfig == null ? List.of() : upgradeConfig.currencies();
    }

    private ItemStack applyFailurePenalty(GemDefinition definition,
            GemDefinition.GemUpgradeLevel upgradeLevel,
            ItemStack itemStack,
            GemItemInstance instance) {
        String penalty = effectiveFailurePenalty(definition, upgradeLevel);
        if ("destroy".equalsIgnoreCase(penalty)) {
            return new ItemStack(org.bukkit.Material.AIR);
        }
        if (!"downgrade".equalsIgnoreCase(penalty)) {
            return itemStack;
        }
        int nextLevel = Math.max(1, instance.level() - 1);
        return itemFactory.createGemItem(definition, nextLevel, Math.max(1, itemStack == null ? 1 : itemStack.getAmount()));
    }

    private GemState applyFailurePenalty(GemDefinition definition,
            GemDefinition.GemUpgradeLevel upgradeLevel,
            GemState state,
            int slotIndex,
            GemItemInstance instance) {
        String penalty = effectiveFailurePenalty(definition, upgradeLevel);
        if ("destroy".equalsIgnoreCase(penalty)) {
            return state.withAssignment(slotIndex, null);
        }
        if (!"downgrade".equalsIgnoreCase(penalty)) {
            return state;
        }
        int nextLevel = Math.max(1, instance.level() - 1);
        return state.withAssignment(slotIndex, new GemItemInstance(definition.id(), nextLevel, System.currentTimeMillis()));
    }

    private String effectiveFailurePenalty(GemDefinition definition, GemDefinition.GemUpgradeLevel upgradeLevel) {
        if (upgradeLevel != null && !upgradeLevel.failurePenalty().isBlank()) {
            return upgradeLevel.failurePenalty();
        }
        if (definition != null && !definition.upgrade().failurePenalty().isBlank()) {
            return definition.upgrade().failurePenalty();
        }
        return plugin.appConfig().upgrade().globalFailurePenalty();
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().emptyGroup()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                text -> resolvePlaceholders(player, text),
                config.invalidAsFailure(),
                ConditionContext.of(player)
        );
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            return Texts.toStringSafe(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text));
        } catch (Exception | NoClassDefFoundError _) {
            return text;
        }
    }
}

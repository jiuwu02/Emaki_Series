package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
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

    public record InlayResult(Result result, ItemStack updatedEquipment) {

        public InlayResult {
            result = result == null ? Result.failure("general.unknown_error", Map.of()) : result;
        }
    }

    private static final String OPERATION_NAMESPACE = "gem";

    private final EmakiGemPlugin plugin;
    private final GemItemMatcher itemMatcher;
    private final GemStateService stateService;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;
    private final ItemOperationLedger operationLedger;

    public GemInlayService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemStateService stateService,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator) {
        this.plugin = plugin;
        this.itemMatcher = itemMatcher;
        this.stateService = stateService;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
        this.operationLedger = new ItemOperationLedger();
    }

    /**
     * GUI 模式镶嵌：直接传入装备和宝石物品，不从主副手读取。
     * 材料费用只从玩家背包和快捷栏中扣除（不含盔甲栏和副手）。
     * 不会修改玩家手持物品，调用方负责处理装备和宝石的归还/消耗。
     */
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

        String failureAction = Texts.lower(plugin.appConfig().inlaySuccess().failureAction());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost && shouldChargeBeforeRoll(failureAction)) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, Map.of());
            if (!chargeResult.success()) {
                return new InlayResult(Result.failure(chargeResult.errorKey(), chargeResult.placeholders()), equipment);
            }
        }
        if (!rollSuccess(successChance)) {
            if (preserveInputOnFailure && chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            boolean inputConsumed = !preserveInputOnFailure && shouldConsumeGemOnFailure(failureAction);
            return new InlayResult(Result.failure("command.inlay.chance_failed", placeholders, inputConsumed), equipment);
        }
        if (!bypassCost && chargeResult == null) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, Map.of());
            if (!chargeResult.success()) {
                return new InlayResult(Result.failure(chargeResult.errorKey(), chargeResult.placeholders()), equipment);
            }
        }
        GemState nextState = currentState.withAssignment(slotIndex, instance);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return new InlayResult(Result.failure("command.inlay.apply_failed", Map.of("player", actor.getName())), equipment);
        }
        // Execute name/lore operations from gem definition and record to ledger
        applyGemOperations(rebuilt, gemDefinition, instance, slotIndex, placeholders);
        actionCoordinator.execute(actor, "gem_inlay_success", gemDefinition.inlaySuccessActions(), placeholders);
        return new InlayResult(Result.success("command.inlay.success", placeholders), rebuilt);
    }

    /**
     * GUI 模式取出：直接传入装备物品，不从主手读取。
     * 调用方负责处理装备的归还。
     */
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
        if (!evaluateConditions(actor)) {
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("gem.error.condition_not_met", Map.of()), equipment, null);
        }
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            chargeResult = economyService.charge(actor, gemDefinition.extractCost(), costVariables(gemDefinition, instance.level(), instance.level()));
            if (!chargeResult.success()) {
                return new ExtractDirectResult(
                        GemExtractService.Result.failure(chargeResult.errorKey(), chargeResult.placeholders()), equipment, null);
            }
        }
        GemState nextState = currentState.withAssignment(slotIndex, null);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return new ExtractDirectResult(
                    GemExtractService.Result.failure("command.extract.apply_failed", Map.of("player", actor.getName())), equipment, null);
        }
        // Revert name/lore operations for the extracted gem
        revertGemOperations(rebuilt, slotIndex);
        ItemStack returned = createReturnedGem(gemDefinition, instance);
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", actor.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        actionCoordinator.execute(actor, "gem_extract_success", gemDefinition.extractSuccessActions(), placeholders);
        return new ExtractDirectResult(
                GemExtractService.Result.success("command.extract.success", placeholders), rebuilt, returned);
    }

    public record ExtractDirectResult(GemExtractService.Result result, ItemStack updatedEquipment, ItemStack returnedGem) {
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
                costConfig == null ? java.util.List.of() : costConfig.currencies(),
                costConfig == null ? java.util.List.of() : costConfig.materials(),
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
        // Apply resonance name/lore operations
        applyResonanceOperations(itemStack);
    }

    private void applyResonanceOperations(ItemStack itemStack) {
        GemResonanceService resonanceService = plugin.resonanceService();
        if (resonanceService == null) {
            return;
        }
        // First revert any existing resonance operations
        operationLedger.revertAll(itemStack, OPERATION_NAMESPACE + ".resonance");
        // Re-evaluate resonances based on current state
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
        // Re-evaluate resonance after extraction
        applyResonanceOperations(itemStack);
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().emptyGroup()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                text -> resolvePlaceholders(player, text),
                config.invalidAsFailure()
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

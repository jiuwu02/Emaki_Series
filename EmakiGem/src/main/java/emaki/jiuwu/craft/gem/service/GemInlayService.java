package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

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

    private final EmakiGemPlugin plugin;
    private final GemItemMatcher itemMatcher;
    private final GemStateService stateService;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;

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
    }

    public Result inlay(Player actor, Player target, int slotIndex, boolean bypassCost) {
        return inlay(actor, target, slotIndex, bypassCost, Map.of());
    }

    public Result inlay(Player actor,
            Player target,
            int slotIndex,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials) {
        return inlay(actor, target, slotIndex, bypassCost, providedMaterials, false);
    }

    public Result inlay(Player actor,
            Player target,
            int slotIndex,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials,
            boolean preserveInputOnFailure) {
        if (actor == null || target == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        ItemStack equipment = target.getInventory().getItemInMainHand();
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Result.failure("gem.error.invalid_equipment", Map.of("player", target.getName()));
        }
        GemState currentState = stateService.resolveState(equipment, itemDefinition);
        GemItemDefinition.SocketSlot slot = itemDefinition.slot(slotIndex);
        if (slot == null) {
            return Result.failure("command.inlay.slot_not_found", Map.of("slot", slotIndex));
        }
        if (!currentState.isOpened(slotIndex)) {
            return Result.failure("command.inlay.slot_not_opened", Map.of("slot", slotIndex));
        }
        if (currentState.assignment(slotIndex) != null) {
            return Result.failure("command.inlay.slot_occupied", Map.of("slot", slotIndex));
        }
        ItemStack gemItem = actor.getInventory().getItemInOffHand();
        GemItemInstance instance = itemMatcher.readGemInstance(gemItem);
        GemDefinition gemDefinition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
        if (gemDefinition == null) {
            return Result.failure("command.inlay.hold_gem", Map.of());
        }
        if (!itemDefinition.allowsGemType(gemDefinition.gemType())) {
            return Result.failure("command.inlay.gem_type_blocked", Map.of("type", gemDefinition.gemType()));
        }
        if (!gemDefinition.supportsSocketType(slot.type())) {
            return Result.failure("command.inlay.socket_incompatible", Map.of("slot", slotIndex, "type", slot.type()));
        }
        if (itemDefinition.maxSameType() > 0
                && stateService.countAssignmentsByType(itemDefinition, currentState).getOrDefault(gemDefinition.gemType(), 0) >= itemDefinition.maxSameType()) {
            return Result.failure("command.inlay.max_same_type", Map.of("type", gemDefinition.gemType()));
        }
        if (stateService.countAssignmentsByGemId(currentState, gemDefinition.id()) >= itemDefinition.maxSameId()) {
            return Result.failure("command.inlay.max_same_id", Map.of("gem", gemDefinition.id()));
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        placeholders.put("level", instance.level());
        double successChance = resolveSuccessChance(gemDefinition);
        placeholders.put("success_rate", successChance);

        String failureAction = Texts.lower(plugin.appConfig().inlaySuccess().failureAction());
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost && shouldChargeBeforeRoll(failureAction)) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, providedMaterials);
            if (!chargeResult.success()) {
                return Result.failure(chargeResult.errorKey(), Map.of());
            }
        }
        if (!rollSuccess(successChance)) {
            if (preserveInputOnFailure && chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            boolean inputConsumed = !preserveInputOnFailure && shouldConsumeGemOnFailure(failureAction);
            if (inputConsumed) {
                consumeOne(actor.getInventory().getItemInOffHand(), actor);
            }
            return Result.failure("command.inlay.chance_failed", placeholders, inputConsumed);
        }
        if (!bypassCost && chargeResult == null) {
            chargeResult = chargeInlayCost(actor, gemDefinition, instance, providedMaterials);
            if (!chargeResult.success()) {
                return Result.failure(chargeResult.errorKey(), Map.of());
            }
        }
        GemState nextState = currentState.withAssignment(slotIndex, instance);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return Result.failure("command.inlay.apply_failed", Map.of("player", target.getName()));
        }
        target.getInventory().setItemInMainHand(rebuilt);
        consumeOne(actor.getInventory().getItemInOffHand(), actor);
        actionCoordinator.execute(target, "gem_inlay_success", gemDefinition.inlaySuccessActions(), placeholders);
        return Result.success("command.inlay.success", placeholders);
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
        double configuredChance = config.tierChances().getOrDefault(
                definition.tier(),
                config.defaultChance()
        );
        if (Texts.isBlank(config.rateFormula())) {
            return clampChance(configuredChance);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("default_chance", config.defaultChance());
        variables.put("tier_chance", configuredChance);
        variables.put("configured_chance", configuredChance);
        variables.put("tier", definition.tier());
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
        variables.put("tier", definition == null ? 1 : definition.tier());
        variables.put("current_level", Math.max(1, currentLevel));
        variables.put("target_level", Math.max(1, targetLevel));
        return Map.copyOf(variables);
    }

    private double clampChance(double chance) {
        return Math.max(0D, Math.min(100D, chance));
    }

    private void consumeOne(ItemStack itemStack, Player holder) {
        if (itemStack == null || holder == null) {
            return;
        }
        if (itemStack.getAmount() <= 1) {
            holder.getInventory().setItemInOffHand(null);
            return;
        }
        itemStack.setAmount(itemStack.getAmount() - 1);
        holder.getInventory().setItemInOffHand(itemStack);
    }
}

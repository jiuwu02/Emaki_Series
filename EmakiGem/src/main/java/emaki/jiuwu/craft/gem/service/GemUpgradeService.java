package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
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
    private final GemItemFactory itemFactory;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;

    public GemUpgradeService(EmakiGemPlugin plugin,
            GemItemFactory itemFactory,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
    }

    public Result upgradeHeldGem(Player player, boolean bypassCost) {
        return upgradeHeldGem(player, bypassCost, Map.of());
    }

    public Result upgradeHeldGem(Player player, boolean bypassCost, Map<Integer, ItemStack> providedMaterials) {
        return upgradeHeldGem(player, bypassCost, providedMaterials, true);
    }

    public Result upgradeHeldGemWithGuiMaterials(Player player,
            boolean bypassCost,
            Map<Integer, ItemStack> providedMaterials) {
        return upgradeHeldGem(player, bypassCost, providedMaterials, false);
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
        if (player == null) {
            return Result.failure("general.player_not_found", Map.of());
        }
        UpgradePreview preview = preview(itemStack);
        if (!preview.eligible()) {
            return Result.failure(preview.errorKey(), Map.of());
        }
        GemDefinition definition = preview.definition();
        GemItemInstance instance = preview.instance();
        GemDefinition.UpgradeConfig upgradeConfig = definition.upgrade();
        int targetLevel = preview.targetLevel();
        GemDefinition.GemUpgradeLevel upgradeLevel = preview.upgradeLevel();
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            List<GemDefinition.CurrencyCost> currencies = new ArrayList<>(effectiveCurrencies(upgradeConfig, upgradeLevel));
            List<GemDefinition.MaterialCost> materials = new ArrayList<>(upgradeLevel.materials());
            Map<String, Object> variables = costVariables(definition, instance.level(), targetLevel);
            chargeResult = allowInventoryFallback
                    ? economyService.charge(player, currencies, materials, variables, providedMaterials)
                    : economyService.chargeProvidedOnly(player, currencies, materials, variables, providedMaterials);
            if (!chargeResult.success()) {
                return Result.failure(chargeResult.errorKey(), Map.of());
            }
        }
        Map<String, Object> placeholders = new LinkedHashMap<>(itemFactory.gemPlaceholders(definition, targetLevel, instance.level()));
        placeholders.put("player", player.getName());
        placeholders.put("current_level", instance.level());
        placeholders.put("target_level", targetLevel);
        double successChance = effectiveSuccessChance(definition, targetLevel, upgradeLevel.successChance());
        placeholders.put("success_rate", successChance);
        if (ThreadLocalRandom.current().nextDouble(100D) >= successChance) {
            ItemStack penalized = applyFailurePenalty(definition, upgradeLevel, itemStack, instance);
            player.getInventory().setItemInMainHand(penalized == null || penalized.getType().isAir() ? null : penalized);
            actionCoordinator.execute(player, "gem_upgrade_failure", upgradeLevel.failureActions(), placeholders);
            return Result.failure("command.upgrade.failed", placeholders);
        }
        ItemStack rebuilt = itemFactory.createGemItem(definition, targetLevel, Math.max(1, itemStack.getAmount()));
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(player, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return Result.failure("command.upgrade.apply_failed", placeholders);
        }
        player.getInventory().setItemInMainHand(rebuilt);
        actionCoordinator.execute(player, "gem_upgrade_success", upgradeLevel.successActions(), placeholders);
        return Result.success("command.upgrade.success", placeholders);
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
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            List<GemDefinition.CurrencyCost> currencies = new ArrayList<>(effectiveCurrencies(upgradeConfig, upgradeLevel));
            List<GemDefinition.MaterialCost> materials = new ArrayList<>(upgradeLevel.materials());
            chargeResult = economyService.charge(actor, currencies, materials, costVariables(definition, instance.level(), targetLevel));
            if (!chargeResult.success()) {
                return Result.failure(chargeResult.errorKey(), Map.of("slot", slotIndex));
            }
        }
        Map<String, Object> placeholders = new LinkedHashMap<>(itemFactory.gemPlaceholders(definition, targetLevel, instance.level()));
        placeholders.put("player", target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("current_level", instance.level());
        placeholders.put("target_level", targetLevel);
        double successChance = effectiveSuccessChance(definition, targetLevel, upgradeLevel.successChance());
        placeholders.put("success_rate", successChance);
        if (ThreadLocalRandom.current().nextDouble(100D) >= successChance) {
            GemState penalizedState = applyFailurePenalty(definition, upgradeLevel, currentState, slotIndex, instance);
            if (penalizedState != null && penalizedState != currentState) {
                ItemStack penalizedItem = plugin.stateService().applyState(equipment, itemDefinition, penalizedState);
                if (penalizedItem != null) {
                    target.getInventory().setItemInMainHand(penalizedItem);
                }
            }
            actionCoordinator.execute(actor, "gem_upgrade_failure", upgradeLevel.failureActions(), placeholders);
            return Result.failure("command.upgrade.failed", placeholders);
        }
        GemItemInstance upgradedInstance = new GemItemInstance(instance.gemId(), targetLevel, System.currentTimeMillis());
        GemState nextState = currentState.withAssignment(slotIndex, upgradedInstance);
        ItemStack rebuilt = plugin.stateService().applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return Result.failure("command.upgrade.apply_failed", placeholders);
        }
        target.getInventory().setItemInMainHand(rebuilt);
        actionCoordinator.execute(actor, "gem_upgrade_success", upgradeLevel.successActions(), placeholders);
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
        variables.put("tier", definition == null ? 1 : definition.tier());
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
}

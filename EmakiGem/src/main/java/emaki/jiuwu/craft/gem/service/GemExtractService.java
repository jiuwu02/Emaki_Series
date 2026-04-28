package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
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

    private final EmakiGemPlugin plugin;
    private final GemItemMatcher itemMatcher;
    private final GemItemFactory itemFactory;
    private final GemStateService stateService;
    private final GemEconomyService economyService;
    private final GemActionCoordinator actionCoordinator;

    public GemExtractService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemItemFactory itemFactory,
            GemStateService stateService,
            GemEconomyService economyService,
            GemActionCoordinator actionCoordinator) {
        this.plugin = plugin;
        this.itemMatcher = itemMatcher;
        this.itemFactory = itemFactory;
        this.stateService = stateService;
        this.economyService = economyService;
        this.actionCoordinator = actionCoordinator;
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
        if (!evaluateConditions(actor)) {
            return Result.failure("gem.error.condition_not_met", Map.of());
        }
        GemEconomyService.ChargeResult chargeResult = null;
        if (!bypassCost) {
            chargeResult = economyService.charge(actor, gemDefinition.extractCost(), costVariables(gemDefinition, instance.level()));
            if (!chargeResult.success()) {
                return Result.failure(chargeResult.errorKey(), Map.of());
            }
        }
        GemState nextState = currentState.withAssignment(slotIndex, null);
        ItemStack rebuilt = stateService.applyState(equipment, itemDefinition, nextState);
        if (rebuilt == null) {
            if (chargeResult != null) {
                economyService.refund(actor, chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            }
            return Result.failure("command.extract.apply_failed", Map.of("player", target.getName()));
        }
        target.getInventory().setItemInMainHand(rebuilt);
        ItemStack returned = createReturnedGem(gemDefinition, instance);
        if (returned != null) {
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(returned);
            leftover.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("slot", slotIndex);
        placeholders.put("gem", plugin.itemFactory().resolveGemDisplayName(gemDefinition, instance.level()));
        placeholders.put("gem_id", gemDefinition.id());
        actionCoordinator.execute(target, "gem_extract_success", gemDefinition.extractSuccessActions(), placeholders);
        return Result.success("command.extract.success", placeholders);
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
        variables.put("tier", definition == null ? 1 : definition.tier());
        variables.put("current_level", Math.max(1, level));
        variables.put("target_level", Math.max(1, level));
        return Map.copyOf(variables);
    }

    private boolean evaluateConditions(Player player) {
        var config = plugin.appConfig().condition();
        if (config.conditions().isEmpty()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                config.conditions(),
                config.conditionType(),
                config.requiredCount(),
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

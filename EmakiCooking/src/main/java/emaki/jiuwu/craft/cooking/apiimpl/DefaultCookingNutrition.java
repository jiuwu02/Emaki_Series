package emaki.jiuwu.craft.cooking.apiimpl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingNutrition;
import emaki.jiuwu.craft.cooking.api.model.NutritionChange;
import emaki.jiuwu.craft.cooking.api.model.NutritionTypeView;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.service.NutritionService;
import emaki.jiuwu.craft.cooking.service.NutritionTypeRegistry;

/** Runtime-backed nutrition API. */
public final class DefaultCookingNutrition implements CookingNutrition {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingNutrition(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enabled() {
        NutritionService service = plugin == null || !plugin.publicApiReady()
                ? null
                : plugin.nutritionService();
        return service != null && service.enabled();
    }

    @Override
    public EmakiResult<Double> value(UUID playerId, String typeId) {
        if (playerId == null) {
            return EmakiResult.invalidInput("cooking.input.player_id_missing");
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("cooking.input.nutrition_type_missing");
        }
        NutritionService service = service();
        NutritionTypeRegistry registry = registry();
        if (service == null || registry == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.rejected("cooking.nutrition.disabled");
        }
        Optional<NutritionTypeConfig> type = registry.type(typeId);
        if (type.isEmpty()) {
            return EmakiResult.notFound("cooking.nutrition.type_not_found");
        }
        NutritionTypeConfig config = type.get();
        double resolved = service.value(playerId, config.id());
        return service.dataStore() != null && service.dataStore().cached(playerId) != null
                ? EmakiResult.success(resolved)
                : EmakiResult.partial(config.defaultValue(), "cooking.nutrition.data_not_loaded");
    }

    @Override
    public EmakiResult<NutritionChange> add(UUID playerId, String typeId, double amount) {
        if (!Double.isFinite(amount) || amount < 0D) {
            return EmakiResult.invalidInput("cooking.input.amount_invalid");
        }
        return applyChange(playerId, typeId, service -> service.add(playerId, typeId, amount));
    }

    @Override
    public EmakiResult<NutritionChange> remove(UUID playerId, String typeId, double amount) {
        if (!Double.isFinite(amount) || amount < 0D) {
            return EmakiResult.invalidInput("cooking.input.amount_invalid");
        }
        return applyChange(playerId, typeId, service -> service.remove(playerId, typeId, amount));
    }

    @Override
    public EmakiResult<NutritionChange> set(UUID playerId, String typeId, double amount) {
        if (!Double.isFinite(amount)) {
            return EmakiResult.invalidInput("cooking.input.amount_invalid");
        }
        return applyChange(playerId, typeId, service -> service.set(playerId, typeId, amount));
    }

    @Override
    public EmakiResult<Unit> applyFood(Player player, ItemStack itemStack) {
        EmakiResult<Unit> playerValidation = validateOwnedPlayer(player);
        if (playerValidation != null) {
            return playerValidation;
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("cooking.input.item_missing");
        }
        NutritionService service = service();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        NutritionService.FoodApplyResult result = service.applyFoodDetailed(player, itemStack);
        return switch (result.status()) {
            case APPLIED -> EmakiResult.ok();
            case DISABLED -> EmakiResult.rejected("cooking.nutrition.disabled");
            case INVALID_INPUT -> EmakiResult.invalidInput("cooking.input.item_invalid");
            case SOURCE_NOT_FOUND -> EmakiResult.notFound("cooking.nutrition.food_source_not_found");
            case CANCELLED -> EmakiResult.failure(FailureKind.CANCELLED, "cooking.nutrition.consume_cancelled");
            case NO_RULE -> EmakiResult.rejected("cooking.nutrition.food_rule_not_found");
            case DATA_UNAVAILABLE -> EmakiResult.failure(FailureKind.UNAVAILABLE,
                    "cooking.nutrition.data_not_loaded");
        };
    }

    @Override
    public EmakiResult<Unit> recheckThresholds(Player player) {
        EmakiResult<Unit> playerValidation = validateOwnedPlayer(player);
        if (playerValidation != null) {
            return playerValidation;
        }
        NutritionService service = service();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.rejected("cooking.nutrition.disabled");
        }
        return service.recheckThresholds(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.UNAVAILABLE, "cooking.nutrition.data_not_loaded");
    }

    @Override
    public List<NutritionTypeView> types() {
        NutritionTypeRegistry registry = registry();
        if (registry == null) {
            return List.of();
        }
        return registry.all().stream()
                .sorted(Comparator.comparing(NutritionTypeConfig::id))
                .map(DefaultCookingNutrition::toTypeView)
                .toList();
    }

    @Override
    public Optional<NutritionTypeView> type(String typeId) {
        NutritionTypeRegistry registry = registry();
        if (registry == null || Texts.isBlank(typeId)) {
            return Optional.empty();
        }
        return registry.type(typeId).map(DefaultCookingNutrition::toTypeView);
    }

    private EmakiResult<NutritionChange> applyChange(UUID playerId,
            String typeId,
            Function<NutritionService, NutritionOperationResult> action) {
        if (playerId == null) {
            return EmakiResult.invalidInput("cooking.input.player_id_missing");
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("cooking.input.nutrition_type_missing");
        }
        NutritionService service = service();
        NutritionTypeRegistry registry = registry();
        if (service == null || registry == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.rejected("cooking.nutrition.disabled");
        }
        if (registry.type(typeId).isEmpty()) {
            return EmakiResult.notFound("cooking.nutrition.type_not_found");
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline() && !plugin.threadOwnership().isEntityOwned(online)) {
            return EmakiResult.wrongThread();
        }
        NutritionOperationResult result = action.apply(service);
        if (result == null) {
            return EmakiResult.internalError("cooking.nutrition.write_failed");
        }
        if (!result.success()) {
            return EmakiResult.failure(toFailureKind(result.reason()), reasonKey(result.reason()));
        }
        return EmakiResult.success(new NutritionChange(result.typeId(), result.oldValue(), result.newValue()));
    }

    private EmakiResult<Unit> validateOwnedPlayer(Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("cooking.input.player_missing");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin == null || plugin.threadOwnership() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return null;
    }

    private NutritionService service() {
        return plugin == null || !plugin.isEnabled() || !plugin.publicApiReady()
                ? null
                : plugin.nutritionService();
    }

    private NutritionTypeRegistry registry() {
        return plugin == null || !plugin.isEnabled() || !plugin.publicApiReady()
                ? null
                : plugin.nutritionTypeRegistry();
    }

    private static FailureKind toFailureKind(String reason) {
        return switch (Texts.toStringSafe(reason)) {
            case "no_target" -> FailureKind.INVALID_INPUT;
            case "unknown_type" -> FailureKind.NOT_FOUND;
            case "data_unavailable" -> FailureKind.UNAVAILABLE;
            default -> FailureKind.INTERNAL_ERROR;
        };
    }

    private static String reasonKey(String reason) {
        return switch (Texts.toStringSafe(reason)) {
            case "no_target" -> "cooking.input.player_id_missing";
            case "unknown_type" -> "cooking.nutrition.type_not_found";
            case "data_unavailable" -> "cooking.nutrition.data_not_loaded";
            default -> "cooking.nutrition.write_failed";
        };
    }

    private static NutritionTypeView toTypeView(NutritionTypeConfig config) {
        return new NutritionTypeView(config.id(), config.displayName(), config.min(), config.max(), config.defaultValue());
    }
}

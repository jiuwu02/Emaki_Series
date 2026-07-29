package emaki.jiuwu.craft.cooking.apiimpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingNutrition;
import emaki.jiuwu.craft.cooking.api.model.NutritionChange;
import emaki.jiuwu.craft.cooking.api.model.NutritionTypeView;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.service.NutritionService;
import emaki.jiuwu.craft.cooking.service.NutritionTypeRegistry;

/**
 * {@link CookingNutrition} 的运行时实现。
 *
 * <p>核心职责是拆开 runtime {@code value(...)} 的三态坍缩：runtime 对「未知类型」「玩家无数据」
 * 「真实值为 0」一律返回 {@code 0}，这里分别映射为 {@code NOT_FOUND} 失败、带默认值的
 * {@code Partial}、以及普通成功，使调用方能区分拼错类型与玩家真的没有该营养。
 */
public final class DefaultCookingNutrition implements CookingNutrition {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingNutrition(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enabled() {
        NutritionService service = plugin.nutritionService();
        return service != null && service.enabled();
    }

    @Override
    public @NotNull List<NutritionTypeView> types() {
        NutritionTypeRegistry registry = plugin.nutritionTypeRegistry();
        if (registry == null) {
            return List.of();
        }
        return registry.all().stream().map(DefaultCookingNutrition::toTypeView).toList();
    }

    @Override
    public @NotNull Optional<NutritionTypeView> type(@Nullable String typeId) {
        NutritionTypeRegistry registry = plugin.nutritionTypeRegistry();
        if (registry == null || Texts.isBlank(typeId)) {
            return Optional.empty();
        }
        return registry.type(typeId).map(DefaultCookingNutrition::toTypeView);
    }

    @Override
    public @NotNull EmakiResult<Double> value(@Nullable UUID playerId, @Nullable String typeId) {
        if (playerId == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("cooking.error.no_nutrition_type");
        }
        NutritionService service = plugin.nutritionService();
        NutritionTypeRegistry registry = plugin.nutritionTypeRegistry();
        if (service == null || registry == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.disabled("cooking.error.nutrition_disabled");
        }
        Optional<NutritionTypeConfig> type = registry.type(typeId);
        if (type.isEmpty()) {
            return EmakiResult.notFound("cooking.error.unknown_nutrition_type");
        }
        NutritionTypeConfig config = type.get();
        double resolved = service.value(playerId, config.id());
        return service.dataStore() != null && service.dataStore().cached(playerId) != null
                ? EmakiResult.success(resolved)
                : EmakiResult.partial(config.defaultValue(), "cooking.nutrition.data_not_loaded");
    }

    @Override
    public @NotNull EmakiResult<NutritionChange> add(@Nullable UUID playerId,
            @Nullable String typeId,
            double amount) {
        return applyChange(playerId, typeId, service -> service.add(playerId, typeId, amount));
    }

    @Override
    public @NotNull EmakiResult<NutritionChange> set(@Nullable UUID playerId,
            @Nullable String typeId,
            double amount) {
        return applyChange(playerId, typeId, service -> service.set(playerId, typeId, amount));
    }

    @Override
    public @NotNull EmakiResult<Unit> applyFood(@Nullable Player player, @Nullable ItemStack itemStack) {
        if (player == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("cooking.error.no_item");
        }
        NutritionService service = plugin.nutritionService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.disabled("cooking.error.nutrition_disabled");
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return service.applyFood(player, itemStack)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "cooking.error.no_food_rule_matched");
    }

    @Override
    public @NotNull EmakiResult<Unit> recheckThresholds(@Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        NutritionService service = plugin.nutritionService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.disabled("cooking.error.nutrition_disabled");
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return service.recheckThresholds(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "cooking.error.nutrition_data_not_loaded");
    }

    /**
     * 执行一次营养值写入并把 runtime 结果映射为统一契约。
     *
     * @param playerId 玩家 id
     * @param typeId   营养类型 id
     * @param action   实际写入动作
     * @return 统一结果
     */
    private EmakiResult<NutritionChange> applyChange(UUID playerId,
            String typeId,
            java.util.function.Function<NutritionService, NutritionOperationResult> action) {
        if (playerId == null) {
            return EmakiResult.invalidInput("cooking.error.no_player");
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("cooking.error.no_nutrition_type");
        }
        NutritionService service = plugin.nutritionService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        if (!service.enabled()) {
            return EmakiResult.disabled("cooking.error.nutrition_disabled");
        }
        NutritionOperationResult result = action.apply(service);
        if (result == null) {
            return EmakiResult.internalError("cooking.error.nutrition_write_failed");
        }
        if (!result.success()) {
            return EmakiResult.failure(toFailureKind(result.reason()), reasonKey(result.reason()));
        }
        return EmakiResult.success(new NutritionChange(result.typeId(), result.oldValue(), result.newValue()));
    }

    /**
     * 把 runtime 的失败原因映射为失败种类。
     *
     * @param reason runtime 原因串
     * @return 失败种类
     */
    private static FailureKind toFailureKind(String reason) {
        return switch (Texts.toStringSafe(reason)) {
            case "no_target" -> FailureKind.INVALID_INPUT;
            case "unknown_type" -> FailureKind.NOT_FOUND;
            default -> FailureKind.UNAVAILABLE;
        };
    }

    /**
     * 把 runtime 的失败原因映射为稳定的 reason key。
     *
     * @param reason runtime 原因串
     * @return reason key
     */
    private static String reasonKey(String reason) {
        return switch (Texts.toStringSafe(reason)) {
            case "no_target" -> "cooking.error.no_player";
            case "unknown_type" -> "cooking.error.unknown_nutrition_type";
            case "data_unavailable" -> "cooking.error.nutrition_data_not_loaded";
            default -> "cooking.error.nutrition_write_failed";
        };
    }

    /**
     * 把 runtime 的营养类型配置映射为只读视图。
     *
     * @param config runtime 配置
     * @return 只读视图
     */
    private static NutritionTypeView toTypeView(NutritionTypeConfig config) {
        return new NutritionTypeView(config.id(),
                config.displayName(),
                config.min(),
                config.max(),
                config.defaultValue());
    }
}

package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.event.NutritionThresholdChangeEvent;
import emaki.jiuwu.craft.cooking.api.event.PlayerNutritionConsumeEvent;
import emaki.jiuwu.craft.cooking.model.NutritionComboThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionFoodSource;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionSingleThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.model.PlayerNutritionData;

/**
 * 营养系统核心服务。负责：
 * <ul>
 *   <li>增/减/设置营养值（按营养类型 min~max 截断）；</li>
 *   <li>食用物品时按物品来源匹配 {@code food_sources} 增加营养；</li>
 *   <li>单类型阈值与组合阈值（膳食均衡反胃）的边沿触发，执行 CoreLib 动作；</li>
 *   <li>定时落盘。</li>
 * </ul>
 *
 * <p>同步调用面只读取已激活缓存；需要从磁盘加载时必须使用数据存储的显式异步 API。</p>
 */
public final class NutritionService {

    private final EmakiCookingPlugin plugin;
    private final ActionExecutor actionExecutor;
    private final ItemSourceService itemSourceService;
    private final CookingSettingsService settingsService;
    private final NutritionTypeRegistry typeRegistry;
    private final PlayerNutritionDataStore dataStore;

    // 阈值满足状态（边沿触发）：单类型 key = ruleId + ":" + typeId；组合 key = ruleId
    private final Map<UUID, Set<String>> metSingleKeys = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> metComboKeys = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile List<NutritionFoodSource> foodSources = List.of();
    private volatile List<NutritionSingleThreshold> singleThresholds = List.of();
    private volatile List<NutritionComboThreshold> comboThresholds = List.of();
    private TaskHandle saveTask;

    public NutritionService(EmakiCookingPlugin plugin,
            ActionExecutor actionExecutor,
            ItemSourceService itemSourceService,
            CookingSettingsService settingsService,
            NutritionTypeRegistry typeRegistry,
            PlayerNutritionDataStore dataStore) {
        this.plugin = plugin;
        this.actionExecutor = actionExecutor;
        this.itemSourceService = itemSourceService;
        this.settingsService = settingsService;
        this.typeRegistry = typeRegistry;
        this.dataStore = dataStore;
    }

    public boolean enabled() {
        return enabled;
    }

    public NutritionTypeRegistry typeRegistry() {
        return typeRegistry;
    }

    public PlayerNutritionDataStore dataStore() {
        return dataStore;
    }

    /**
     * 重新加载营养配置并重置定时任务；同时为已激活玩家补齐新增类型默认值并重算阈值状态。
     */
    public void reload() {
        this.enabled = settingsService.nutritionEnabled();
        this.foodSources = settingsService.nutritionFoodSources();
        this.singleThresholds = settingsService.nutritionSingleThresholds();
        this.comboThresholds = settingsService.nutritionComboThresholds();
        dataStore.ensureTypesForCached(typeRegistry.asMap());
        metSingleKeys.clear();
        metComboKeys.clear();
        restartSaveTask();
        if (enabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerNutritionData data = dataStore.cached(online.getUniqueId());
                if (data != null) {
                    evaluateThresholds(online, data);
                }
            }
        }
    }

    public void shutdown() {
        cancelSaveTask();
    }

    // ===================== 数值读写 =====================

    public double value(UUID uuid, String typeId) {
        NutritionTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return 0D;
        }
        PlayerNutritionData data = dataStore.cached(uuid);
        return data == null ? type.defaultValue() : data.value(type.id(), type.defaultValue());
    }

    public NutritionOperationResult add(UUID uuid, String typeId, double amount) {
        return apply(uuid, typeId, current -> current + amount);
    }

    public NutritionOperationResult remove(UUID uuid, String typeId, double amount) {
        return apply(uuid, typeId, current -> current - amount);
    }

    public NutritionOperationResult set(UUID uuid, String typeId, double amount) {
        return apply(uuid, typeId, _ -> amount);
    }

    private NutritionOperationResult apply(UUID uuid, String typeId, java.util.function.DoubleUnaryOperator operator) {
        if (uuid == null) {
            return NutritionOperationResult.failure(typeId, "no_target");
        }
        NutritionTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return NutritionOperationResult.failure(typeId, "unknown_type");
        }
        NutritionOperationResult result = dataStore.mutateActive(uuid, typeRegistry.asMap(), data -> {
            double oldValue = data.value(type.id(), type.defaultValue());
            double newValue = type.clamp(operator.applyAsDouble(oldValue));
            if (Double.compare(newValue, oldValue) != 0) {
                data.set(type.id(), newValue);
            }
            return NutritionOperationResult.ok(type.id(), oldValue, newValue);
        });
        if (result == null) {
            return NutritionOperationResult.failure(type.id(), "data_unavailable");
        }
        Player player = Bukkit.getPlayer(uuid);
        PlayerNutritionData current = dataStore.cached(uuid);
        if (player != null && current != null) {
            evaluateThresholds(player, current);
        }
        return result;
    }

    // ===================== 食用接入 =====================

    /**
     * 玩家食用一个物品时调用：识别物品来源，匹配 {@code food_sources} 并增加对应营养、执行额外动作。
     *
     * @return 是否命中了任意食物来源规则
     */
    public boolean applyFood(Player player, ItemStack itemStack) {
        if (!enabled || player == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemSource source = itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return false;
        }
        if (Bukkit.isPrimaryThread()) {
            PlayerNutritionConsumeEvent consumeEvent =
                    new PlayerNutritionConsumeEvent(player, itemStack, ItemSourceUtil.toShorthand(source));
            Bukkit.getPluginManager().callEvent(consumeEvent);
            if (consumeEvent.isCancelled()) {
                return false;
            }
        }
        boolean matched = false;
        for (NutritionFoodSource rule : foodSources) {
            if (!matchesAny(rule.itemSources(), source)) {
                continue;
            }
            PlayerNutritionData updated = dataStore.mutateActive(player.getUniqueId(), typeRegistry.asMap(), data -> {
                for (Map.Entry<String, Double> entry : rule.nutrition().entrySet()) {
                    NutritionTypeConfig type = typeRegistry.type(entry.getKey()).orElse(null);
                    if (type == null) {
                        continue;
                    }
                    double oldValue = data.value(type.id(), type.defaultValue());
                    double newValue = type.clamp(oldValue + entry.getValue());
                    if (Double.compare(newValue, oldValue) != 0) {
                        data.set(type.id(), newValue);
                    }
                }
                return data.copy();
            });
            if (updated == null) {
                continue;
            }
            matched = true;
            if (!rule.actions().isEmpty()) {
                ActionContext context = baseContext(player, "cooking.nutrition.food")
                        .withPlaceholders(nutritionPlaceholders(updated))
                        .withPlaceholder("consumed_item", ItemSourceUtil.toShorthand(source));
                actionExecutor.executeAll(context, rule.actions(), false);
            }
            evaluateThresholds(player, updated);
        }
        return matched;
    }

    private boolean matchesAny(List<ItemSource> sources, ItemSource target) {
        for (ItemSource candidate : sources) {
            if (ItemSourceUtil.matches(candidate, target)) {
                return true;
            }
        }
        return false;
    }

    // ===================== 阈值判定 =====================

    public boolean recheckThresholds(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        PlayerNutritionData data = dataStore.cached(player.getUniqueId());
        if (data == null) {
            return false;
        }
        evaluateThresholds(player, data);
        return true;
    }

    private void evaluateThresholds(Player player, PlayerNutritionData data) {
        if (!enabled || player == null || data == null) {
            return;
        }
        evaluateSingleThresholds(player, data);
        evaluateComboThresholds(player, data);
    }

    private void evaluateSingleThresholds(Player player, PlayerNutritionData data) {
        if (singleThresholds.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> met = metSingleKeys.computeIfAbsent(uuid, _ -> ConcurrentHashMap.newKeySet());
        for (NutritionSingleThreshold rule : singleThresholds) {
            for (NutritionTypeConfig type : typeRegistry.all()) {
                if (!rule.appliesTo(type.id())) {
                    continue;
                }
                String key = rule.id() + ":" + type.id();
                double value = data.value(type.id(), type.defaultValue());
                boolean meets = rule.compare().test(value, rule.value());
                boolean wasMet = met.contains(key);
                if (meets && !wasMet) {
                    met.add(key);
                    runActions(player, data, rule.onMeetActions(), "cooking.nutrition.single." + rule.id(),
                            singlePlaceholders(type, value, rule.value()));
                    fireThresholdEvent(player, NutritionThresholdChangeEvent.Kind.SINGLE, rule.id(), type.id(), true,
                            value, rule.value(), 0, 0);
                } else if (!meets && wasMet) {
                    met.remove(key);
                    runActions(player, data, rule.onRecoverActions(),
                            "cooking.nutrition.single." + rule.id() + ".recover",
                            singlePlaceholders(type, value, rule.value()));
                    fireThresholdEvent(player, NutritionThresholdChangeEvent.Kind.SINGLE, rule.id(), type.id(), false,
                            value, rule.value(), 0, 0);
                }
            }
        }
    }

    private void evaluateComboThresholds(Player player, PlayerNutritionData data) {
        if (comboThresholds.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> met = metComboKeys.computeIfAbsent(uuid, _ -> ConcurrentHashMap.newKeySet());
        for (NutritionComboThreshold rule : comboThresholds) {
            int count = 0;
            for (NutritionTypeConfig type : typeRegistry.all()) {
                if (!rule.counts(type.id())) {
                    continue;
                }
                double value = data.value(type.id(), type.defaultValue());
                if (rule.compare().test(value, rule.value())) {
                    count++;
                }
            }
            boolean meets = count >= rule.requiredCount();
            boolean wasMet = met.contains(rule.id());
            if (meets && !wasMet) {
                met.add(rule.id());
                runActions(player, data, rule.onMeetActions(), "cooking.nutrition.combo." + rule.id(),
                        comboPlaceholders(count, rule.requiredCount(), rule.value()));
                fireThresholdEvent(player, NutritionThresholdChangeEvent.Kind.COMBO, rule.id(), null, true,
                        0D, rule.value(), count, rule.requiredCount());
            } else if (!meets && wasMet) {
                met.remove(rule.id());
                runActions(player, data, rule.onRecoverActions(),
                        "cooking.nutrition.combo." + rule.id() + ".recover",
                        comboPlaceholders(count, rule.requiredCount(), rule.value()));
                fireThresholdEvent(player, NutritionThresholdChangeEvent.Kind.COMBO, rule.id(), null, false,
                        0D, rule.value(), count, rule.requiredCount());
            }
        }
    }

    private void fireThresholdEvent(Player player,
            NutritionThresholdChangeEvent.Kind kind,
            String ruleId,
            String typeId,
            boolean met,
            double value,
            double threshold,
            int matchedCount,
            int requiredCount) {
        if (!Bukkit.isPrimaryThread()) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new NutritionThresholdChangeEvent(
                player, kind, ruleId, typeId, met, value, threshold, matchedCount, requiredCount));
    }

    // ===================== 动作与占位符 =====================

    private void runActions(Player player, PlayerNutritionData data, List<String> actions, String phase,
            Map<String, Object> extra) {
        if (actions == null || actions.isEmpty() || actionExecutor == null) {
            return;
        }
        ActionContext context = baseContext(player, phase).withPlaceholders(nutritionPlaceholders(data));
        if (extra != null && !extra.isEmpty()) {
            context = context.withPlaceholders(extra);
        }
        actionExecutor.executeAll(context, actions, false);
    }

    private ActionContext baseContext(Player player, String phase) {
        return ActionContext.create(plugin, player, phase, false);
    }

    /**
     * 构造全部营养值占位符：{@code nutrition_<type>} 与 {@code nutrition_<type>_max}。
     */
    public Map<String, Object> nutritionPlaceholders(PlayerNutritionData data) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (NutritionTypeConfig type : typeRegistry.all()) {
            double value = data == null ? type.defaultValue() : data.value(type.id(), type.defaultValue());
            placeholders.put("nutrition_" + type.id(), formatValue(value));
            placeholders.put("nutrition_" + type.id() + "_max", formatValue(type.max()));
            placeholders.put("nutrition_" + type.id() + "_min", formatValue(type.min()));
        }
        return placeholders;
    }

    private Map<String, Object> singlePlaceholders(NutritionTypeConfig type, double value, double threshold) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("nutrition_type", type.id());
        placeholders.put("nutrition_value", formatValue(value));
        placeholders.put("nutrition_threshold", formatValue(threshold));
        return placeholders;
    }

    private Map<String, Object> comboPlaceholders(int count, int requiredCount, double threshold) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("nutrition_combo_count", count);
        placeholders.put("nutrition_combo_required", requiredCount);
        placeholders.put("nutrition_threshold", formatValue(threshold));
        return placeholders;
    }

    /**
     * 统计满足组合阈值条件的营养类型数量（供占位符/命令查询使用）。
     */
    public int comboCount(UUID uuid, NutritionComboThreshold rule) {
        if (uuid == null || rule == null) {
            return 0;
        }
        PlayerNutritionData data = dataStore.cached(uuid);
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (NutritionTypeConfig type : typeRegistry.all()) {
            if (rule.counts(type.id())
                    && rule.compare().test(data.value(type.id(), type.defaultValue()), rule.value())) {
                count++;
            }
        }
        return count;
    }

    public List<NutritionComboThreshold> comboThresholds() {
        return comboThresholds;
    }

    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return Texts.toStringSafe(value);
    }

    // ===================== 定时落盘 =====================

    private void restartSaveTask() {
        cancelSaveTask();
        int seconds = settingsService.nutritionSaveIntervalSeconds();
        if (!enabled || seconds <= 0) {
            return;
        }
        long periodTicks = (long) seconds * 20L;
        saveTask = FoliaSchedulerAdapter.runTaskTimer(plugin, () -> dataStore.saveAllAsync(), periodTicks, periodTicks);
    }

    private void cancelSaveTask() {
        if (saveTask != null) {
            FoliaSchedulerAdapter.cancelTask(saveTask);
            saveTask = null;
        }
    }

    public void handleQuit(UUID uuid) {
        metSingleKeys.remove(uuid);
        metComboKeys.remove(uuid);
    }
}

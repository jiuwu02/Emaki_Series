package emaki.jiuwu.craft.cooking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.event.NutritionThresholdChangeEvent;
import emaki.jiuwu.craft.cooking.api.event.PlayerNutritionConsumeEvent;
import emaki.jiuwu.craft.cooking.model.NutritionComboThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionFoodSource;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionSingleThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.model.PlayerNutritionData;

public final class NutritionService {

    public enum FoodApplyStatus {
        APPLIED,
        DISABLED,
        INVALID_INPUT,
        SOURCE_NOT_FOUND,
        CANCELLED,
        NO_RULE,
        DATA_UNAVAILABLE
    }

    public record FoodApplyResult(FoodApplyStatus status) {

        public FoodApplyResult {
            status = status == null ? FoodApplyStatus.DATA_UNAVAILABLE : status;
        }

        public boolean applied() {
            return status == FoodApplyStatus.APPLIED;
        }
    }

    private final EmakiCookingPlugin plugin;
    private final ItemSourceService itemSourceService;
    private final CookingSettingsService settingsService;
    private final NutritionTypeRegistry typeRegistry;
    private final PlayerNutritionDataStore dataStore;
    private final EmakiScheduling taskScheduler;
    private final ThreadOwnership threadOwnership;

    private final Map<UUID, Set<String>> metSingleKeys = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> metComboKeys = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile List<NutritionFoodSource> foodSources = List.of();
    private volatile List<NutritionSingleThreshold> singleThresholds = List.of();
    private volatile List<NutritionComboThreshold> comboThresholds = List.of();
    private TaskToken saveTask;

    public NutritionService(EmakiCookingPlugin plugin,
            ItemSourceService itemSourceService,
            CookingSettingsService settingsService,
            NutritionTypeRegistry typeRegistry,
            PlayerNutritionDataStore dataStore,
            EmakiScheduling taskScheduler,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
        this.settingsService = settingsService;
        this.typeRegistry = typeRegistry;
        this.dataStore = dataStore;
        this.taskScheduler = taskScheduler;
        this.threadOwnership = threadOwnership;
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
                evaluateThresholdsOnOwnerThread(online);
            }
        }
    }

    public void shutdown() {
        cancelSaveTask();
    }

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

    private NutritionOperationResult apply(UUID uuid, String typeId, DoubleUnaryOperator operator) {
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

    public boolean applyFood(Player player, ItemStack itemStack) {
        return applyFoodDetailed(player, itemStack).applied();
    }

    public FoodApplyResult applyFoodDetailed(Player player, ItemStack itemStack) {
        if (!enabled) {
            return new FoodApplyResult(FoodApplyStatus.DISABLED);
        }
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return new FoodApplyResult(FoodApplyStatus.INVALID_INPUT);
        }
        ItemSourceRef source = itemSourceService.identifyItem(itemStack);
        if (source == null) {
            return new FoodApplyResult(FoodApplyStatus.SOURCE_NOT_FOUND);
        }
        if (threadOwnership != null && threadOwnership.isEntityOwned(player)) {
            PlayerNutritionConsumeEvent consumeEvent =
                    new PlayerNutritionConsumeEvent(player, itemStack, ItemSourceUtil.toShorthand(source));
            Bukkit.getPluginManager().callEvent(consumeEvent);
            if (consumeEvent.isCancelled()) {
                return new FoodApplyResult(FoodApplyStatus.CANCELLED);
            }
        }
        boolean ruleMatched = false;
        boolean applied = false;
        for (NutritionFoodSource rule : foodSources) {
            if (!matchesAny(rule.itemSources(), source)) {
                continue;
            }
            ruleMatched = true;
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
            applied = true;
            if (!rule.actions().isEmpty()) {
                Map<String, Object> placeholders = nutritionPlaceholders(updated);
                placeholders.put("consumed_item", ItemSourceUtil.toShorthand(source));
                plugin.actionLines().run(rule.actions(), player, "cooking.nutrition.food", false,
                        placeholders, false);
            }
            evaluateThresholds(player, updated);
        }
        if (applied) {
            return new FoodApplyResult(FoodApplyStatus.APPLIED);
        }
        return new FoodApplyResult(ruleMatched ? FoodApplyStatus.DATA_UNAVAILABLE : FoodApplyStatus.NO_RULE);
    }

    private boolean matchesAny(List<ItemSourceRef> sources, ItemSourceRef target) {
        for (ItemSourceRef candidate : sources) {
            if (ItemSourceUtil.matches(candidate, target)) {
                return true;
            }
        }
        return false;
    }

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

    private void evaluateThresholdsOnOwnerThread(Player player) {
        if (player == null) {
            return;
        }

        if (threadOwnership != null && threadOwnership.isEntityOwned(player)) {
            evaluateCachedThresholds(player);
            return;
        }
        if (taskScheduler == null) {
            plugin.getLogger().warning("EmakiCooking skipped nutrition threshold evaluation for " + player.getName()
                    + ": caller thread does not own the player and no execution dispatcher is available.");
            return;
        }
        if (taskScheduler.runForEntity(plugin, player, () -> evaluateCachedThresholds(player), null) == null) {
            plugin.getLogger().warning("EmakiCooking failed to reroute nutrition threshold evaluation for "
                    + player.getName() + ": entity task scheduling was rejected.");
        }
    }

    private void evaluateCachedThresholds(Player player) {
        PlayerNutritionData data = dataStore.cached(player.getUniqueId());
        if (data != null) {
            evaluateThresholds(player, data);
        }
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
        if (threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new NutritionThresholdChangeEvent(
                player, kind, ruleId, typeId, met, value, threshold, matchedCount, requiredCount));
    }

    private void runActions(Player player, PlayerNutritionData data, List<String> actions, String phase,
            Map<String, Object> extra) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        Map<String, Object> placeholders = nutritionPlaceholders(data);
        if (extra != null && !extra.isEmpty()) {

            placeholders.putAll(extra);
        }
        plugin.actionLines().run(actions, player, phase, false, placeholders, false);
    }

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

    private void restartSaveTask() {
        cancelSaveTask();
        int seconds = settingsService.nutritionSaveIntervalSeconds();
        if (!enabled || seconds <= 0) {
            return;
        }
        long periodTicks = (long) seconds * 20L;
        saveTask = taskScheduler.runGlobalTimer(plugin, () -> dataStore.saveAllAsync(), periodTicks, periodTicks);
    }

    private void cancelSaveTask() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    public void handleQuit(UUID uuid) {
        metSingleKeys.remove(uuid);
        metComboKeys.remove(uuid);
    }
}

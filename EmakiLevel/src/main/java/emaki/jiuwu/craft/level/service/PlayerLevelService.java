package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperationType;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.api.event.PlayerExpGainEvent;
import emaki.jiuwu.craft.level.api.event.PlayerLevelChangeEvent;
import emaki.jiuwu.craft.level.api.event.PlayerLevelUpEvent;
import emaki.jiuwu.craft.level.api.event.PlayerMaxLevelReachedEvent;
import emaki.jiuwu.craft.level.api.event.PlayerPreLevelUpEvent;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.LevelFailureReason;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.corelib.cost.CostReceipt;
import emaki.jiuwu.craft.corelib.cost.CostTransaction;

public final class PlayerLevelService {

    private final JavaPlugin plugin;
    private final LevelTypeRegistry typeRegistry;
    private final RequirementService requirementService;
    private final PlayerLevelDataStore dataStore;
    private final LevelPdcService pdcService;
    private final LevelExperienceRuleService experienceRuleService;
    private final ItemSourceService itemSourceService;
    private final EconomyManager economyManager;
    private final ActionLineRunner actionLines;
    private final EmakiScheduling scheduling;
    private final LevelOperationJournal operationJournal;
    private final Runnable attributeRefreshAll;
    private final Consumer<Player> attributeRefreshPlayer;
    private final Consumer<PlayerLevelData> dataChangeListener;
    private AppConfig config;

    public PlayerLevelService(JavaPlugin plugin,
            LevelTypeRegistry typeRegistry,
            RequirementService requirementService,
            PlayerLevelDataStore dataStore,
            LevelPdcService pdcService,
            LevelExperienceRuleService experienceRuleService,
            ItemSourceService itemSourceService,
            EconomyManager economyManager,
            ActionLineRunner actionLines,
            EmakiScheduling scheduling,
            AppConfig config,
            Runnable attributeRefreshAll,
            Consumer<Player> attributeRefreshPlayer,
            Consumer<PlayerLevelData> dataChangeListener) {
        this.plugin = plugin;
        this.typeRegistry = typeRegistry;
        this.requirementService = requirementService;
        this.dataStore = dataStore;
        this.pdcService = pdcService;
        this.experienceRuleService = experienceRuleService == null ? new LevelExperienceRuleService() : experienceRuleService;
        this.itemSourceService = itemSourceService;
        this.economyManager = economyManager;
        this.actionLines = actionLines;
        this.scheduling = scheduling;
        this.operationJournal = new LevelOperationJournal(plugin, scheduling);
        this.config = config;
        this.attributeRefreshAll = attributeRefreshAll == null ? () -> { } : attributeRefreshAll;
        this.attributeRefreshPlayer = attributeRefreshPlayer == null ? player -> { } : attributeRefreshPlayer;
        this.dataChangeListener = dataChangeListener == null ? data -> { } : dataChangeListener;
    }

    public void config(AppConfig config) {
        this.config = config;
        experienceRuleService.config(config);
        operationJournal.recover(economyManager, itemSourceService);
    }

    public LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason) {
        return addExp(uuid, typeId, amount, reason, null, false);
    }

    public LevelOperationResult addExp(UUID uuid, String typeId, double amount, String reason, Boolean autoUpgradeOverride, boolean silent) {
        if (uuid == null || amount <= 0D) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.ADD_EXP, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.ADD_EXP, typeId);
        }
        if (!type.enabled()) {
            return failure(LevelFailureReason.TYPE_DISABLED, LevelOperationType.ADD_EXP, type.id());
        }
        LevelExperienceRuleService.LevelExperienceAdjustment preview = experienceRuleService.preview(
                uuid, type.id(), amount, reason);
        if (preview.actualAmount() <= 0D) {
            String failureReason = LevelFailureReason.DAILY_CAP_REACHED.equals(preview.reason())
                    ? LevelFailureReason.DAILY_CAP_REACHED
                    : LevelFailureReason.INVALID_AMOUNT;
            return failure(failureReason, LevelOperationType.ADD_EXP, type.id()).withData(preview.data());
        }
        double previewAmount = preview.actualAmount();
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            double approvedAmount = previewAmount;
            Player player = Bukkit.getPlayer(uuid);

            if (ownsPlayer(player)) {
                PlayerExpGainEvent gainEvent = new PlayerExpGainEvent(
                        player, type.id(), oldLevel, oldExp, approvedAmount, reason);
                Bukkit.getPluginManager().callEvent(gainEvent);
                if (gainEvent.isCancelled() || gainEvent.getAmount() <= 0D) {
                    return failure(LevelFailureReason.EVENT_CANCELLED, LevelOperationType.ADD_EXP, type.id())
                            .withData(preview.data());
                }
                approvedAmount = gainEvent.getAmount();
            }
            LevelExperienceRuleService.LevelExperienceAdjustment recorded = experienceRuleService.record(
                    uuid, type.id(), approvedAmount, preview);
            if (recorded.actualAmount() <= 0D) {
                return failure(LevelFailureReason.DAILY_CAP_REACHED, LevelOperationType.ADD_EXP, type.id())
                        .withData(recorded.data());
            }
            double appliedAmount = recorded.actualAmount();
            if (entry.level() >= type.maxLevel()) {
                if (config.keepTotalExpAtMaxLevel()) {
                    entry.totalExp(entry.totalExp() + appliedAmount);
                    data.markDirty();
                }
                sync(uuid, type, entry);
                publishDataChange(data);
                return LevelOperationResult.success(
                        LevelOperationType.ADD_EXP,
                        type.id(),
                        oldLevel,
                        entry.level(),
                        oldExp,
                        entry.exp(),
                        appliedAmount).withData(recorded.data());
            }
            entry.exp(entry.exp() + appliedAmount);
            entry.totalExp(entry.totalExp() + appliedAmount);
            data.markDirty();
            executeActions(
                    player,
                    type,
                    "gain",
                    placeholders(type, entry, oldLevel, oldExp, appliedAmount, reason),
                    silent);
            boolean auto = autoUpgradeOverride == null ? type.upgrade().autoUpgrade() : autoUpgradeOverride;
            if (auto) {
                int steps = 0;
                while (steps < Math.max(1, config.maxAutoUpgradeSteps())) {
                    LevelOperationResult levelUpResult = levelUp(uuid, type.id(), LevelUpCause.AUTO, true, silent);
                    if (!levelUpResult.success()) {
                        break;
                    }
                    steps++;
                    entry = data.entry(type.id());
                    if (entry.level() >= type.maxLevel()) {
                        break;
                    }
                }
            }
            sync(uuid, type, entry);
            publishDataChange(data);
            return LevelOperationResult.success(
                    LevelOperationType.ADD_EXP,
                    type.id(),
                    oldLevel,
                    entry.level(),
                    oldExp,
                    entry.exp(),
                    appliedAmount).withData(recorded.data());
        });
        return dataResult(result, LevelOperationType.ADD_EXP, type.id());
    }

    public LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason) {
        if (uuid == null || amount < 0D) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.SET_EXP, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.SET_EXP, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.exp(amount);
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            return LevelOperationResult.success(LevelOperationType.SET_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
        });
        return dataResult(result, LevelOperationType.SET_EXP, type.id());
    }

    public LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason) {
        if (uuid == null || amount <= 0D) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.REMOVE_EXP, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.REMOVE_EXP, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.exp(Math.max(0D, entry.exp() - amount));
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            return LevelOperationResult.success(LevelOperationType.REMOVE_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
        });
        return dataResult(result, LevelOperationType.REMOVE_EXP, type.id());
    }

    public LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason) {
        if (uuid == null || amount <= 0) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.ADD_LEVEL, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.ADD_LEVEL, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.level(clamp(oldLevel + amount, type.startLevel(), type.maxLevel()));
            entry.exp(0D);
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            refreshAttribute(uuid);
            fireLevelChange(uuid, type, oldLevel, entry.level(), LevelOperationType.ADD_LEVEL);
            return LevelOperationResult.success(LevelOperationType.ADD_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
        });
        return dataResult(result, LevelOperationType.ADD_LEVEL, type.id());
    }

    public LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason) {
        if (uuid == null || amount <= 0) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.REMOVE_LEVEL, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.REMOVE_LEVEL, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.level(clamp(oldLevel - amount, type.startLevel(), type.maxLevel()));
            entry.exp(0D);
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            refreshAttribute(uuid);
            fireLevelChange(uuid, type, oldLevel, entry.level(), LevelOperationType.REMOVE_LEVEL);
            return LevelOperationResult.success(LevelOperationType.REMOVE_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
        });
        return dataResult(result, LevelOperationType.REMOVE_LEVEL, type.id());
    }

    public LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason) {
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return failure(type == null ? LevelFailureReason.TYPE_NOT_FOUND : LevelFailureReason.PLAYER_NOT_FOUND, LevelOperationType.SET_LEVEL, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.level(clamp(level, type.startLevel(), type.maxLevel()));
            entry.exp(0D);
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            refreshAttribute(uuid);
            fireLevelChange(uuid, type, oldLevel, entry.level(), LevelOperationType.SET_LEVEL);
            return LevelOperationResult.success(LevelOperationType.SET_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), level);
        });
        return dataResult(result, LevelOperationType.SET_LEVEL, type.id());
    }

    public LevelOperationResult reset(UUID uuid, String typeId) {
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return failure(type == null ? LevelFailureReason.TYPE_NOT_FOUND : LevelFailureReason.PLAYER_NOT_FOUND, LevelOperationType.RESET, typeId);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            entry.level(type.startLevel());
            entry.exp(0D);
            entry.totalExp(0D);
            data.markDirty();
            sync(uuid, type, entry);
            publishDataChange(data);
            refreshAttribute(uuid);
            fireLevelChange(uuid, type, oldLevel, entry.level(), LevelOperationType.RESET);
            return LevelOperationResult.success(LevelOperationType.RESET, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), 0D);
        });
        return dataResult(result, LevelOperationType.RESET, type.id());
    }

    public LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause) {
        return levelUp(uuid, typeId, cause, false, false);
    }

    private LevelOperationResult levelUp(UUID uuid, String typeId, LevelUpCause cause, boolean fromAuto, boolean silent) {
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return failure(type == null ? LevelFailureReason.TYPE_NOT_FOUND : LevelFailureReason.PLAYER_NOT_FOUND, LevelOperationType.LEVEL_UP, typeId);
        }
        if (!type.upgrade().enabled()) {
            return levelUpFailure(uuid, type, LevelFailureReason.UPGRADE_DISABLED, silent);
        }
        if (!fromAuto && !type.upgrade().manualUpgrade()) {
            return levelUpFailure(uuid, type, LevelFailureReason.MANUAL_UPGRADE_DISABLED, silent);
        }
        LevelOperationResult result = dataStore.mutate(uuid, typeRegistry.asMap(), data -> {
            PlayerLevelEntry entry = data.entry(type.id());
            if (entry.level() >= type.maxLevel()) {
                return levelUpFailure(uuid, type, LevelFailureReason.MAX_LEVEL, silent);
            }
            int targetLevel = entry.level() + 1;
            double requiredExp = requirementService.requiredExp(type, entry, targetLevel);
            if (requiredExp <= 0D) {
                return levelUpFailure(uuid, type, LevelFailureReason.INVALID_REQUIREMENT, silent);
            }
            if (entry.exp() + 1.0E-9D < requiredExp) {
                return levelUpFailure(uuid, type, LevelFailureReason.NOT_ENOUGH_EXP, silent);
            }
            int oldLevel = entry.level();
            double oldExp = entry.exp();
            Player player = Bukkit.getPlayer(uuid);
            if (ownsPlayer(player)) {
                PlayerPreLevelUpEvent preEvent = new PlayerPreLevelUpEvent(
                        player, type.id(), oldLevel, targetLevel, requiredExp, cause);
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled()) {
                    return failure(LevelFailureReason.EVENT_CANCELLED, LevelOperationType.LEVEL_UP, type.id());
                }
            }
            String operationId = operationJournal.begin("level_up:" + type.id(), uuid);
            CostReceipt costResult = chargeCost(player, type, targetLevel, operationId);
            if (!costResult.success()) {
                operationJournal.failedCharge(operationId, costResult);
                return levelUpFailure(uuid, type, mapCostFailure(costResult.failureReason()), silent);
            }
            entry.exp(Math.max(0D, entry.exp() - requiredExp));
            entry.level(targetLevel);
            data.markDirty();
            operationJournal.advance(operationId, LevelOperationJournal.Phase.STATE_COMMITTED);
            giveRewards(player, type, targetLevel);
            Map<String, Object> placeholders = placeholders(type, entry, oldLevel, oldExp, 1D, cause == null ? "levelup" : cause.name().toLowerCase(Locale.ROOT));
            operationJournal.completeAfterActions(operationId,
                    executeActions(player, type, "success", placeholders, silent));
            sync(uuid, type, entry);
            publishDataChange(data);
            refreshAttribute(uuid);
            if (ownsPlayer(player)) {
                Bukkit.getPluginManager().callEvent(new PlayerLevelUpEvent(player, type.id(), oldLevel, entry.level(), cause));
                if (oldLevel < type.maxLevel() && entry.level() >= type.maxLevel()) {
                    Bukkit.getPluginManager().callEvent(new PlayerMaxLevelReachedEvent(player, type.id(), type.maxLevel(), cause));
                }
            }
            return LevelOperationResult.success(LevelOperationType.LEVEL_UP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), 1D);
        });
        return dataResult(result, LevelOperationType.LEVEL_UP, type.id());
    }

    private LevelOperationResult levelUpFailure(UUID uuid,
            LevelTypeConfig type,
            String reason,
            boolean silent) {
        if (type != null && uuid != null) {
            PlayerLevelData data = dataStore.cached(uuid);
            PlayerLevelEntry entry = data == null ? null : data.entry(type.id());
            if (entry != null) {
                Player player = Bukkit.getPlayer(uuid);
                executeActions(
                        player,
                        type,
                        "failure",
                        placeholders(type, entry, entry.level(), entry.exp(), 0D, reason),
                        silent);
            }
        }
        return failure(reason, LevelOperationType.LEVEL_UP, type == null ? "" : type.id());
    }

    private boolean ownsPlayer(Player player) {
        return player != null && scheduling.ownsEntity(player);
    }

    public void syncAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncOwnedPlayer(player);
        }
        attributeRefreshAll.run();
    }

    private void syncOwnedPlayer(Player player) {
        if (player == null) {
            return;
        }
        if (ownsPlayer(player)) {
            syncPlayer(player);
            return;
        }
        scheduling.runForEntity(plugin, player, () -> syncPlayer(player), null);
    }

    public void syncPlayer(Player player) {
        if (player == null) {
            return;
        }
        PlayerLevelData data = dataStore.cached(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (LevelTypeConfig type : typeRegistry.all()) {
            sync(player.getUniqueId(), type, data.entry(type.id()));
        }
        attributeRefreshPlayer.accept(player);
    }

    private CostReceipt chargeCost(Player player, LevelTypeConfig type, int targetLevel, String operationId) {
        if (!type.upgrade().cost().enabled()) {
            operationJournal.advance(operationId, LevelOperationJournal.Phase.CHARGED);
            return CostReceipt.noop();
        }
        if (player == null) {
            return CostReceipt.failure(CostReceipt.FailureReason.PLAYER_UNAVAILABLE);
        }
        Map<String, Object> vars = costVariables(type, targetLevel);
        List<CostTransaction.CurrencyCharge> currencies = new ArrayList<>();
        for (LevelTypeConfig.CurrencyCost currency : type.upgrade().cost().currencies()) {
            vars.put("base_cost", currency.baseCost());
            double amount = Math.max(0D, ExpressionEngine.evaluate(currency.costFormula(), vars));
            if (amount > 0D) {
                currencies.add(new CostTransaction.CurrencyCharge(currency.provider(), currency.currencyId(), amount));
            }
        }
        List<CostTransaction.MaterialSource> materials = new ArrayList<>();
        for (LevelTypeConfig.MaterialCost material : type.upgrade().cost().materials()) {
            vars.put("base_amount", material.baseAmount());
            long amount = Math.max(0L, Math.round(ExpressionEngine.evaluate(material.amountFormula(), vars)));
            if (amount > 0L) {
                List<ItemSourceRef> refs = new ArrayList<>();
                List<String> tokens = new ArrayList<>();
                for (String token : material.itemSources()) {
                    ItemSourceRef ref = ItemSourceUtil.parse(token);
                    if (ref != null) {
                        refs.add(ref);
                        tokens.add(token);
                    }
                }
                if (!refs.isEmpty()) {
                    materials.add(CostTransaction.ofParsed(refs, tokens, amount));
                }
            }
        }
        operationJournal.preparedCosts(operationId, currencies, materials);
        CostReceipt receipt = CostTransaction.execute(player, economyManager, itemSourceService, currencies, materials);
        if (receipt.success()) {
            operationJournal.charged(operationId, receipt);
        }
        return receipt;
    }

    private static String mapCostFailure(CostReceipt.FailureReason reason) {
        return switch (reason) {
            case INSUFFICIENT_FUNDS -> LevelFailureReason.NOT_ENOUGH_MONEY;
            case INSUFFICIENT_MATERIALS -> LevelFailureReason.NOT_ENOUGH_MATERIAL;
            case PLAYER_UNAVAILABLE -> LevelFailureReason.PLAYER_NOT_FOUND;
            default -> LevelFailureReason.COST_COMPENSATION_FAILED;
        };
    }

    private void giveRewards(Player player, LevelTypeConfig type, int level) {
        if (player == null) {
            return;
        }
        for (LevelTypeConfig.ItemReward reward : type.upgrade().rewards().items()) {
            if (!matchesLevel(reward.levels(), level)) {
                continue;
            }
            for (String sourceText : reward.itemSources()) {
                ItemSourceRef source = ItemSourceUtil.parse(sourceText);
                ItemStack item = itemSourceService.createItem(source, reward.amount());
                if (item != null) {
                    InventoryItemUtil.giveOrDrop(player, item);
                    break;
                }
            }
        }
    }

    private boolean matchesLevel(String pattern, int level) {
        if (Texts.isBlank(pattern) || "*".equals(pattern.trim())) {
            return true;
        }
        for (String part : pattern.split(",")) {
            String text = part.trim();
            if (text.contains("-")) {
                String[] range = text.split("-", 2);
                try {
                    int min = Integer.parseInt(range[0].trim());
                    int max = Integer.parseInt(range[1].trim());
                    if (level >= min && level <= max) {
                        return true;
                    }
                } catch (Exception _) {
                }
            } else {
                try {
                    if (Integer.parseInt(text) == level) {
                        return true;
                    }
                } catch (Exception _) {
                }
            }
        }
        return false;
    }

    private CompletionStage<Boolean> executeActions(Player player,
            LevelTypeConfig type,
            String phase,
            Map<String, Object> placeholders,
            boolean silent) {
        if (type == null) {
            return CompletableFuture.completedFuture(false);
        }
        List<String> lines = type.upgrade().actions().getOrDefault(phase, List.of());
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        if (actionLines == null) {
            plugin.getLogger().warning("EmakiLevel action phase '" + phase + "' failed: action runner unavailable");
            return CompletableFuture.completedFuture(false);
        }
        return actionLines.run(lines, player, phase, silent, placeholders, true).handle((success, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("EmakiLevel action phase '" + phase + "' failed: " + throwable.getMessage());
                return false;
            }
            if (success == null || !success) {
                plugin.getLogger().warning("EmakiLevel action phase '" + phase + "' failed: action batch unsuccessful");
                return false;
            }
            return true;
        });
    }

    private void sync(UUID uuid, LevelTypeConfig type, PlayerLevelEntry entry) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || type == null || entry == null) {
            return;
        }
        double required = requirementService.requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
        pdcService.sync(player, type, entry, required);
    }

    private void refreshAttribute(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            attributeRefreshPlayer.accept(player);
        }
    }

    private void fireLevelChange(UUID uuid, LevelTypeConfig type, int oldLevel, int newLevel, LevelOperationType operationType) {
        if (oldLevel == newLevel) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (!ownsPlayer(player)) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new PlayerLevelChangeEvent(
                player, type.id(), oldLevel, newLevel, operationType));
    }

    private void publishDataChange(PlayerLevelData data) {
        if (data != null) {
            dataChangeListener.accept(data.copy());
        }
    }

    private Map<String, Object> placeholders(LevelTypeConfig type, PlayerLevelEntry entry, int oldLevel, double oldExp, double amount, String reason) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        double required = requirementService.requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
        double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
        placeholders.put("type", type.id());
        placeholders.put("type_display_name", type.displayName());
        placeholders.put("level", entry.level());
        placeholders.put("old_level", oldLevel);
        placeholders.put("new_level", entry.level());
        placeholders.put("exp", format(entry.exp()));
        placeholders.put("old_exp", format(oldExp));
        placeholders.put("new_exp", format(entry.exp()));
        placeholders.put("total_exp", format(entry.totalExp()));
        placeholders.put("required_exp", format(required));
        placeholders.put("progress", progress);
        placeholders.put("progress_percent", format(progress * 100D));
        placeholders.put("amount", format(amount));
        placeholders.put("reason", Texts.toStringSafe(reason));
        placeholders.put("failure_reason", Texts.toStringSafe(reason));
        return placeholders;
    }

    public Map<String, Object> displayPlaceholders(LevelTypeConfig type, PlayerLevelEntry entry) {
        return placeholders(type, entry, entry.level(), entry.exp(), 0D, "display");
    }

    private Map<String, Object> costVariables(LevelTypeConfig type, int targetLevel) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("target_level", targetLevel);
        vars.put("type", type.id());
        vars.put("base_cost", 0D);
        vars.put("base_amount", 1D);
        return vars;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static LevelOperationResult dataResult(LevelOperationResult result,
            LevelOperationType operationType,
            String typeId) {
        return result == null
                ? failure(LevelFailureReason.PLAYER_DATA_UNAVAILABLE, operationType, typeId)
                : result;
    }

    private static LevelOperationResult failure(String reason, LevelOperationType operationType, String typeId) {
        return LevelOperationResult.failure(reason, operationType, typeId);
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }
}

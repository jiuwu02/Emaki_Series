package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperationType;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.config.AppConfig;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.LevelFailureReason;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.script.js.JavaScriptLevelUpHookRegistry;

public final class PlayerLevelService {

    private final Plugin plugin;
    private final LevelTypeRegistry typeRegistry;
    private final RequirementService requirementService;
    private final PlayerLevelDataStore dataStore;
    private final LevelPdcService pdcService;
    private final LevelExperienceRuleService experienceRuleService;
    private final JavaScriptLevelUpHookRegistry javaScriptLevelUpHooks;
    private final ItemSourceService itemSourceService;
    private final EconomyManager economyManager;
    private final ActionExecutor actionExecutor;
    private final Runnable attributeRefreshAll;
    private final java.util.function.Consumer<Player> attributeRefreshPlayer;
    private final java.util.function.Consumer<PlayerLevelData> dataChangeListener;
    private AppConfig config;

    public PlayerLevelService(Plugin plugin,
            LevelTypeRegistry typeRegistry,
            RequirementService requirementService,
            PlayerLevelDataStore dataStore,
            LevelPdcService pdcService,
            LevelExperienceRuleService experienceRuleService,
            JavaScriptLevelUpHookRegistry javaScriptLevelUpHooks,
            ItemSourceService itemSourceService,
            EconomyManager economyManager,
            ActionExecutor actionExecutor,
            AppConfig config,
            Runnable attributeRefreshAll,
            java.util.function.Consumer<Player> attributeRefreshPlayer,
            java.util.function.Consumer<PlayerLevelData> dataChangeListener) {
        this.plugin = plugin;
        this.typeRegistry = typeRegistry;
        this.requirementService = requirementService;
        this.dataStore = dataStore;
        this.pdcService = pdcService;
        this.experienceRuleService = experienceRuleService == null ? new LevelExperienceRuleService() : experienceRuleService;
        this.javaScriptLevelUpHooks = javaScriptLevelUpHooks;
        this.itemSourceService = itemSourceService;
        this.economyManager = economyManager;
        this.actionExecutor = actionExecutor;
        this.config = config;
        this.attributeRefreshAll = attributeRefreshAll == null ? () -> { } : attributeRefreshAll;
        this.attributeRefreshPlayer = attributeRefreshPlayer == null ? player -> { } : attributeRefreshPlayer;
        this.dataChangeListener = dataChangeListener == null ? data -> { } : dataChangeListener;
    }

    public void config(AppConfig config) {
        this.config = config;
        experienceRuleService.config(config);
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
        LevelExperienceRuleService.LevelExperienceAdjustment adjustment = experienceRuleService.adjust(uuid, type.id(), amount, reason);
        if (adjustment.actualAmount() <= 0D) {
            String failureReason = LevelFailureReason.DAILY_CAP_REACHED.equals(adjustment.reason())
                    ? LevelFailureReason.DAILY_CAP_REACHED
                    : LevelFailureReason.INVALID_AMOUNT;
            return failure(failureReason, LevelOperationType.ADD_EXP, type.id()).withData(adjustment.data());
        }
        amount = adjustment.actualAmount();
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        if (entry.level() >= type.maxLevel()) {
            if (config.keepTotalExpAtMaxLevel()) {
                entry.totalExp(entry.totalExp() + amount);
                data.markDirty();
            }
            sync(uuid, type, entry);
            publishDataChange(data);
            return LevelOperationResult.success(LevelOperationType.ADD_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount).withData(adjustment.data());
        }
        entry.exp(entry.exp() + amount);
        entry.totalExp(entry.totalExp() + amount);
        data.markDirty();
        Player player = Bukkit.getPlayer(uuid);
        executeActions(player, type, "gain", placeholders(type, entry, oldLevel, oldExp, amount, reason));
        boolean auto = autoUpgradeOverride == null ? type.upgrade().autoUpgrade() : autoUpgradeOverride;
        if (auto) {
            int steps = 0;
            while (steps < Math.max(1, config.maxAutoUpgradeSteps())) {
                LevelOperationResult result = levelUp(uuid, type.id(), LevelUpCause.AUTO, true, silent);
                if (!result.success()) {
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
        return LevelOperationResult.success(LevelOperationType.ADD_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount).withData(adjustment.data());
    }

    public LevelOperationResult setExp(UUID uuid, String typeId, double amount, String reason) {
        if (uuid == null || amount < 0D) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.SET_EXP, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.SET_EXP, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        entry.exp(amount);
        data.markDirty();
        sync(uuid, type, entry);
        publishDataChange(data);
        return LevelOperationResult.success(LevelOperationType.SET_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
    }

    public LevelOperationResult removeExp(UUID uuid, String typeId, double amount, String reason) {
        if (uuid == null || amount <= 0D) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.REMOVE_EXP, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.REMOVE_EXP, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        entry.exp(Math.max(0D, entry.exp() - amount));
        data.markDirty();
        sync(uuid, type, entry);
        publishDataChange(data);
        return LevelOperationResult.success(LevelOperationType.REMOVE_EXP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
    }

    public LevelOperationResult addLevel(UUID uuid, String typeId, int amount, String reason) {
        if (uuid == null || amount <= 0) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.ADD_LEVEL, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.ADD_LEVEL, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        entry.level(clamp(oldLevel + amount, type.startLevel(), type.maxLevel()));
        entry.exp(0D);
        data.markDirty();
        sync(uuid, type, entry);
        publishDataChange(data);
        refreshAttribute(uuid);
        return LevelOperationResult.success(LevelOperationType.ADD_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
    }

    public LevelOperationResult removeLevel(UUID uuid, String typeId, int amount, String reason) {
        if (uuid == null || amount <= 0) {
            return failure(LevelFailureReason.INVALID_AMOUNT, LevelOperationType.REMOVE_LEVEL, typeId);
        }
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (type == null) {
            return failure(LevelFailureReason.TYPE_NOT_FOUND, LevelOperationType.REMOVE_LEVEL, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        entry.level(clamp(oldLevel - amount, type.startLevel(), type.maxLevel()));
        entry.exp(0D);
        data.markDirty();
        sync(uuid, type, entry);
        publishDataChange(data);
        refreshAttribute(uuid);
        return LevelOperationResult.success(LevelOperationType.REMOVE_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), amount);
    }

    public LevelOperationResult setLevel(UUID uuid, String typeId, int level, String reason) {
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return failure(type == null ? LevelFailureReason.TYPE_NOT_FOUND : LevelFailureReason.PLAYER_NOT_FOUND, LevelOperationType.SET_LEVEL, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        entry.level(clamp(level, type.startLevel(), type.maxLevel()));
        entry.exp(0D);
        data.markDirty();
        sync(uuid, type, entry);
        publishDataChange(data);
        refreshAttribute(uuid);
        return LevelOperationResult.success(LevelOperationType.SET_LEVEL, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), level);
    }

    public LevelOperationResult reset(UUID uuid, String typeId) {
        LevelTypeConfig type = typeRegistry.type(typeId).orElse(null);
        if (uuid == null || type == null) {
            return failure(type == null ? LevelFailureReason.TYPE_NOT_FOUND : LevelFailureReason.PLAYER_NOT_FOUND, LevelOperationType.RESET, typeId);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
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
        return LevelOperationResult.success(LevelOperationType.RESET, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), 0D);
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
            return levelUpFailure(uuid, type, LevelFailureReason.UPGRADE_DISABLED);
        }
        if (!fromAuto && !type.upgrade().manualUpgrade()) {
            return levelUpFailure(uuid, type, LevelFailureReason.MANUAL_UPGRADE_DISABLED);
        }
        PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        if (entry.level() >= type.maxLevel()) {
            return levelUpFailure(uuid, type, LevelFailureReason.MAX_LEVEL);
        }
        int targetLevel = entry.level() + 1;
        double requiredExp = requirementService.requiredExp(type, entry, targetLevel);
        if (requiredExp <= 0D) {
            return levelUpFailure(uuid, type, LevelFailureReason.INVALID_REQUIREMENT);
        }
        if (entry.exp() + 1.0E-9D < requiredExp) {
            return levelUpFailure(uuid, type, LevelFailureReason.NOT_ENOUGH_EXP);
        }
        Player player = Bukkit.getPlayer(uuid);
        String costFailure = checkCost(player, type, targetLevel);
        if (costFailure != null) {
            return levelUpFailure(uuid, type, costFailure);
        }
        int oldLevel = entry.level();
        double oldExp = entry.exp();
        chargeCost(player, type, targetLevel);
        entry.exp(Math.max(0D, entry.exp() - requiredExp));
        entry.level(targetLevel);
        data.markDirty();
        giveRewards(player, type, targetLevel);
        Map<String, Object> placeholders = placeholders(type, entry, oldLevel, oldExp, 1D, cause == null ? "levelup" : cause.name().toLowerCase(java.util.Locale.ROOT));
        executeActions(player, type, "success", placeholders);
        sync(uuid, type, entry);
        publishDataChange(data);
        refreshAttribute(uuid);
        if (javaScriptLevelUpHooks != null) {
            javaScriptLevelUpHooks.fire(new JavaScriptLevelUpHookRegistry.LevelUpEvent(
                    uuid.toString(),
                    player == null ? "" : player.getName(),
                    type.id(),
                    oldLevel,
                    entry.level(),
                    oldExp,
                    entry.exp(),
                    cause == null ? "levelup" : cause.name().toLowerCase(java.util.Locale.ROOT),
                    requiredExp
            ));
        }
        return LevelOperationResult.success(LevelOperationType.LEVEL_UP, type.id(), oldLevel, entry.level(), oldExp, entry.exp(), 1D);
    }

    private LevelOperationResult levelUpFailure(UUID uuid, LevelTypeConfig type, String reason) {
        if (type != null && uuid != null) {
            PlayerLevelData data = dataStore.getOrLoad(uuid, typeRegistry.asMap());
            PlayerLevelEntry entry = data.entry(type.id());
            Player player = Bukkit.getPlayer(uuid);
            executeActions(player, type, "failure", placeholders(type, entry, entry.level(), entry.exp(), 0D, reason));
        }
        return failure(reason, LevelOperationType.LEVEL_UP, type == null ? "" : type.id());
    }

    public void syncAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerLevelData data = dataStore.getOrLoad(player.getUniqueId(), typeRegistry.asMap());
            for (LevelTypeConfig type : typeRegistry.all()) {
                sync(player.getUniqueId(), type, data.entry(type.id()));
            }
        }
        attributeRefreshAll.run();
    }

    private String checkCost(Player player, LevelTypeConfig type, int targetLevel) {
        if (!type.upgrade().cost().enabled()) {
            return null;
        }
        if (player == null) {
            return LevelFailureReason.PLAYER_NOT_FOUND;
        }
        Map<String, Object> vars = costVariables(type, targetLevel);
        for (LevelTypeConfig.CurrencyCost currency : type.upgrade().cost().currencies()) {
            vars.put("base_cost", currency.baseCost());
            double amount = Math.max(0D, ExpressionEngine.evaluate(currency.costFormula(), vars));
            if (amount > 0D && economyManager.getBalance(player, currency.provider(), currency.currencyId()) + 1.0E-9D < amount) {
                return LevelFailureReason.NOT_ENOUGH_MONEY;
            }
        }
        for (LevelTypeConfig.MaterialCost material : type.upgrade().cost().materials()) {
            vars.put("base_amount", material.baseAmount());
            long amount = Math.max(0L, Math.round(ExpressionEngine.evaluate(material.amountFormula(), vars)));
            if (amount <= 0L) {
                continue;
            }
            long count = 0L;
            for (String source : material.itemSources()) {
                count += InventoryItemUtil.countItems(player, itemSourceService, source);
            }
            if (count < amount) {
                return LevelFailureReason.NOT_ENOUGH_MATERIAL;
            }
        }
        return null;
    }

    private void chargeCost(Player player, LevelTypeConfig type, int targetLevel) {
        if (!type.upgrade().cost().enabled() || player == null) {
            return;
        }
        Map<String, Object> vars = costVariables(type, targetLevel);
        for (LevelTypeConfig.CurrencyCost currency : type.upgrade().cost().currencies()) {
            vars.put("base_cost", currency.baseCost());
            double amount = Math.max(0D, ExpressionEngine.evaluate(currency.costFormula(), vars));
            if (amount > 0D) {
                economyManager.remove(player, currency.provider(), currency.currencyId(), amount);
            }
        }
        for (LevelTypeConfig.MaterialCost material : type.upgrade().cost().materials()) {
            vars.put("base_amount", material.baseAmount());
            long remaining = Math.max(0L, Math.round(ExpressionEngine.evaluate(material.amountFormula(), vars)));
            for (String source : material.itemSources()) {
                if (remaining <= 0L) {
                    break;
                }
                long before = InventoryItemUtil.countItems(player, itemSourceService, source);
                long take = Math.min(before, remaining);
                if (take > 0L && InventoryItemUtil.removeItems(player.getInventory(), itemSourceService, source, take)) {
                    remaining -= take;
                }
            }
        }
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
                ItemSource source = ItemSourceUtil.parse(sourceText);
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

    private void executeActions(Player player, LevelTypeConfig type, String phase, Map<String, Object> placeholders) {
        if (actionExecutor == null || type == null) {
            return;
        }
        List<String> lines = type.upgrade().actions().getOrDefault(phase, List.of());
        if (lines.isEmpty()) {
            return;
        }
        ActionContext context = ActionContext.create(plugin, player, phase, false).withPlaceholders(placeholders);
        actionExecutor.executeAll(context, lines, false).whenComplete((_, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("EmakiLevel action phase '" + phase + "' failed: " + throwable.getMessage());
            }
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

    private void publishDataChange(PlayerLevelData data) {
        if (data != null) {
            dataChangeListener.accept(data);
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

    private static LevelOperationResult failure(String reason, LevelOperationType operationType, String typeId) {
        return LevelOperationResult.failure(reason, operationType, typeId);
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}

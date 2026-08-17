package emaki.jiuwu.craft.skills.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.cost.CostReceipt;
import emaki.jiuwu.craft.corelib.cost.CostTransaction;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.event.SkillPreUpgradeEvent;
import emaki.jiuwu.craft.skills.api.event.SkillUpgradeEvent;

import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

public final class SkillUpgradeService {

    public record CurrencyCost(String provider,
            String currencyId,
            double amount,
            String displayName) {

        public CurrencyCost {
            provider = Texts.isBlank(provider) ? "auto" : Texts.lower(provider);
            currencyId = Texts.toStringSafe(currencyId);
            amount = Math.max(0D, amount);
            displayName = Texts.toStringSafe(displayName);
        }
    }

    public record MaterialCost(String item, int amount, String displayName) {

        public MaterialCost {
            item = Texts.toStringSafe(item);
            amount = Math.max(1, amount);
            displayName = Texts.toStringSafe(displayName);
        }
    }

    public record UpgradePreview(
            SkillDefinition definition,
            int currentLevel,
            int targetLevel,
            int maxLevel,
            double successRate,
            List<CurrencyCost> currencies,
            List<MaterialCost> materials) {

        public UpgradePreview {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }

    public record UpgradeResult(
            boolean success,
            boolean levelChanged,
            boolean successfulRoll,
            boolean downgraded,
            String messageKey,
            Map<String, Object> placeholders,
            UpgradePreview preview) {

        public UpgradeResult {
            messageKey = Texts.toStringSafe(messageKey);
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static UpgradeResult fail(String messageKey, Map<String, Object> placeholders, UpgradePreview preview) {
            return new UpgradeResult(false, false, false, false, messageKey, placeholders, preview);
        }

        public static UpgradeResult ok(boolean successfulRoll,
                boolean levelChanged,
                boolean downgraded,
                String messageKey,
                Map<String, Object> placeholders,
                UpgradePreview preview) {
            return new UpgradeResult(true, levelChanged, successfulRoll, downgraded,
                    messageKey, placeholders, preview);
        }
    }

    private final JavaPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;
    private final SkillLevelService levelService;
    private final SkillParameterResolver parameterResolver;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;
    private final ActionLineRunner actionLines;

    public SkillUpgradeService(JavaPlugin plugin,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillLevelService levelService,
            SkillParameterResolver parameterResolver,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService,
            ActionLineRunner actionLines) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.dataStore = dataStore;
        this.levelService = levelService;
        this.parameterResolver = parameterResolver;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
        this.actionLines = actionLines;
    }

    public UpgradePreview preview(Player player, String skillId) {
        SkillDefinition definition = stateService == null ? null : stateService.getDefinition(Texts.normalizeId(skillId));
        return preview(player, definition);
    }

    public UpgradePreview preview(Player player, SkillDefinition definition) {
        if (player == null || definition == null) {
            return null;
        }
        int currentLevel = levelService.currentLevel(player, definition);
        int maxLevel = levelService.maxLevel(definition);
        int targetLevel = Math.min(maxLevel, currentLevel + 1);
        double successRate = definition.upgrade().successRateFor(targetLevel);
        return new UpgradePreview(
                definition,
                currentLevel,
                targetLevel,
                maxLevel,
                successRate,
                quoteCurrencies(player, definition, currentLevel, targetLevel),
                quoteMaterials(definition, targetLevel)
        );
    }

    public UpgradeResult upgrade(Player player, String skillId) {
        if (player == null) {
            return UpgradeResult.fail("upgrade.player_required", Map.of(), null);
        }
        String normalizedSkillId = Texts.normalizeId(skillId);
        SkillDefinition definition = stateService.getDefinition(normalizedSkillId);
        if (definition == null) {
            return UpgradeResult.fail("skill.not_found", Map.of("skill_id", normalizedSkillId), null);
        }
        if (!isUnlocked(player, normalizedSkillId)) {
            return UpgradeResult.fail("upgrade.not_unlocked", basePlaceholders(player, definition, 1, 1, 1, 100D), null);
        }
        SkillUpgradeConfig upgrade = definition.upgrade();
        if (upgrade == null || !upgrade.enabled()) {
            int current = levelService.currentLevel(player, definition);
            return UpgradeResult.fail("upgrade.disabled",
                    basePlaceholders(player, definition, current, current, levelService.maxLevel(definition), 100D), null);
        }

        int currentLevel = levelService.currentLevel(player, definition);
        int maxLevel = levelService.maxLevel(definition);
        if (currentLevel >= maxLevel) {
            return UpgradeResult.fail("upgrade.max_level",
                    basePlaceholders(player, definition, currentLevel, currentLevel, maxLevel, 100D), preview(player, definition));
        }

        int targetLevel = currentLevel + 1;
        UpgradePreview preview = preview(player, definition);

        if (!definition.conditions().emptyGroup()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                    definition.conditions(),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "skill_upgrade"),
                    true,
                    ConditionContext.of(player, null, Map.of(
                            "skillId", definition.id(),
                            "currentLevel", currentLevel,
                            "targetLevel", targetLevel,
                            "maxLevel", maxLevel))
            );
            if (!conditionsPassed) {
                return UpgradeResult.fail("upgrade.condition_not_met",
                        basePlaceholders(player, definition, currentLevel, targetLevel, maxLevel, preview == null ? 100D : preview.successRate()), preview);
            }
        }

        Map<String, Object> placeholders = basePlaceholders(
                player,
                definition,
                currentLevel,
                targetLevel,
                maxLevel,
                preview == null ? 100D : preview.successRate()
        );

        double successRate = preview == null ? 100D : preview.successRate();

        SkillPreUpgradeEvent preUpgradeEvent = new SkillPreUpgradeEvent(
                player, definition.id(), currentLevel, targetLevel, maxLevel, successRate);
        Bukkit.getPluginManager().callEvent(preUpgradeEvent);
        if (preUpgradeEvent.isCancelled()) {
            return UpgradeResult.fail("upgrade.cancelled", placeholders, preview);
        }
        successRate = preUpgradeEvent.getSuccessRate();
        placeholders.put("success_rate", successRate);

        CostCheckResult costCheck = checkCosts(player, preview);
        if (!costCheck.success()) {
            placeholders.putAll(costCheck.placeholders());
            return UpgradeResult.fail(costCheck.messageKey(), placeholders, preview);
        }

        ChargeResult chargeResult = charge(player, preview);
        if (!chargeResult.success()) {
            placeholders.putAll(chargeResult.placeholders());
            return UpgradeResult.fail(chargeResult.messageKey(), placeholders, preview);
        }

        boolean success = roll(successRate);
        boolean downgraded = !success && "downgrade".equals(Texts.lower(upgrade.failurePenalty()));
        try {
            if (success) {
                levelService.setLevel(player, definition, targetLevel);
            } else {
                applyFailurePenalty(player, definition, currentLevel, upgrade.failurePenalty());
            }
            dataStore.save(player);
        } catch (RuntimeException | LinkageError exception) {
            boolean stateRestored = restoreLevelAfterCommitFailure(player, definition, currentLevel);
            boolean costsRestored = rollbackCharge(player, chargeResult);
            boolean compensated = stateRestored & costsRestored;
            if (!compensated && plugin != null) {
                plugin.getLogger().severe("Failed to fully compensate skill upgrade state commit for "
                        + (player == null ? "unknown player" : player.getUniqueId()));
            }
            placeholders.put("reason", Texts.isBlank(exception.getMessage())
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
            return UpgradeResult.fail("upgrade.commit_failed", placeholders, preview);
        }

        if (success) {
            triggerActions(player, definition, "skill_upgrade_success",
                    upgrade.levels().get(targetLevel) == null
                            ? List.of()
                            : upgrade.levels().get(targetLevel).successActions(),
                    placeholders);
            fireUpgradeEvent(player, definition, currentLevel, targetLevel, successRate, true, false);
            return UpgradeResult.ok(true, true, false, "upgrade.success", placeholders, preview);
        }

        triggerActions(player, definition, "skill_upgrade_failure",
                upgrade.levels().get(targetLevel) == null
                        ? List.of()
                        : upgrade.levels().get(targetLevel).failureActions(),
                placeholders);
        fireUpgradeEvent(player, definition, currentLevel, downgraded ? Math.max(1, currentLevel - 1) : currentLevel,
                successRate, false, downgraded);
        return UpgradeResult.ok(false, downgraded, downgraded,
                "upgrade.failed", placeholders, preview);
    }

    private void fireUpgradeEvent(Player player,
            SkillDefinition definition,
            int fromLevel,
            int toLevel,
            double successRate,
            boolean success,
            boolean downgraded) {

        Bukkit.getPluginManager().callEvent(new SkillUpgradeEvent(
                player, definition.id(), fromLevel, toLevel, successRate, success, downgraded));
    }

    private List<CurrencyCost> quoteCurrencies(Player player,
            SkillDefinition definition,
            int currentLevel,
            int targetLevel) {
        SkillUpgradeConfig upgrade = definition.upgrade();
        if (upgrade == null) {
            return List.of();
        }
        List<CurrencyCost> result = new ArrayList<>();
        Map<String, Object> variables = parameterResolver.variables(
                player,
                definition,
                "upgrade",
                null,
                currentLevel,
                targetLevel
        );
        for (SkillUpgradeConfig.CurrencyEntry currency : upgrade.effectiveCurrencies(targetLevel)) {
            if (currency == null) {
                continue;
            }
            Map<String, Object> context = new LinkedHashMap<>(variables);
            context.put("base_cost", currency.baseCost());
            double amount = ExpressionEngine.evaluate(currency.costFormula(), context);
            if (amount <= 0D) {
                continue;
            }
            result.add(new CurrencyCost(
                    currency.provider(),
                    currency.currencyId(),
                    amount,
                    resolveCurrencyDisplayName(currency)
            ));
        }
        return List.copyOf(result);
    }

    private List<MaterialCost> quoteMaterials(SkillDefinition definition, int targetLevel) {
        SkillUpgradeConfig upgrade = definition.upgrade();
        SkillUpgradeConfig.SkillUpgradeLevel level = upgrade == null ? null : upgrade.levels().get(targetLevel);
        if (level == null || level.materials().isEmpty()) {
            return List.of();
        }
        List<MaterialCost> result = new ArrayList<>();
        for (SkillUpgradeConfig.MaterialCost material : level.materials()) {
            if (material == null || Texts.isBlank(material.item())) {
                continue;
            }
            result.add(new MaterialCost(
                    material.item(),
                    material.amount(),
                    resolveMaterialDisplayName(material.item())
            ));
        }
        return List.copyOf(result);
    }

    private CostCheckResult checkCosts(Player player, UpgradePreview preview) {
        if (preview == null) {
            return CostCheckResult.fail("upgrade.invalid", Map.of());
        }
        EconomyManager economyManager = economyManager();
        for (CurrencyCost currency : aggregateCurrencies(preview.currencies())) {
            if (economyManager == null || economyManager.select(currency.provider(), currency.currencyId()) == null) {
                return CostCheckResult.fail("upgrade.economy_unavailable", Map.of(
                        "cost", formatCost(currency)
                ));
            }
            double balance = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            if (balance + 1.0E-9D < currency.amount()) {
                return CostCheckResult.fail("upgrade.insufficient_funds", Map.of(
                        "cost", formatCost(currency),
                        "required", formatAmount(currency.amount()),
                        "balance", formatAmount(balance)
                ));
            }
        }
        for (ResolvedMaterialCost material : aggregateMaterials(preview.materials())) {
            long available = InventoryItemUtil.countItems(player, itemSourceService, material.source());
            if (available < material.amount()) {
                return CostCheckResult.fail("upgrade.insufficient_materials", Map.of(
                        "material", material.displayName(),
                        "required", material.amount(),
                        "available", available
                ));
            }
        }
        return CostCheckResult.ok();
    }

    ChargeResult charge(Player player, UpgradePreview preview) {
        if (player == null || player.getInventory() == null || preview == null) {
            return ChargeResult.fail("upgrade.invalid", Map.of(), true);
        }
        List<CostTransaction.CurrencyCharge> currencies = new ArrayList<>();
        for (CurrencyCost c : aggregateCurrencies(preview.currencies())) {
            currencies.add(new CostTransaction.CurrencyCharge(c.provider(), c.currencyId(), c.amount()));
        }
        List<CostTransaction.MaterialSource> materials = new ArrayList<>();
        for (ResolvedMaterialCost m : aggregateMaterials(preview.materials())) {
            List<ItemSourceRef> srcs = m.source() != null ? List.of(m.source()) : List.of();
            materials.add(CostTransaction.ofParsed(srcs, List.of(), m.amount()));
        }
        CostReceipt receipt = CostTransaction.execute(player, economyManager(), itemSourceService, currencies, materials);
        if (receipt.success()) {
            return ChargeResult.committed(receipt);
        }
        boolean compensated = receipt.compensationComplete();
        return switch (receipt.failureReason()) {
            case ECONOMY_UNAVAILABLE -> ChargeResult.fail("upgrade.economy_unavailable", Map.of(), compensated);
            case INSUFFICIENT_FUNDS -> ChargeResult.fail("upgrade.insufficient_funds", Map.of(), compensated);
            case INSUFFICIENT_MATERIALS -> ChargeResult.fail("upgrade.insufficient_materials", Map.of(), compensated);
            default -> ChargeResult.fail("upgrade.invalid", Map.of(), compensated);
        };
    }

    private List<CurrencyCost> aggregateCurrencies(List<CurrencyCost> currencies) {
        if (currencies == null || currencies.isEmpty()) {
            return List.of();
        }
        Map<CurrencyKey, CurrencyCost> aggregated = new LinkedHashMap<>();
        for (CurrencyCost currency : currencies) {
            if (currency == null || currency.amount() <= 0D) {
                continue;
            }
            CurrencyKey key = new CurrencyKey(currency.provider(), currency.currencyId());
            CurrencyCost existing = aggregated.get(key);
            double amount = existing == null ? currency.amount() : existing.amount() + currency.amount();
            String displayName = existing == null ? currency.displayName() : existing.displayName();
            aggregated.put(key, new CurrencyCost(currency.provider(), currency.currencyId(), amount, displayName));
        }
        return List.copyOf(aggregated.values());
    }

    private List<ResolvedMaterialCost> aggregateMaterials(List<MaterialCost> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        Map<MaterialKey, ResolvedMaterialCost> aggregated = new LinkedHashMap<>();
        for (MaterialCost material : materials) {
            if (material == null || material.amount() <= 0) {
                continue;
            }
            ItemSourceRef source = ItemSourceUtil.parse(material.item());
            MaterialKey key = new MaterialKey(source, source == null ? Texts.lower(material.item()) : "");
            ResolvedMaterialCost existing = aggregated.get(key);
            long amount = existing == null
                    ? material.amount()
                    : Math.min(Integer.MAX_VALUE, (long) existing.amount() + material.amount());
            String displayName = existing == null ? material.displayName() : existing.displayName();
            aggregated.put(key, new ResolvedMaterialCost(source, (int) amount, displayName));
        }
        return List.copyOf(aggregated.values());
    }

    private boolean restoreLevelAfterCommitFailure(Player player,
            SkillDefinition definition,
            int previousLevel) {
        try {
            levelService.setLevel(player, definition, previousLevel);
            dataStore.save(player);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to restore skill level after upgrade commit failure for "
                                + (player == null ? "unknown player" : player.getUniqueId()),
                        exception);
            }
            return false;
        }
    }

    private boolean rollbackCharge(Player player, ChargeResult chargeResult) {
        CostReceipt receipt = chargeResult == null ? null : chargeResult.receipt();
        if (receipt == null) {
            return true;
        }
        return receipt.rollback().complete();
    }

    private void applyFailurePenalty(Player player, SkillDefinition definition, int currentLevel, String failurePenalty) {
        if (!"downgrade".equals(Texts.lower(failurePenalty))) {
            return;
        }
        levelService.setLevel(player, definition, Math.max(1, currentLevel - 1));
    }

    private void triggerActions(Player player,
            SkillDefinition definition,
            String phase,
            List<String> actions,
            Map<String, Object> placeholders) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        if (actionLines == null) {
            return;
        }
        Map<String, String> stringPlaceholders = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            stringPlaceholders.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        stringPlaceholders.putIfAbsent("skills_skill_id", definition.id());
        stringPlaceholders.putIfAbsent("skills_phase", phase);
        actionLines.run(actions, player, phase, false, stringPlaceholders, true)
                .whenComplete((success, throwable) -> logActionResult(definition, phase, success, throwable));
    }

    private void logActionResult(SkillDefinition definition,
            String phase,
            Boolean success,
            Throwable throwable) {
        if (throwable != null) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillUpgrade] Action phase '" + phase + "' failed for "
                            + (definition == null ? "-" : definition.id()),
                    throwable);
            return;
        }
        if (success == null || !success) {
            plugin.getLogger().warning("[SkillUpgrade] Action phase '" + phase + "' failed for "
                    + (definition == null ? "-" : definition.id()));
        }
    }

    private boolean isUnlocked(Player player, String skillId) {
        if (stateService == null || player == null || Texts.isBlank(skillId)) {
            return false;
        }
        for (UnlockedSkillEntry entry : stateService.getUnlockedSkills(player)) {
            if (skillId.equals(Texts.normalizeId(entry.skillId()))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> basePlaceholders(Player player,
            SkillDefinition definition,
            int currentLevel,
            int targetLevel,
            int maxLevel,
            double successRate) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player == null ? "" : player.getName());
        placeholders.put("skill_id", definition == null ? "" : definition.id());
        placeholders.put("skill", definition == null ? "" : definition.displayName());
        placeholders.put("level", currentLevel);
        placeholders.put("current_level", currentLevel);
        placeholders.put("target_level", targetLevel);
        placeholders.put("max_level", maxLevel);
        placeholders.put("success_rate", formatAmount(successRate));
        placeholders.put("success_rate_raw", successRate);
        placeholders.put("roman_level", roman(targetLevel));
        return placeholders;
    }

    private String resolveCurrencyDisplayName(SkillUpgradeConfig.CurrencyEntry currency) {
        if (currency == null) {
            return "";
        }
        if (Texts.isNotBlank(currency.displayName())) {
            return currency.displayName();
        }
        if (Texts.isNotBlank(currency.currencyId())) {
            return currency.currencyId();
        }
        return currency.provider();
    }

    private String resolveMaterialDisplayName(String itemToken) {
        String displayName = EmakiCoreLibApi.itemDisplayName(itemToken).orElse("");
        return Texts.isBlank(displayName) ? itemToken : displayName;
    }

    private String formatCost(CurrencyCost currency) {
        return formatAmount(currency.amount()) + " " + currency.displayName();
    }

    private String formatAmount(double amount) {
        return Numbers.formatNumber(amount, "0.##");
    }

    private boolean roll(double successRate) {
        if (successRate >= 100D) {
            return true;
        }
        if (successRate <= 0D) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100D) < successRate;
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    private record CostCheckResult(boolean success, String messageKey, Map<String, Object> placeholders) {

        private static CostCheckResult ok() {
            return new CostCheckResult(true, "", Map.of());
        }

        private static CostCheckResult fail(String messageKey, Map<String, Object> placeholders) {
            return new CostCheckResult(false, messageKey, placeholders == null ? Map.of() : Map.copyOf(placeholders));
        }
    }

    record ChargeResult(boolean success,
            String messageKey,
            Map<String, Object> placeholders,
            boolean compensationComplete,
            CostReceipt receipt) {

        private static ChargeResult committed(CostReceipt receipt) {
            return new ChargeResult(true, "", Map.of(), true, receipt);
        }

        private static ChargeResult fail(String messageKey,
                Map<String, Object> placeholders,
                boolean compensationComplete) {
            return new ChargeResult(false,
                    messageKey,
                    placeholders == null ? Map.of() : Map.copyOf(placeholders),
                    compensationComplete,
                    null);
        }
    }

    private record CurrencyKey(String provider, String currencyId) {

    }

    private record MaterialKey(ItemSourceRef source, String unresolvedToken) {

    }

    private record ResolvedMaterialCost(ItemSourceRef source, int amount, String displayName) {

    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

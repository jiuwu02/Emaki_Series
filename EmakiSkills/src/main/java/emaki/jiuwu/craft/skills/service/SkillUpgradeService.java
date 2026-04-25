package emaki.jiuwu.craft.skills.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
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
            List<MaterialCost> materials,
            ResolvedSkillParameters parameters) {

        public UpgradePreview {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
            parameters = parameters == null ? ResolvedSkillParameters.empty() : parameters;
        }
    }

    public record UpgradeResult(
            boolean success,
            boolean levelChanged,
            String messageKey,
            Map<String, Object> placeholders,
            UpgradePreview preview) {

        public UpgradeResult {
            messageKey = Texts.toStringSafe(messageKey);
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static UpgradeResult fail(String messageKey, Map<String, Object> placeholders, UpgradePreview preview) {
            return new UpgradeResult(false, false, messageKey, placeholders, preview);
        }

        public static UpgradeResult ok(boolean levelChanged,
                String messageKey,
                Map<String, Object> placeholders,
                UpgradePreview preview) {
            return new UpgradeResult(true, levelChanged, messageKey, placeholders, preview);
        }
    }

    private final JavaPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final PlayerSkillDataStore dataStore;
    private final SkillLevelService levelService;
    private final SkillParameterResolver parameterResolver;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;
    private final Supplier<ActionExecutor> actionExecutorSupplier;

    public SkillUpgradeService(JavaPlugin plugin,
            PlayerSkillStateService stateService,
            PlayerSkillDataStore dataStore,
            SkillLevelService levelService,
            SkillParameterResolver parameterResolver,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService,
            Supplier<ActionExecutor> actionExecutorSupplier) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.dataStore = dataStore;
        this.levelService = levelService;
        this.parameterResolver = parameterResolver;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
        this.actionExecutorSupplier = actionExecutorSupplier;
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
                quoteMaterials(definition, targetLevel),
                resolveTargetParameters(player, definition, targetLevel)
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
        Map<String, Object> placeholders = basePlaceholders(
                player,
                definition,
                currentLevel,
                targetLevel,
                maxLevel,
                preview == null ? 100D : preview.successRate()
        );

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

        boolean success = roll(preview.successRate());
        if (success) {
            levelService.setLevel(player, definition, targetLevel);
            dataStore.save(player);
            triggerActions(player, definition, "skill_upgrade_success",
                    upgrade.levels().get(targetLevel) == null
                            ? List.of()
                            : upgrade.levels().get(targetLevel).successActions(),
                    placeholders);
            return UpgradeResult.ok(true, "upgrade.success", placeholders, preview);
        }

        applyFailurePenalty(player, definition, currentLevel, upgrade.failurePenalty());
        dataStore.save(player);
        triggerActions(player, definition, "skill_upgrade_failure",
                upgrade.levels().get(targetLevel) == null
                        ? List.of()
                        : upgrade.levels().get(targetLevel).failureActions(),
                placeholders);
        return UpgradeResult.ok(false, "upgrade.failed", placeholders, preview);
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

    private ResolvedSkillParameters resolveTargetParameters(Player player, SkillDefinition definition, int targetLevel) {
        if (definition == null || parameterResolver == null) {
            return ResolvedSkillParameters.empty();
        }
        int currentLevel = levelService.currentLevel(player, definition);
        return parameterResolver.resolveAtLevel(player, definition, "upgrade", null,
                Math.max(1, targetLevel), Math.max(1, targetLevel), currentLevel);
    }

    private CostCheckResult checkCosts(Player player, UpgradePreview preview) {
        if (preview == null) {
            return CostCheckResult.fail("upgrade.invalid", Map.of());
        }
        EconomyManager economyManager = economyManager();
        for (CurrencyCost currency : preview.currencies()) {
            if (currency.amount() <= 0D) {
                continue;
            }
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
        for (MaterialCost material : preview.materials()) {
            long available = InventoryItemUtil.countItems(player, itemSourceService, material.item());
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

    private ChargeResult charge(Player player, UpgradePreview preview) {
        List<CurrencyCost> chargedCurrencies = new ArrayList<>();
        List<MaterialCost> chargedMaterials = new ArrayList<>();
        EconomyManager economyManager = economyManager();

        for (CurrencyCost currency : preview.currencies()) {
            if (currency.amount() <= 0D) {
                continue;
            }
            if (economyManager == null) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.fail("upgrade.economy_unavailable", Map.of("cost", formatCost(currency)));
            }
            ActionResult result = economyManager.remove(player, currency.provider(), currency.currencyId(), currency.amount());
            if (!result.success()) {
                refund(player, chargedCurrencies, chargedMaterials);
                String messageKey = result.errorType() == ActionErrorType.INSUFFICIENT_BALANCE
                        ? "upgrade.insufficient_funds"
                        : "upgrade.economy_unavailable";
                return ChargeResult.fail(messageKey, Map.of("cost", formatCost(currency)));
            }
            chargedCurrencies.add(currency);
        }

        for (MaterialCost material : preview.materials()) {
            boolean removed = InventoryItemUtil.removeItems(
                    player.getInventory(),
                    itemSourceService,
                    material.item(),
                    material.amount()
            );
            if (!removed) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.fail("upgrade.insufficient_materials", Map.of(
                        "material", material.displayName(),
                        "required", material.amount(),
                        "available", InventoryItemUtil.countItems(player, itemSourceService, material.item())
                ));
            }
            chargedMaterials.add(material);
        }

        return ChargeResult.ok(chargedCurrencies, chargedMaterials);
    }

    private void refund(Player player, List<CurrencyCost> currencies, List<MaterialCost> materials) {
        EconomyManager economyManager = economyManager();
        if (economyManager != null) {
            for (CurrencyCost currency : currencies) {
                if (currency != null && currency.amount() > 0D) {
                    economyManager.add(player, currency.provider(), currency.currencyId(), currency.amount());
                }
            }
        }
        for (MaterialCost material : materials) {
            if (material == null || material.amount() <= 0) {
                continue;
            }
            ItemSource source = ItemSourceUtil.parse(material.item());
            ItemStack itemStack = source == null ? null : itemSourceService.createItem(source, material.amount());
            if (itemStack == null) {
                continue;
            }
            player.getInventory().addItem(itemStack)
                    .values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
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
        ActionExecutor executor = actionExecutorSupplier == null ? null : actionExecutorSupplier.get();
        if (executor == null) {
            return;
        }
        Map<String, String> stringPlaceholders = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            stringPlaceholders.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        ActionContext context = ActionContext.create(plugin, player, phase, false)
                .withPlaceholders(stringPlaceholders)
                .withAttribute("skill_id", definition.id());
        executor.executeAll(context, actions, true)
                .whenComplete((result, throwable) -> logActionResult(definition, phase, result, throwable));
    }

    private void logActionResult(SkillDefinition definition,
            String phase,
            ActionBatchResult result,
            Throwable throwable) {
        if (throwable != null) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillUpgrade] Action phase '" + phase + "' failed for "
                            + (definition == null ? "-" : definition.id()),
                    throwable);
            return;
        }
        if (result == null || result.success()) {
            return;
        }
        var firstFailure = result.firstFailure();
        String error = firstFailure == null || firstFailure.result() == null
                ? "unknown"
                : Texts.toStringSafe(firstFailure.result().errorMessage());
        plugin.getLogger().warning("[SkillUpgrade] Action phase '" + phase + "' failed for "
                + (definition == null ? "-" : definition.id()) + ": " + error);
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
        ItemSource source = ItemSourceUtil.parse(itemToken);
        String displayName = source == null || itemSourceService == null ? "" : itemSourceService.displayName(source);
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

    private record ChargeResult(boolean success,
            String messageKey,
            Map<String, Object> placeholders,
            List<CurrencyCost> currencies,
            List<MaterialCost> materials) {

        private static ChargeResult ok(List<CurrencyCost> currencies, List<MaterialCost> materials) {
            return new ChargeResult(true, "", Map.of(),
                    currencies == null ? List.of() : List.copyOf(currencies),
                    materials == null ? List.of() : List.copyOf(materials));
        }

        private static ChargeResult fail(String messageKey, Map<String, Object> placeholders) {
            return new ChargeResult(false, messageKey, placeholders == null ? Map.of() : Map.copyOf(placeholders),
                    List.of(), List.of());
        }
    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairCurrencyCost;
import emaki.jiuwu.craft.item.model.RepairEconomyConfig;
import emaki.jiuwu.craft.item.model.RepairMaterial;

public final class ItemRepairService {

    private static final NamespacedKey DISABLED_KEY = new NamespacedKey("emakiitem", "disabled");

    public record CurrencyQuote(RepairCurrencyCost cost, double amount, double balance, boolean supported) {

        public boolean affordable() {
            return supported && balance >= amount;
        }
    }

    public record EconomyQuote(boolean success,
            String errorKey,
            Map<String, Object> replacements,
            int currentDamage,
            int maxDamage,
            int restoreAmount,
            List<CurrencyQuote> currencies) {

        public EconomyQuote {
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }

        public boolean affordable() {
            return success && currencies.stream().allMatch(CurrencyQuote::affordable);
        }
    }

    public record RepairResult(boolean success,
            String errorKey,
            Map<String, Object> replacements,
            int restoreAmount) {

        public RepairResult {
            replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        }

        public static RepairResult success(int restoreAmount) {
            return new RepairResult(true, "", Map.of("restore", restoreAmount), restoreAmount);
        }

        public static RepairResult failure(String errorKey, Map<String, Object> replacements) {
            return new RepairResult(false, errorKey, replacements, 0);
        }
    }

    private final EmakiItemPlugin plugin;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;
    private final ThreadOwnership threadOwnership;

    public ItemRepairService(EmakiItemPlugin plugin) {
        this(plugin, null);
    }

    public ItemRepairService(EmakiItemPlugin plugin, Supplier<EconomyManager> economyManagerSupplier) {
        this(plugin, economyManagerSupplier, plugin == null ? null : plugin.itemSourceService(), null);
    }

    ItemRepairService(EmakiItemPlugin plugin,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService) {
        this(plugin, economyManagerSupplier, itemSourceService, null);
    }

    public ItemRepairService(EmakiItemPlugin plugin,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
        this.threadOwnership = threadOwnership;
    }

    public boolean isDisabled(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte value = pdc.get(DISABLED_KEY, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void markDisabled(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(DISABLED_KEY, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(meta);
    }

    public void clearDisabled(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(DISABLED_KEY);
        itemStack.setItemMeta(meta);
    }

    @Nullable
    public RepairMaterial findMatchingMaterial(EmakiItemDefinition definition, ItemStack repairItem) {
        if (definition == null || repairItem == null || repairItem.getType().isAir()) {
            return null;
        }
        if (!definition.repair().enabled() || !definition.repair().hasRepairMaterials()) {
            return null;
        }
        ItemSourceService sourceService = itemSourceService;
        ItemSource repairItemSource = sourceService == null ? null : sourceService.identifyItem(repairItem);
        if (repairItemSource == null) {
            return null;
        }
        for (RepairMaterial material : definition.repair().materials()) {
            if (material.matches(repairItemSource)) {
                return material;
            }
        }
        return null;
    }

    @Nullable
    public RepairMaterial findAffordableMaterial(EmakiItemDefinition definition, Map<Integer, ItemStack> providedMaterials) {
        if (definition == null || providedMaterials == null || providedMaterials.isEmpty()) {
            return null;
        }
        if (!definition.repair().enabled() || !definition.repair().hasRepairMaterials()) {
            return null;
        }
        for (RepairMaterial material : definition.repair().materials()) {
            if (countProvidedMaterial(providedMaterials, material) >= material.amount()) {
                return material;
            }
        }
        return null;
    }

    public long countProvidedMaterial(Map<Integer, ItemStack> providedMaterials, RepairMaterial material) {
        if (providedMaterials == null || providedMaterials.isEmpty() || material == null) {
            return 0L;
        }
        ItemSourceService sourceService = itemSourceService;
        if (sourceService == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : providedMaterials.values()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = sourceService.identifyItem(itemStack);
            if (material.matches(source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public boolean removeProvidedMaterial(Map<Integer, ItemStack> providedMaterials, RepairMaterial material) {
        return debitProvidedMaterial(providedMaterials, material).success();
    }

    private MaterialDebit debitProvidedMaterial(Map<Integer, ItemStack> providedMaterials, RepairMaterial material) {
        if (providedMaterials == null || material == null || material.amount() <= 0) {
            return MaterialDebit.committed(List.of());
        }
        if (itemSourceService == null) {
            return MaterialDebit.failure();
        }
        long remaining = material.amount();
        List<InventoryItemUtil.RemovalPlan> plans = new ArrayList<>();
        for (ItemSource source : material.itemSources()) {
            if (remaining <= 0L) {
                break;
            }
            InventoryItemUtil.RemovalPlan plan = InventoryItemUtil.planRemoval(
                    providedMaterials,
                    itemSourceService,
                    source,
                    remaining
            );
            if (plan.removedAmount() <= 0L) {
                continue;
            }
            if (!InventoryItemUtil.applyRemoval(providedMaterials, plan)) {
                rollbackMaterial(providedMaterials, plans);
                return MaterialDebit.failure();
            }
            plans.add(plan);
            remaining -= plan.removedAmount();
        }
        if (remaining > 0L) {
            rollbackMaterial(providedMaterials, plans);
            return MaterialDebit.failure();
        }
        return MaterialDebit.committed(plans);
    }

    private boolean rollbackMaterial(Map<Integer, ItemStack> providedMaterials,
            List<InventoryItemUtil.RemovalPlan> plans) {
        boolean success = true;
        for (int index = plans.size() - 1; index >= 0; index--) {
            success &= InventoryItemUtil.rollbackRemoval(providedMaterials, plans.get(index));
        }
        if (!success && plugin != null) {
            plugin.getLogger().severe("Failed to fully roll back repair materials.");
        }
        return success;
    }

    public int repair(Player player, ItemStack equipment, ItemStack repairItem, RepairMaterial matched) {
        if (player == null || equipment == null || matched == null) {
            return 0;
        }
        int restored = applyRepair(equipment, matched.resolveAmount(maxDamage(equipment)));
        return restored <= 0 ? 0 : matched.amount();
    }

    public RepairResult repairWithMaterial(Player player,
            EmakiItemDefinition definition,
            ItemStack equipment,
            Map<Integer, ItemStack> providedMaterials) {
        RepairMaterial material = findAffordableMaterial(definition, providedMaterials);
        if (material == null) {
            return RepairResult.failure("repair.error.insufficient_materials", Map.of());
        }
        int restoreAmount = material.resolveAmount(maxDamage(equipment));
        if (restoreAmount <= 0) {
            return RepairResult.failure("repair.error.invalid_restore", Map.of("material", material.displaySources()));
        }
        ItemRepairEventResult eventResult = fireRepairEvent(player, definition, equipment, "material", restoreAmount);
        if (eventResult.cancelled()) {
            return RepairResult.failure("repair.error.cancelled", Map.of());
        }
        restoreAmount = eventResult.restoreAmount();
        if (restoreAmount <= 0) {
            return RepairResult.failure("repair.error.invalid_restore", Map.of("material", material.displaySources()));
        }
        MaterialDebit materialDebit = debitProvidedMaterial(providedMaterials, material);
        if (!materialDebit.success()) {
            return RepairResult.failure("repair.error.insufficient_materials", Map.of("material", material.displaySources(), "required", material.amount()));
        }
        int restored = applyRepair(equipment, restoreAmount);
        if (restored <= 0) {
            rollbackMaterial(providedMaterials, materialDebit.plans());
            return RepairResult.failure("repair.error.already_repaired", Map.of());
        }
        triggerRepaired(player, definition, equipment, "material", restored);
        return RepairResult.success(restored);
    }

    public EconomyQuote quoteEconomy(Player player, EmakiItemDefinition definition, ItemStack equipment) {
        if (definition == null || !definition.repair().enabled() || !definition.repair().hasEconomyRepair()) {
            return quoteFailure("repair.error.economy_disabled", Map.of(), equipment, 0, List.of());
        }
        int maxDamage = maxDamage(equipment);
        int currentDamage = currentDamage(equipment);
        if (maxDamage <= 0) {
            return quoteFailure("repair.error.not_repairable", Map.of(), equipment, 0, List.of());
        }
        if (currentDamage <= 0 && !isDisabled(equipment)) {
            return quoteFailure("repair.error.already_repaired", Map.of(), equipment, 0, List.of());
        }
        RepairEconomyConfig economy = definition.repair().economy();
        int restoreAmount = Math.min(Math.max(0, currentDamage), economy.resolveAmount(maxDamage));
        if (restoreAmount <= 0) {
            return quoteFailure("repair.error.invalid_restore", Map.of(), equipment, 0, List.of());
        }
        EconomyManager economyManager = economyManager();
        if (economyManager == null) {
            return quoteFailure("repair.error.economy_provider_unavailable", Map.of(), equipment, restoreAmount, List.of());
        }
        Map<String, Object> baseVariables = repairVariables(maxDamage, currentDamage, restoreAmount);
        List<CurrencyQuote> quotes = quoteCurrencies(player, economyManager, economy.currencies(), baseVariables);
        if (quotes.isEmpty()) {
            return quoteFailure("repair.error.invalid_cost", Map.of(), equipment, restoreAmount, quotes);
        }
        for (CurrencyQuote quote : quotes) {
            if (!quote.supported()) {
                return quoteFailure("repair.error.economy_provider_unavailable", quoteReplacements(quote), equipment, restoreAmount, quotes);
            }
            if (!quote.affordable()) {
                return quoteFailure("repair.error.insufficient_funds", quoteReplacements(quote), equipment, restoreAmount, quotes);
            }
        }
        return new EconomyQuote(true, "", Map.of(), currentDamage, maxDamage, restoreAmount, quotes);
    }

    public RepairResult repairWithEconomy(Player player, EmakiItemDefinition definition, ItemStack equipment) {
        EconomyQuote quote = quoteEconomy(player, definition, equipment);
        if (!quote.success()) {
            return RepairResult.failure(quote.errorKey(), quote.replacements());
        }
        EconomyManager economyManager = economyManager();
        if (economyManager == null) {
            return RepairResult.failure("repair.error.economy_provider_unavailable", Map.of());
        }
        ItemRepairEventResult eventResult = fireRepairEvent(player, definition, equipment, "economy", quote.restoreAmount());
        if (eventResult.cancelled()) {
            return RepairResult.failure("repair.error.cancelled", Map.of());
        }
        int restoreAmount = eventResult.restoreAmount();
        if (restoreAmount <= 0) {
            return RepairResult.failure("repair.error.invalid_restore", Map.of());
        }
        CurrencyChargeResult chargeResult = chargeCurrencies(player, economyManager, quote.currencies());
        if (!chargeResult.success()) {
            return RepairResult.failure(chargeResult.errorKey(), quoteReplacements(chargeResult.failedQuote()));
        }
        int restored = applyRepair(equipment, restoreAmount);
        if (restored <= 0) {
            rollbackCurrencies(player, economyManager, chargeResult.debits());
            return RepairResult.failure("repair.error.already_repaired", Map.of());
        }
        triggerRepaired(player, definition, equipment, "economy", restored);
        return RepairResult.success(restored);
    }

    private record ItemRepairEventResult(boolean cancelled, int restoreAmount) {
    }

    private ItemRepairEventResult fireRepairEvent(Player player,
            EmakiItemDefinition definition,
            ItemStack equipment,
            String source,
            int restoreAmount) {
        // 物品修复对外开放，可取消、可改修复量；在扣费前派发以保证取消即不扣费。
        if (threadOwnership == null || !threadOwnership.isEntityOwned(player)) {
            return new ItemRepairEventResult(false, restoreAmount);
        }
        emaki.jiuwu.craft.item.api.event.ItemRepairEvent event = new emaki.jiuwu.craft.item.api.event.ItemRepairEvent(
                player,
                equipment,
                definition == null ? "" : definition.id(),
                source,
                restoreAmount,
                currentDamage(equipment),
                maxDamage(equipment));
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        return new ItemRepairEventResult(event.isCancelled(), event.getRestoreAmount());
    }

    public void triggerRepaired(Player player,
            EmakiItemDefinition definition,
            ItemStack equipment,
            String source,
            int restored) {
        if (player == null || definition == null || definition.repair().onRepairedActions().isEmpty()) {
            return;
        }
        plugin.actionService().executeLines(
                player,
                definition,
                "on_repaired",
                definition.repair().onRepairedActions(),
                Map.of("item_id", definition.id(), "repair_source", Texts.toStringSafe(source), "restore", restored),
                equipment
        );
    }

    public int maxDamage(ItemStack equipment) {
        if (equipment == null || equipment.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = equipment.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        return damageable.hasMaxDamage() ? damageable.getMaxDamage() : equipment.getType().getMaxDurability();
    }

    public int currentDamage(ItemStack equipment) {
        if (equipment == null || equipment.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = equipment.getItemMeta();
        return meta instanceof Damageable damageable ? damageable.getDamage() : 0;
    }

    public int applyRepair(ItemStack equipment, int restoreAmount) {
        if (equipment == null || equipment.getType().isAir() || restoreAmount <= 0) {
            return 0;
        }
        ItemMeta meta = equipment.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        int maxDamage = maxDamage(equipment);
        if (maxDamage <= 0) {
            return 0;
        }
        int currentDamage = damageable.getDamage();
        int newDamage = Math.max(0, currentDamage - restoreAmount);
        if (newDamage == currentDamage && !isDisabled(equipment)) {
            return 0;
        }
        damageable.setDamage(newDamage);
        equipment.setItemMeta(meta);
        if (newDamage < maxDamage) {
            clearDisabled(equipment);
        }
        return Math.max(0, currentDamage - newDamage);
    }

    private EconomyQuote quoteFailure(String errorKey,
            Map<String, Object> replacements,
            ItemStack equipment,
            int restoreAmount,
            List<CurrencyQuote> currencies) {
        return new EconomyQuote(
                false,
                errorKey,
                replacements,
                currentDamage(equipment),
                maxDamage(equipment),
                restoreAmount,
                currencies
        );
    }

    private Map<String, Object> repairVariables(int maxDamage, int currentDamage, int restoreAmount) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("max_damage", maxDamage);
        variables.put("damage", currentDamage);
        variables.put("current_damage", currentDamage);
        variables.put("missing_durability", currentDamage);
        variables.put("missing_ratio", maxDamage <= 0 ? 0D : currentDamage / (double) maxDamage);
        variables.put("restore_amount", restoreAmount);
        variables.put("restore_ratio", maxDamage <= 0 ? 0D : restoreAmount / (double) maxDamage);
        return variables;
    }

    private double resolveCurrencyAmount(RepairCurrencyCost currency, Map<String, Object> variables) {
        if (currency.amount() > 0D) {
            return currency.amount();
        }
        Map<String, Object> context = new LinkedHashMap<>(variables == null ? Map.of() : variables);
        context.put("base_cost", currency.baseCost());
        if (Texts.isNotBlank(currency.costFormula())) {
            return Math.max(0D, ExpressionEngine.evaluate(currency.costFormula(), context));
        }
        return Math.max(0D, currency.baseCost());
    }

    private List<CurrencyQuote> quoteCurrencies(Player player,
            EconomyManager economyManager,
            List<RepairCurrencyCost> costs,
            Map<String, Object> variables) {
        Map<CurrencyKey, ResolvedCurrency> resolved = new LinkedHashMap<>();
        if (costs != null) {
            for (RepairCurrencyCost cost : costs) {
                if (cost == null) {
                    continue;
                }
                double amount = resolveCurrencyAmount(cost, variables);
                if (amount <= 0D) {
                    continue;
                }
                CurrencyKey key = new CurrencyKey(cost.provider(), cost.currencyId());
                ResolvedCurrency existing = resolved.get(key);
                resolved.put(key, new ResolvedCurrency(
                        existing == null ? cost : existing.cost(),
                        (existing == null ? 0D : existing.amount()) + amount
                ));
            }
        }
        List<CurrencyQuote> quotes = new ArrayList<>(resolved.size());
        for (ResolvedCurrency currency : resolved.values()) {
            ActionResult supported = economyManager.requireSupported(currency.cost().provider(), currency.cost().currencyId());
            boolean providerSupported = supported.success();
            double balance = providerSupported
                    ? economyManager.getBalance(player, currency.cost().provider(), currency.cost().currencyId())
                    : 0D;
            quotes.add(new CurrencyQuote(currency.cost(), currency.amount(), balance, providerSupported));
        }
        return List.copyOf(quotes);
    }

    CurrencyChargeResult chargeCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyQuote> quotes) {
        if (player == null || economyManager == null) {
            return CurrencyChargeResult.failure("repair.error.economy_provider_unavailable", null, List.of(), true);
        }
        Map<CurrencyKey, ResolvedCurrency> aggregated = new LinkedHashMap<>();
        if (quotes != null) {
            for (CurrencyQuote quote : quotes) {
                if (quote == null || quote.cost() == null || quote.amount() <= 0D) {
                    continue;
                }
                CurrencyKey key = new CurrencyKey(quote.cost().provider(), quote.cost().currencyId());
                ResolvedCurrency existing = aggregated.get(key);
                aggregated.put(key, new ResolvedCurrency(
                        existing == null ? quote.cost() : existing.cost(),
                        (existing == null ? 0D : existing.amount()) + quote.amount()
                ));
            }
        }
        List<CurrencyQuote> normalized = new ArrayList<>(aggregated.size());
        for (ResolvedCurrency currency : aggregated.values()) {
            double balance = economyManager.getBalance(player, currency.cost().provider(), currency.cost().currencyId());
            CurrencyQuote quote = new CurrencyQuote(currency.cost(), currency.amount(), balance, true);
            if (balance + 1.0E-9D < currency.amount()) {
                return CurrencyChargeResult.failure("repair.error.insufficient_funds", quote, List.of(), true);
            }
            normalized.add(quote);
        }

        List<CurrencyDebit> debits = new ArrayList<>();
        for (CurrencyQuote quote : normalized) {
            double before = economyManager.getBalance(player, quote.cost().provider(), quote.cost().currencyId());
            ActionResult result = economyManager.remove(player, quote.cost().provider(), quote.cost().currencyId(), quote.amount());
            double after = economyManager.getBalance(player, quote.cost().provider(), quote.cost().currencyId());
            double debited = Math.max(0D, before - after);
            if (debited > 1.0E-9D) {
                debits.add(new CurrencyDebit(quote.cost().provider(), quote.cost().currencyId(), debited, before));
            }
            if (!result.success() || debited + 1.0E-9D < quote.amount()) {
                boolean compensated = rollbackCurrencies(player, economyManager, debits);
                String errorKey = compensated && result.errorType() == ActionErrorType.INSUFFICIENT_BALANCE
                        ? "repair.error.insufficient_funds"
                        : "repair.error.economy_provider_unavailable";
                return CurrencyChargeResult.failure(errorKey, quote, compensated ? List.of() : debits, compensated);
            }
        }
        return CurrencyChargeResult.committed(debits);
    }

    private boolean rollbackCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return true;
        }
        if (player == null || economyManager == null) {
            return false;
        }
        boolean success = true;
        for (int index = debits.size() - 1; index >= 0; index--) {
            CurrencyDebit debit = debits.get(index);
            ActionResult result = economyManager.add(player, debit.provider(), debit.currencyId(), debit.amount());
            double restored = economyManager.getBalance(player, debit.provider(), debit.currencyId());
            success &= result.success() && Math.abs(restored - debit.balanceBefore()) <= 1.0E-6D;
        }
        if (!success && plugin != null) {
            plugin.getLogger().severe("Failed to fully compensate repair economy costs.");
        }
        return success;
    }

    private Map<String, Object> quoteReplacements(CurrencyQuote quote) {
        if (quote == null || quote.cost() == null) {
            return Map.of();
        }
        return Map.of(
                "provider", quote.cost().provider(),
                "currency", quote.cost().currencyId(),
                "currency_id", quote.cost().currencyId(),
                "name", quote.cost().effectiveDisplayName(),
                "display_name", quote.cost().effectiveDisplayName(),
                "required", formatAmount(quote.amount()),
                "available", formatAmount(quote.balance())
        );
    }

    private String formatAmount(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record MaterialDebit(boolean success, List<InventoryItemUtil.RemovalPlan> plans) {

        private MaterialDebit {
            plans = plans == null ? List.of() : List.copyOf(plans);
        }

        static MaterialDebit committed(List<InventoryItemUtil.RemovalPlan> plans) {
            return new MaterialDebit(true, plans);
        }

        static MaterialDebit failure() {
            return new MaterialDebit(false, List.of());
        }
    }

    record CurrencyChargeResult(boolean success,
            String errorKey,
            CurrencyQuote failedQuote,
            List<CurrencyDebit> debits,
            boolean compensationComplete) {

        CurrencyChargeResult {
            debits = debits == null ? List.of() : List.copyOf(debits);
        }

        static CurrencyChargeResult committed(List<CurrencyDebit> debits) {
            return new CurrencyChargeResult(true, "", null, debits, true);
        }

        static CurrencyChargeResult failure(String errorKey,
                CurrencyQuote failedQuote,
                List<CurrencyDebit> debits,
                boolean compensationComplete) {
            return new CurrencyChargeResult(false, errorKey, failedQuote, debits, compensationComplete);
        }
    }

    record CurrencyDebit(String provider, String currencyId, double amount, double balanceBefore) {

    }

    private record CurrencyKey(String provider, String currencyId) {

    }

    private record ResolvedCurrency(RepairCurrencyCost cost, double amount) {

    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

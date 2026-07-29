package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

public final class GemEconomyService {

    public record ChargeRequest(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials,
            Map<String, ?> variables,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {

        public ChargeRequest {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            providedMaterials = providedMaterials == null ? Map.of() : providedMaterials;
        }

        public static ChargeRequest from(Player player, GemDefinition.CostConfig costConfig, Map<String, ?> variables) {
            if (costConfig == null) {
                return new ChargeRequest(player, List.of(), List.of(), variables, Map.of(), true);
            }
            return new ChargeRequest(player, costConfig.currencies(), costConfig.materials(), variables, Map.of(), true);
        }
    }

    public record ChargeResult(boolean success,
            String errorKey,
            Map<String, Object> placeholders,
            List<GemDefinition.CurrencyCost> chargedCurrencies,
            List<GemDefinition.MaterialCost> chargedMaterials,
            boolean compensationComplete,
            ChargeReceipt receipt) {

        public ChargeResult {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
            chargedCurrencies = chargedCurrencies == null ? List.of() : List.copyOf(chargedCurrencies);
            chargedMaterials = chargedMaterials == null ? List.of() : List.copyOf(chargedMaterials);
        }

        public static ChargeResult success(List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials,
                ChargeReceipt receipt) {
            return new ChargeResult(true, "", Map.of(), chargedCurrencies, chargedMaterials, true, receipt);
        }

        public static ChargeResult failure(String errorKey,
                List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials) {
            return failure(errorKey, Map.of(), chargedCurrencies, chargedMaterials);
        }

        public static ChargeResult failure(String errorKey,
                Map<String, Object> placeholders,
                List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials) {
            boolean compensationComplete = (chargedCurrencies == null || chargedCurrencies.isEmpty())
                    && (chargedMaterials == null || chargedMaterials.isEmpty());
            return new ChargeResult(false, errorKey, placeholders, chargedCurrencies, chargedMaterials,
                    compensationComplete, null);
        }
    }

    public record CurrencyDebit(String provider,
            String currencyId,
            double amount,
            double balanceBefore) {

    }

    public record MaterialDebit(GemDefinition.MaterialCost cost,
            Map<Integer, ItemStack> providedMaterials,
            InventoryItemUtil.RemovalPlan providedPlan,
            PlayerInventory inventory,
            InventoryItemUtil.RemovalPlan inventoryPlan) {

    }

    public record ChargeReceipt(UUID id,
            List<CurrencyDebit> currencyDebits,
            List<MaterialDebit> materialDebits) {

        public ChargeReceipt {
            id = id == null ? UUID.randomUUID() : id;
            currencyDebits = currencyDebits == null ? List.of() : List.copyOf(currencyDebits);
            materialDebits = materialDebits == null ? List.of() : List.copyOf(materialDebits);
        }
    }

    public record RefundResult(boolean success,
            List<GemDefinition.CurrencyCost> remainingCurrencies,
            List<GemDefinition.MaterialCost> remainingMaterials) {

        public RefundResult {
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        }

        public static RefundResult complete() {
            return new RefundResult(true, List.of(), List.of());
        }

        public static RefundResult incomplete(List<GemDefinition.CurrencyCost> remainingCurrencies,
                List<GemDefinition.MaterialCost> remainingMaterials) {
            return new RefundResult(false, remainingCurrencies, remainingMaterials);
        }
    }

    private final EmakiGemPlugin plugin;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;
    private final Set<UUID> refundedReceipts = ConcurrentHashMap.newKeySet();

    public GemEconomyService(EmakiGemPlugin plugin,
            Supplier<EconomyManager> economyManagerSupplier,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
    }

    public ChargeResult charge(Player player, GemDefinition.CostConfig costConfig) {
        return charge(ChargeRequest.from(player, costConfig, Map.of()));
    }

    public ChargeResult charge(Player player, GemDefinition.CostConfig costConfig, Map<String, ?> variables) {
        return charge(ChargeRequest.from(player, costConfig, variables));
    }

    public ChargeResult charge(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {
        return charge(new ChargeRequest(player, currencies, materials, Map.of(), Map.of(), true));
    }

    public ChargeResult charge(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials,
            Map<String, ?> variables) {
        return charge(new ChargeRequest(player, currencies, materials, variables, Map.of(), true));
    }

    public ChargeResult charge(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials,
            Map<String, ?> variables,
            Map<Integer, ItemStack> providedMaterials) {
        return charge(new ChargeRequest(player, currencies, materials, variables, providedMaterials, true));
    }

    public ChargeResult chargeProvidedOnly(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials,
            Map<String, ?> variables,
            Map<Integer, ItemStack> providedMaterials) {
        return charge(new ChargeRequest(player, currencies, materials, variables, providedMaterials, false));
    }

    public ChargeResult charge(ChargeRequest request) {
        if (request == null || request.player() == null || request.player().getInventory() == null) {
            return ChargeResult.failure("gem.error.player_required", List.of(), List.of());
        }
        Player player = request.player();
        List<GemDefinition.CurrencyCost> safeCurrencies = aggregateCurrencies(resolveCurrencies(request.currencies(), request.variables()));
        List<GemDefinition.MaterialCost> safeMaterials = aggregateMaterials(request.materials());
        EconomyManager economyManager = economyManager();

        for (GemDefinition.CurrencyCost currency : safeCurrencies) {
            double available = balanceOf(player, currency);
            if (economyManager == null || !canAfford(currency, available)) {
                return ChargeResult.failure("gem.error.insufficient_currency", currencyPlaceholders(currency, available), List.of(), List.of());
            }
        }

        List<MaterialDebit> materialDebits = new ArrayList<>();
        for (GemDefinition.MaterialCost material : safeMaterials) {
            InventoryItemUtil.RemovalPlan providedPlan = InventoryItemUtil.planRemoval(
                    request.providedMaterials(),
                    itemSourceService,
                    material.itemSource(),
                    material.amount()
            );
            long remaining = providedPlan.remainingAmount();
            InventoryItemUtil.RemovalPlan inventoryPlan = remaining > 0L && request.allowInventoryFallback()
                    ? InventoryItemUtil.planRemoval(player.getInventory(), itemSourceService, material.itemSource(), remaining)
                    : InventoryItemUtil.RemovalPlan.empty(remaining);
            long available = providedPlan.removedAmount() + inventoryPlan.removedAmount();
            if (available < material.amount()) {
                CompensationResult compensation = rollbackCharge(player, economyManager, List.of(), materialDebits);
                return ChargeResult.failure(
                        "gem.error.insufficient_material",
                        materialPlaceholders(material, available),
                        compensation.remainingCurrencies(),
                        compensation.remainingMaterials()
                );
            }
            boolean providedApplied = providedPlan.removedAmount() <= 0L
                    || InventoryItemUtil.applyRemoval(request.providedMaterials(), providedPlan);
            boolean inventoryApplied = providedApplied && (inventoryPlan.removedAmount() <= 0L
                    || InventoryItemUtil.applyRemoval(player.getInventory(), inventoryPlan));
            if (!inventoryApplied) {
                List<MaterialDebit> appliedDebits = new ArrayList<>(materialDebits);
                if (providedApplied && providedPlan.removedAmount() > 0L) {
                    appliedDebits.add(new MaterialDebit(
                            material,
                            request.providedMaterials(),
                            providedPlan,
                            player.getInventory(),
                            InventoryItemUtil.RemovalPlan.empty(0L)
                    ));
                }
                CompensationResult compensation = rollbackCharge(player, economyManager, List.of(), appliedDebits);
                return ChargeResult.failure(
                        "gem.error.insufficient_material",
                        materialPlaceholders(material, availableMaterialAmount(player, material, request.providedMaterials(), request.allowInventoryFallback())),
                        compensation.remainingCurrencies(),
                        compensation.remainingMaterials()
                );
            }
            materialDebits.add(new MaterialDebit(
                    material,
                    request.providedMaterials(),
                    providedPlan,
                    player.getInventory(),
                    inventoryPlan
            ));
        }

        List<CurrencyDebit> currencyDebits = new ArrayList<>();
        for (GemDefinition.CurrencyCost currency : safeCurrencies) {
            double before;
            try {
                before = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                CompensationResult compensation = rollbackCharge(player, economyManager, currencyDebits, materialDebits);
                return failedCurrencyCharge(player, currency, compensation);
            }
            ActionResult result = null;
            boolean providerFailed = false;
            try {
                result = economyManager.remove(player, currency.provider(), currency.currencyId(), currency.amount());
            } catch (RuntimeException | LinkageError exception) {
                providerFailed = true;
            }
            double after = before;
            try {
                after = economyManager.getBalance(player, currency.provider(), currency.currencyId());
            } catch (RuntimeException | LinkageError exception) {
                providerFailed = true;
            }
            double debited = Math.max(0D, before - after);
            if (debited > 1.0E-9D) {
                currencyDebits.add(new CurrencyDebit(currency.provider(), currency.currencyId(), debited, before));
            }
            if (providerFailed || result == null || !result.success()
                    || debited + 1.0E-9D < currency.amount()) {
                CompensationResult compensation = rollbackCharge(player, economyManager, currencyDebits, materialDebits);
                return failedCurrencyCharge(player, currency, compensation);
            }
        }

        ChargeReceipt receipt = new ChargeReceipt(UUID.randomUUID(), currencyDebits, materialDebits);
        return ChargeResult.success(currencyCosts(currencyDebits), materialCosts(materialDebits), receipt);
    }

    private ChargeResult failedCurrencyCharge(Player player,
            GemDefinition.CurrencyCost currency,
            CompensationResult compensation) {
        if (!compensation.complete()) {
            logCompensationFailure(player, "charge");
        }
        return ChargeResult.failure(
                "gem.error.insufficient_currency",
                currencyPlaceholders(currency, balanceOf(player, currency)),
                compensation.remainingCurrencies(),
                compensation.remainingMaterials()
        );
    }

    private List<GemDefinition.CurrencyCost> resolveCurrencies(List<GemDefinition.CurrencyCost> currencies, Map<String, ?> variables) {
        if (currencies == null || currencies.isEmpty()) {
            return List.of();
        }
        List<GemDefinition.CurrencyCost> resolved = new ArrayList<>();
        for (GemDefinition.CurrencyCost currency : currencies) {
            if (currency == null) {
                continue;
            }
            GemDefinition.CurrencyCost resolvedCurrency = currency.resolve(variables);
            if (resolvedCurrency != null && resolvedCurrency.amount() > 0D) {
                resolved.add(resolvedCurrency);
            }
        }
        return List.copyOf(resolved);
    }

    private List<GemDefinition.CurrencyCost> aggregateCurrencies(List<GemDefinition.CurrencyCost> currencies) {
        if (currencies == null || currencies.isEmpty()) {
            return List.of();
        }
        Map<CurrencyKey, GemDefinition.CurrencyCost> aggregated = new LinkedHashMap<>();
        for (GemDefinition.CurrencyCost currency : currencies) {
            if (currency == null || currency.amount() <= 0D) {
                continue;
            }
            CurrencyKey key = new CurrencyKey(currency.provider(), currency.currencyId());
            GemDefinition.CurrencyCost existing = aggregated.get(key);
            double amount = (existing == null ? 0D : existing.amount()) + currency.amount();
            GemDefinition.CurrencyCost template = existing == null ? currency : existing;
            aggregated.put(key, new GemDefinition.CurrencyCost(
                    template.provider(),
                    template.currencyId(),
                    amount,
                    template.baseCost(),
                    template.costFormula(),
                    template.displayName()
            ));
        }
        return List.copyOf(aggregated.values());
    }

    private List<GemDefinition.MaterialCost> aggregateMaterials(List<GemDefinition.MaterialCost> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        Map<ItemSource, Integer> aggregated = new LinkedHashMap<>();
        for (GemDefinition.MaterialCost material : materials) {
            if (material == null || material.itemSource() == null || material.amount() <= 0) {
                continue;
            }
            aggregated.merge(material.itemSource(), material.amount(), this::saturatedAdd);
        }
        List<GemDefinition.MaterialCost> result = new ArrayList<>(aggregated.size());
        for (Map.Entry<ItemSource, Integer> entry : aggregated.entrySet()) {
            result.add(new GemDefinition.MaterialCost(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private List<GemDefinition.CurrencyCost> currencyCosts(List<CurrencyDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        List<GemDefinition.CurrencyCost> costs = new ArrayList<>(debits.size());
        for (CurrencyDebit debit : debits) {
            if (debit != null && debit.amount() > 1.0E-9D) {
                costs.add(new GemDefinition.CurrencyCost(
                        debit.provider(), debit.currencyId(), debit.amount(), 0D, "", debit.currencyId()));
            }
        }
        return aggregateCurrencies(costs);
    }

    private List<GemDefinition.MaterialCost> materialCosts(List<MaterialDebit> debits) {
        if (debits == null || debits.isEmpty()) {
            return List.of();
        }
        List<GemDefinition.MaterialCost> costs = new ArrayList<>(debits.size());
        for (MaterialDebit debit : debits) {
            if (debit == null || debit.cost() == null || debit.cost().itemSource() == null) {
                continue;
            }
            long amount = (debit.providedPlan() == null ? 0L : debit.providedPlan().removedAmount())
                    + (debit.inventoryPlan() == null ? 0L : debit.inventoryPlan().removedAmount());
            if (amount > 0L) {
                costs.add(new GemDefinition.MaterialCost(
                        debit.cost().itemSource(), (int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }
        return aggregateMaterials(costs);
    }

    public boolean refund(Player player, ChargeResult chargeResult) {
        return refundDetailed(player, chargeResult).success();
    }

    public RefundResult refundDetailed(Player player, ChargeResult chargeResult) {
        if (player == null || chargeResult == null || !chargeResult.success()) {
            return RefundResult.incomplete(List.of(), List.of());
        }
        ChargeReceipt receipt = chargeResult.receipt();
        if (receipt == null) {
            boolean refunded = refundPersisted(player,
                    chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
            return refunded
                    ? RefundResult.complete()
                    : RefundResult.incomplete(chargeResult.chargedCurrencies(), chargeResult.chargedMaterials());
        }
        if (!refundedReceipts.add(receipt.id())) {
            return RefundResult.incomplete(List.of(), List.of());
        }
        EconomyManager economyManager = economyManager();
        CompensationResult compensation = rollbackCharge(
                player, economyManager, receipt.currencyDebits(), receipt.materialDebits());
        if (!compensation.complete()) {
            logCompensationFailure(player, "refund");
            return RefundResult.incomplete(
                    compensation.remainingCurrencies(), compensation.remainingMaterials());
        }
        return RefundResult.complete();
    }

    public void refund(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {
        refundPersisted(player, currencies, materials);
    }

    public boolean refundPersisted(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {
        return refundPersistedDetailed(player, currencies, materials).success();
    }

    public RefundResult refundPersistedDetailed(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {
        List<GemDefinition.CurrencyCost> remainingCurrencies = new ArrayList<>();
        List<GemDefinition.MaterialCost> remainingMaterials = new ArrayList<>();
        if (player == null) {
            return RefundResult.incomplete(aggregateCurrencies(currencies), aggregateMaterials(materials));
        }
        EconomyManager economyManager = economyManager();
        for (GemDefinition.CurrencyCost currency : aggregateCurrencies(currencies)) {
            boolean refunded = false;
            if (economyManager != null) {
                try {
                    ActionResult result = economyManager.add(
                            player, currency.provider(), currency.currencyId(), currency.amount());
                    refunded = result != null && result.success();
                } catch (RuntimeException | LinkageError ignored) {
                    refunded = false;
                }
            }
            if (!refunded) {
                remainingCurrencies.add(currency);
            }
        }
        for (GemDefinition.MaterialCost material : aggregateMaterials(materials)) {
            if (!addItemCost(player, material)) {
                remainingMaterials.add(material);
            }
        }
        if (!remainingCurrencies.isEmpty() || !remainingMaterials.isEmpty()) {
            logCompensationFailure(player, "persisted refund");
            return RefundResult.incomplete(remainingCurrencies, remainingMaterials);
        }
        return RefundResult.complete();
    }

    private CompensationResult rollbackCharge(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits,
            List<MaterialDebit> materialDebits) {
        List<GemDefinition.CurrencyCost> remainingCurrencies = rollbackCurrencyDebits(player, economyManager, currencyDebits);
        List<GemDefinition.MaterialCost> remainingMaterials = rollbackMaterialDebits(materialDebits);
        return new CompensationResult(remainingCurrencies, remainingMaterials);
    }

    private List<GemDefinition.CurrencyCost> rollbackCurrencyDebits(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits) {
        if (currencyDebits == null || currencyDebits.isEmpty()) {
            return List.of();
        }
        List<GemDefinition.CurrencyCost> remaining = new ArrayList<>();
        for (int index = currencyDebits.size() - 1; index >= 0; index--) {
            CurrencyDebit debit = currencyDebits.get(index);
            double amount = debit.amount();
            if (player != null && economyManager != null) {
                try {
                    double current = economyManager.getBalance(player, debit.provider(), debit.currencyId());
                    double needed = Math.min(debit.amount(), Math.max(0D, debit.balanceBefore() - current));
                    if (needed > 1.0E-9D) {
                        economyManager.add(player, debit.provider(), debit.currencyId(), needed);
                    }
                    double restored = economyManager.getBalance(player, debit.provider(), debit.currencyId());
                    amount = Math.max(0D, debit.balanceBefore() - restored);
                } catch (RuntimeException | LinkageError ignored) {
                    amount = debit.amount();
                }
            }
            if (amount > 1.0E-9D) {
                remaining.add(new GemDefinition.CurrencyCost(
                        debit.provider(), debit.currencyId(), amount, 0D, "", debit.currencyId()));
            }
        }
        return remaining.isEmpty() ? List.of() : aggregateCurrencies(remaining);
    }

    private List<GemDefinition.MaterialCost> rollbackMaterialDebits(List<MaterialDebit> materialDebits) {
        if (materialDebits == null || materialDebits.isEmpty()) {
            return List.of();
        }
        Map<ItemSource, Integer> remaining = new LinkedHashMap<>();
        for (int index = materialDebits.size() - 1; index >= 0; index--) {
            MaterialDebit debit = materialDebits.get(index);
            long amount = 0L;
            if (debit.inventoryPlan() != null && debit.inventoryPlan().removedAmount() > 0L
                    && !InventoryItemUtil.rollbackRemoval(debit.inventory(), debit.inventoryPlan())) {
                amount += debit.inventoryPlan().removedAmount();
            }
            if (debit.providedPlan() != null && debit.providedPlan().removedAmount() > 0L
                    && !InventoryItemUtil.rollbackRemoval(debit.providedMaterials(), debit.providedPlan())) {
                amount += debit.providedPlan().removedAmount();
            }
            if (amount > 0L && debit.cost() != null && debit.cost().itemSource() != null) {
                remaining.merge(debit.cost().itemSource(),
                        (int) Math.min(Integer.MAX_VALUE, amount),
                        this::saturatedAdd);
            }
        }
        if (remaining.isEmpty()) {
            return List.of();
        }
        List<GemDefinition.MaterialCost> costs = new ArrayList<>(remaining.size());
        remaining.forEach((source, amount) -> costs.add(new GemDefinition.MaterialCost(source, amount)));
        return List.copyOf(costs);
    }

    private boolean rollbackCurrencies(Player player,
            EconomyManager economyManager,
            List<CurrencyDebit> currencyDebits) {
        if (currencyDebits == null || currencyDebits.isEmpty()) {
            return true;
        }
        if (player == null || economyManager == null) {
            return false;
        }
        boolean success = true;
        for (int index = currencyDebits.size() - 1; index >= 0; index--) {
            CurrencyDebit debit = currencyDebits.get(index);
            ActionResult result = economyManager.add(player, debit.provider(), debit.currencyId(), debit.amount());
            double restored = economyManager.getBalance(player, debit.provider(), debit.currencyId());
            success &= result.success() && Math.abs(restored - debit.balanceBefore()) <= 1.0E-6D;
        }
        return success;
    }

    private boolean rollbackMaterials(List<MaterialDebit> materialDebits) {
        if (materialDebits == null || materialDebits.isEmpty()) {
            return true;
        }
        boolean success = true;
        for (int index = materialDebits.size() - 1; index >= 0; index--) {
            MaterialDebit debit = materialDebits.get(index);
            if (debit.inventoryPlan() != null && debit.inventoryPlan().removedAmount() > 0L) {
                success &= InventoryItemUtil.rollbackRemoval(debit.inventory(), debit.inventoryPlan());
            }
            if (debit.providedPlan() != null && debit.providedPlan().removedAmount() > 0L) {
                success &= InventoryItemUtil.rollbackRemoval(debit.providedMaterials(), debit.providedPlan());
            }
        }
        return success;
    }

    private double balanceOf(Player player, GemDefinition.CurrencyCost currency) {
        if (currency == null || currency.amount() <= 0D) {
            return Double.MAX_VALUE;
        }
        EconomyManager economyManager = economyManager();
        if (economyManager == null) {
            return 0D;
        }
        try {
            return economyManager.getBalance(player, currency.provider(), currency.currencyId());
        } catch (RuntimeException | LinkageError ignored) {
            return 0D;
        }
    }

    private boolean canAfford(GemDefinition.CurrencyCost currency, double available) {
        return currency == null || currency.amount() <= 0D || available + 1.0E-9D >= currency.amount();
    }

    private long availableMaterialAmount(Player player,
            GemDefinition.MaterialCost material,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        if (material == null || material.itemSource() == null || material.amount() <= 0) {
            return Long.MAX_VALUE;
        }
        long available = countProvidedItemCost(providedMaterials, material.itemSource());
        if (allowInventoryFallback) {
            available += countItemCost(player, material.itemSource());
        }
        return available;
    }

    private long countItemCost(Player player, ItemSource targetSource) {
        return InventoryItemUtil.countItems(player, itemSourceService, targetSource);
    }

    private Map<String, Object> currencyPlaceholders(GemDefinition.CurrencyCost currency, double available) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("provider", currency == null ? "" : currency.provider());
        placeholders.put("currency", currency == null ? "" : currency.currencyId());
        placeholders.put("currency_id", currency == null ? "" : currency.currencyId());
        placeholders.put("name", currency == null || Texts.isBlank(currency.displayName()) ? (currency == null ? "" : currency.currencyId()) : currency.displayName());
        placeholders.put("display_name", placeholders.get("name"));
        placeholders.put("required", currency == null ? 0D : currency.amount());
        placeholders.put("available", Math.max(0D, available));
        return placeholders;
    }

    private Map<String, Object> materialPlaceholders(GemDefinition.MaterialCost material, long available) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        String item = material == null || material.itemSource() == null ? "" : ItemSourceUtil.toShorthand(material.itemSource());
        String displayName = material == null || material.itemSource() == null
                ? ""
                : EmakiCoreLibApi.itemDisplayName(item);
        placeholders.put("item", Texts.isBlank(item) ? "unknown" : item);
        placeholders.put("material", Texts.isBlank(displayName) ? placeholders.get("item") : displayName);
        placeholders.put("display_name", placeholders.get("material"));
        placeholders.put("required", material == null ? 0 : material.amount());
        placeholders.put("available", Math.max(0L, available));
        return placeholders;
    }

    private long countProvidedItemCost(Map<Integer, ItemStack> providedItems, ItemSource targetSource) {
        return InventoryItemUtil.countItems(providedItems, itemSourceService, targetSource);
    }

    private boolean addItemCost(Player player, GemDefinition.MaterialCost material) {
        if (player == null || material == null || material.itemSource() == null || material.amount() <= 0 || plugin == null) {
            return false;
        }
        ItemStack itemStack = plugin.coreItemSourceService().createItem(material.itemSource(), material.amount());
        if (itemStack == null) {
            return false;
        }
        InventoryItemUtil.giveOrDrop(player, itemStack);
        return true;
    }

    private int saturatedAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    private void logCompensationFailure(Player player, String phase) {
        if (plugin != null) {
            plugin.getLogger().severe("Failed to fully compensate gem costs during " + phase + " for "
                    + (player == null ? "unknown" : player.getUniqueId()));
        }
    }

    private record CompensationResult(List<GemDefinition.CurrencyCost> remainingCurrencies,
            List<GemDefinition.MaterialCost> remainingMaterials) {

        private CompensationResult {
            remainingCurrencies = remainingCurrencies == null ? List.of() : List.copyOf(remainingCurrencies);
            remainingMaterials = remainingMaterials == null ? List.of() : List.copyOf(remainingMaterials);
        }

        private boolean complete() {
            return remainingCurrencies.isEmpty() && remainingMaterials.isEmpty();
        }
    }

    private record CurrencyKey(String provider, String currencyId) {

    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.cost.CostReceipt;
import emaki.jiuwu.craft.corelib.cost.CostTransaction;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.ItemPdcKeys;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairCurrencyCost;
import emaki.jiuwu.craft.item.model.RepairEconomyConfig;
import emaki.jiuwu.craft.item.model.RepairMaterial;
import emaki.jiuwu.craft.item.api.event.ItemRepairEvent;

public final class ItemRepairService {

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
    private final EmakiScheduling scheduling;

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
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.economyManagerSupplier = economyManagerSupplier;
        this.itemSourceService = itemSourceService;
        this.scheduling = scheduling;
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
        Byte value = pdc.get(ItemPdcKeys.DISABLED, PersistentDataType.BYTE);
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
        Byte previous = meta.getPersistentDataContainer().get(ItemPdcKeys.DISABLED, PersistentDataType.BYTE);
        meta.getPersistentDataContainer().set(ItemPdcKeys.DISABLED, PersistentDataType.BYTE, (byte) 1);
        boolean committed = itemStack.setItemMeta(meta);
        logPdcMutation(itemStack, "set", previous == null ? "{}" : "{emakiitem:disabled=" + previous + "}",
                "1", "{emakiitem:disabled=1}", committed);
    }

    public void clearDisabled(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        Byte previous = meta.getPersistentDataContainer().get(ItemPdcKeys.DISABLED, PersistentDataType.BYTE);
        meta.getPersistentDataContainer().remove(ItemPdcKeys.DISABLED);
        boolean committed = itemStack.setItemMeta(meta);
        logPdcMutation(itemStack, "remove", previous == null ? "{}" : "{emakiitem:disabled=" + previous + "}",
                "", "{}", committed);
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
        ItemSourceRef repairItemSource = sourceService == null ? null : sourceService.identifyItem(repairItem);
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
            ItemSourceRef source = sourceService.identifyItem(itemStack);
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
        for (ItemSourceRef source : material.itemSources()) {
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

    public int repair(Player player,
            EmakiItemDefinition definition,
            ItemStack equipment,
            ItemStack repairItem,
            RepairMaterial matched) {
        if (player == null || equipment == null || matched == null) {
            return 0;
        }
        ItemRepairEventResult eventResult = fireRepairEvent(
                player, definition, equipment, "material", matched.resolveAmount(maxDamage(equipment)));
        if (eventResult.cancelled()) {
            return 0;
        }
        int restored = applyRepair(equipment, eventResult.restoreAmount());
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
            if (chargeResult.receipt() != null) {
                chargeResult.receipt().rollback();
            }
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

        if (scheduling == null || !scheduling.ownsEntity(player)) {
            return new ItemRepairEventResult(false, restoreAmount);
        }
        ItemRepairEvent event = new ItemRepairEvent(
                player,
                equipment,
                definition == null ? "" : definition.id(),
                source,
                restoreAmount,
                currentDamage(equipment),
                maxDamage(equipment));
        Bukkit.getPluginManager().callEvent(event);
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
            return CurrencyChargeResult.failure("repair.error.economy_provider_unavailable", null, true, null);
        }
        List<CostTransaction.CurrencyCharge> charges = new ArrayList<>();
        if (quotes != null) {
            for (CurrencyQuote quote : quotes) {
                if (quote != null && quote.cost() != null && quote.amount() > 0D) {
                    charges.add(new CostTransaction.CurrencyCharge(
                            quote.cost().provider(), quote.cost().currencyId(), quote.amount()));
                }
            }
        }
        CostReceipt receipt = CostTransaction.execute(player, economyManager, null, charges, List.of());
        if (receipt.success()) {
            return CurrencyChargeResult.committed(receipt);
        }
        boolean compensated = receipt.compensationComplete();
        return switch (receipt.failureReason()) {
            case INSUFFICIENT_FUNDS ->
                    CurrencyChargeResult.failure("repair.error.insufficient_funds", null, compensated, receipt);
            default ->
                    CurrencyChargeResult.failure("repair.error.economy_provider_unavailable", null, compensated, receipt);
        };
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
        return String.format(Locale.ROOT, "%.2f", value);
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
            boolean compensationComplete,
            CostReceipt receipt) {

        CurrencyChargeResult {
        }

        static CurrencyChargeResult committed(CostReceipt receipt) {
            return new CurrencyChargeResult(true, "", null, true, receipt);
        }

        static CurrencyChargeResult failure(String errorKey,
                CurrencyQuote failedQuote,
                boolean compensationComplete,
                CostReceipt receipt) {
            return new CurrencyChargeResult(false, errorKey, failedQuote, compensationComplete, receipt);
        }
    }

    private record CurrencyKey(String provider, String currencyId) {

    }

    private record ResolvedCurrency(RepairCurrencyCost cost, double amount) {

    }

    private void logPdcMutation(ItemStack itemStack,
            String operation,
            String before,
            String value,
            String after,
            boolean committed) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("operation", operation);
        replacements.put("item", itemStack == null ? "null" : itemStack.getType());
        replacements.put("amount", itemStack == null ? 0 : itemStack.getAmount());
        replacements.put("key", ItemPdcKeys.DISABLED);
        replacements.put("value", value);
        replacements.put("before", before);
        replacements.put("after", after);
        replacements.put("added", operation.equals("set") ? after : Map.of());
        replacements.put("removed", operation.equals("remove") ? before : Map.of());
        replacements.put("changed", Map.of());
        replacements.put("committed", committed);
        replacements.put("reason", "");
        plugin.debugLogger().log("pdc", (UUID) null, "pdc.mutation", replacements);
    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

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
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
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

    public ItemRepairService(EmakiItemPlugin plugin) {
        this(plugin, null);
    }

    public ItemRepairService(EmakiItemPlugin plugin, Supplier<EconomyManager> economyManagerSupplier) {
        this.plugin = plugin;
        this.economyManagerSupplier = economyManagerSupplier;
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
        ItemSourceService sourceService = plugin.itemSourceService();
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
        ItemSourceService sourceService = plugin.itemSourceService();
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
        if (providedMaterials == null || material == null || material.amount() <= 0) {
            return true;
        }
        ItemSourceService sourceService = plugin.itemSourceService();
        if (sourceService == null) {
            return false;
        }
        long remaining = material.amount();
        for (Map.Entry<Integer, ItemStack> entry : providedMaterials.entrySet()) {
            ItemStack itemStack = entry.getValue();
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = sourceService.identifyItem(itemStack);
            if (!material.matches(source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - take);
            remaining -= take;
            if (itemStack.getAmount() <= 0) {
                entry.setValue(null);
            }
            if (remaining <= 0L) {
                break;
            }
        }
        providedMaterials.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().getType().isAir());
        return remaining <= 0L;
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
        if (!removeProvidedMaterial(providedMaterials, material)) {
            return RepairResult.failure("repair.error.insufficient_materials", Map.of("material", material.displaySources(), "required", material.amount()));
        }
        int restored = applyRepair(equipment, restoreAmount);
        if (restored <= 0) {
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
        List<CurrencyQuote> quotes = new ArrayList<>();
        for (RepairCurrencyCost currency : economy.currencies()) {
            double amount = resolveCurrencyAmount(currency, baseVariables);
            if (amount <= 0D) {
                continue;
            }
            ActionResult supported = economyManager.requireSupported(currency.provider(), currency.currencyId());
            boolean providerSupported = supported.success();
            double balance = providerSupported ? economyManager.getBalance(player, currency.provider(), currency.currencyId()) : 0D;
            quotes.add(new CurrencyQuote(currency, amount, balance, providerSupported));
        }
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
        List<CurrencyQuote> charged = new ArrayList<>();
        for (CurrencyQuote currency : quote.currencies()) {
            ActionResult result = economyManager.remove(player, currency.cost().provider(), currency.cost().currencyId(), currency.amount());
            if (!result.success()) {
                refund(player, charged);
                String errorKey = result.errorType() == ActionErrorType.INSUFFICIENT_BALANCE
                        ? "repair.error.insufficient_funds"
                        : "repair.error.economy_provider_unavailable";
                return RepairResult.failure(errorKey, quoteReplacements(currency));
            }
            charged.add(currency);
        }
        int restored = applyRepair(equipment, quote.restoreAmount());
        if (restored <= 0) {
            refund(player, charged);
            return RepairResult.failure("repair.error.already_repaired", Map.of());
        }
        triggerRepaired(player, definition, equipment, "economy", restored);
        return RepairResult.success(restored);
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

    private void refund(Player player, List<CurrencyQuote> charged) {
        EconomyManager economyManager = economyManager();
        if (player == null || economyManager == null || charged == null || charged.isEmpty()) {
            return;
        }
        for (CurrencyQuote quote : charged) {
            economyManager.add(player, quote.cost().provider(), quote.cost().currencyId(), quote.amount());
        }
    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

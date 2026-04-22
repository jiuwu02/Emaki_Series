package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
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
            List<GemDefinition.CurrencyCost> chargedCurrencies,
            List<GemDefinition.MaterialCost> chargedMaterials) {

        public ChargeResult {
            chargedCurrencies = chargedCurrencies == null ? List.of() : List.copyOf(chargedCurrencies);
            chargedMaterials = chargedMaterials == null ? List.of() : List.copyOf(chargedMaterials);
        }

        public static ChargeResult success(List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials) {
            return new ChargeResult(true, "", chargedCurrencies, chargedMaterials);
        }

        public static ChargeResult failure(String errorKey,
                List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials) {
            return new ChargeResult(false, errorKey, chargedCurrencies, chargedMaterials);
        }
    }

    private final EmakiGemPlugin plugin;
    private final Supplier<EconomyManager> economyManagerSupplier;
    private final ItemSourceService itemSourceService;

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
        if (request == null || request.player() == null) {
            return ChargeResult.failure("gem.error.player_required", List.of(), List.of());
        }
        Player player = request.player();
        List<GemDefinition.CurrencyCost> safeCurrencies = resolveCurrencies(request.currencies(), request.variables());
        List<GemDefinition.MaterialCost> safeMaterials = request.materials();
        for (GemDefinition.CurrencyCost currency : safeCurrencies) {
            if (!canAfford(player, currency)) {
                return ChargeResult.failure("gem.error.insufficient_cost", List.of(), List.of());
            }
        }
        for (GemDefinition.MaterialCost material : safeMaterials) {
            if (!canAfford(player, material, request.providedMaterials(), request.allowInventoryFallback())) {
                return ChargeResult.failure("gem.error.insufficient_cost", List.of(), List.of());
            }
        }
        List<GemDefinition.CurrencyCost> chargedCurrencies = new ArrayList<>();
        List<GemDefinition.MaterialCost> chargedMaterials = new ArrayList<>();
        for (GemDefinition.CurrencyCost currency : safeCurrencies) {
            if (!chargeCurrency(player, currency)) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.failure("gem.error.insufficient_cost", chargedCurrencies, chargedMaterials);
            }
            chargedCurrencies.add(currency);
        }
        for (GemDefinition.MaterialCost material : safeMaterials) {
            if (!removeItemCost(player, material, request.providedMaterials(), request.allowInventoryFallback())) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.failure("gem.error.insufficient_cost", chargedCurrencies, chargedMaterials);
            }
            chargedMaterials.add(material);
        }
        return ChargeResult.success(chargedCurrencies, chargedMaterials);
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

    public void refund(Player player,
            List<GemDefinition.CurrencyCost> currencies,
            List<GemDefinition.MaterialCost> materials) {
        if (player == null) {
            return;
        }
        EconomyManager economyManager = economyManager();
        if (currencies != null && economyManager != null) {
            for (GemDefinition.CurrencyCost currency : currencies) {
                if (currency == null || currency.amount() <= 0D) {
                    continue;
                }
                economyManager.add(player, currency.provider(), currency.currency(), currency.amount());
            }
        }
        if (materials != null) {
            for (GemDefinition.MaterialCost material : materials) {
                addItemCost(player, material);
            }
        }
    }

    private boolean canAfford(Player player, GemDefinition.CurrencyCost currency) {
        if (currency == null || currency.amount() <= 0D) {
            return true;
        }
        EconomyManager economyManager = economyManager();
        return economyManager != null
                && economyManager.getBalance(player, currency.provider(), currency.currency()) >= currency.amount();
    }

    private boolean canAfford(Player player,
            GemDefinition.MaterialCost material,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        if (material == null || material.itemSource() == null || material.amount() <= 0) {
            return true;
        }
        long available = countProvidedItemCost(providedMaterials, material.itemSource());
        if (allowInventoryFallback) {
            available += countItemCost(player, material.itemSource());
        }
        return available >= material.amount();
    }

    private boolean chargeCurrency(Player player, GemDefinition.CurrencyCost currency) {
        if (currency == null || currency.amount() <= 0D) {
            return true;
        }
        EconomyManager economyManager = economyManager();
        if (economyManager == null) {
            return false;
        }
        ActionResult result = economyManager.remove(player, currency.provider(), currency.currency(), currency.amount());
        return result.success();
    }

    private long countItemCost(Player player, ItemSource targetSource) {
        return InventoryItemUtil.countItems(player, itemSourceService, targetSource);
    }

    private boolean removeItemCost(Player player,
            GemDefinition.MaterialCost material,
            Map<Integer, ItemStack> providedMaterials,
            boolean allowInventoryFallback) {
        if (player == null || material == null || material.itemSource() == null || material.amount() <= 0) {
            return true;
        }
        long remaining = removeProvidedItemCost(providedMaterials, material.itemSource(), material.amount());
        if (remaining > 0L && allowInventoryFallback) {
            return InventoryItemUtil.removeItems(player.getInventory(), itemSourceService, material.itemSource(), remaining);
        }
        return remaining <= 0L;
    }

    private long countProvidedItemCost(Map<Integer, ItemStack> providedItems, ItemSource targetSource) {
        return InventoryItemUtil.countItems(providedItems, itemSourceService, targetSource);
    }

    private long removeProvidedItemCost(Map<Integer, ItemStack> providedItems, ItemSource targetSource, long amount) {
        return InventoryItemUtil.removeItems(providedItems, itemSourceService, targetSource, amount);
    }

    private void addItemCost(Player player, GemDefinition.MaterialCost material) {
        if (player == null || material == null || material.itemSource() == null || material.amount() <= 0) {
            return;
        }
        ItemStack itemStack = plugin.coreItemSourceService().createItem(material.itemSource(), material.amount());
        if (itemStack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private EconomyManager economyManager() {
        return economyManagerSupplier == null ? null : economyManagerSupplier.get();
    }
}

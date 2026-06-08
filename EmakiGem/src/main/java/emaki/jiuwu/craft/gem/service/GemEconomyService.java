package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            List<GemDefinition.MaterialCost> chargedMaterials) {

        public ChargeResult {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
            chargedCurrencies = chargedCurrencies == null ? List.of() : List.copyOf(chargedCurrencies);
            chargedMaterials = chargedMaterials == null ? List.of() : List.copyOf(chargedMaterials);
        }

        public static ChargeResult success(List<GemDefinition.CurrencyCost> chargedCurrencies,
                List<GemDefinition.MaterialCost> chargedMaterials) {
            return new ChargeResult(true, "", Map.of(), chargedCurrencies, chargedMaterials);
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
            return new ChargeResult(false, errorKey, placeholders, chargedCurrencies, chargedMaterials);
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
            double available = balanceOf(player, currency);
            if (!canAfford(currency, available)) {
                return ChargeResult.failure("gem.error.insufficient_currency", currencyPlaceholders(currency, available), List.of(), List.of());
            }
        }
        for (GemDefinition.MaterialCost material : safeMaterials) {
            long available = availableMaterialAmount(player, material, request.providedMaterials(), request.allowInventoryFallback());
            if (!canAfford(material, available)) {
                return ChargeResult.failure("gem.error.insufficient_material", materialPlaceholders(material, available), List.of(), List.of());
            }
        }
        List<GemDefinition.CurrencyCost> chargedCurrencies = new ArrayList<>();
        List<GemDefinition.MaterialCost> chargedMaterials = new ArrayList<>();
        for (GemDefinition.CurrencyCost currency : safeCurrencies) {
            if (!chargeCurrency(player, currency)) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.failure("gem.error.insufficient_currency", currencyPlaceholders(currency, balanceOf(player, currency)), chargedCurrencies, chargedMaterials);
            }
            chargedCurrencies.add(currency);
        }
        for (GemDefinition.MaterialCost material : safeMaterials) {
            if (!removeItemCost(player, material, request.providedMaterials(), request.allowInventoryFallback())) {
                refund(player, chargedCurrencies, chargedMaterials);
                return ChargeResult.failure("gem.error.insufficient_material", materialPlaceholders(material, availableMaterialAmount(player, material, request.providedMaterials(), request.allowInventoryFallback())), chargedCurrencies, chargedMaterials);
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
                economyManager.add(player, currency.provider(), currency.currencyId(), currency.amount());
            }
        }
        if (materials != null) {
            for (GemDefinition.MaterialCost material : materials) {
                addItemCost(player, material);
            }
        }
    }

    private boolean canAfford(GemDefinition.CurrencyCost currency, double available) {
        return currency == null || currency.amount() <= 0D || available >= currency.amount();
    }

    private double balanceOf(Player player, GemDefinition.CurrencyCost currency) {
        if (currency == null || currency.amount() <= 0D) {
            return Double.MAX_VALUE;
        }
        EconomyManager economyManager = economyManager();
        return economyManager == null ? 0D : economyManager.getBalance(player, currency.provider(), currency.currencyId());
    }

    private boolean canAfford(GemDefinition.MaterialCost material, long available) {
        return material == null || material.itemSource() == null || material.amount() <= 0 || available >= material.amount();
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

    private boolean chargeCurrency(Player player, GemDefinition.CurrencyCost currency) {
        if (currency == null || currency.amount() <= 0D) {
            return true;
        }
        EconomyManager economyManager = economyManager();
        if (economyManager == null) {
            return false;
        }
        ActionResult result = economyManager.remove(player, currency.provider(), currency.currencyId(), currency.amount());
        return result.success();
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

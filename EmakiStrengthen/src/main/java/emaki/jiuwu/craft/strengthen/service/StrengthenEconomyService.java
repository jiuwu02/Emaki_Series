package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class StrengthenEconomyService {

    public record ChargeResult(boolean success, String errorKey, List<AttemptCost> appliedCosts) {

        public ChargeResult {
            appliedCosts = appliedCosts == null ? List.of() : List.copyOf(appliedCosts);
        }

        public static ChargeResult success(List<AttemptCost> appliedCosts) {
            return new ChargeResult(true, "", appliedCosts);
        }

        public static ChargeResult failure(String errorKey, List<AttemptCost> appliedCosts) {
            return new ChargeResult(false, errorKey, appliedCosts);
        }
    }

    private final EmakiStrengthenPlugin plugin;

    public StrengthenEconomyService(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public List<AttemptCost> quoteCosts(StrengthenRecipe recipe, int targetStar) {
        if (recipe == null) {
            return List.of();
        }
        List<AttemptCost> result = new ArrayList<>();
        for (StrengthenRecipe.CurrencyEntry currency : recipe.effectiveCurrencies(targetStar)) {
            if (currency == null || Texts.isBlank(currency.provider())) {
                continue;
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("base_cost", currency.baseCost());
            variables.put("star", targetStar);
            long amount = Math.max(0L, Math.round(ExpressionEngine.evaluate(currency.costFormula(), variables)));
            if (amount <= 0L) {
                continue;
            }
            result.add(new AttemptCost(
                    currency.provider(),
                    currency.currencyId(),
                    resolveCostDisplayName(currency),
                    amount
            ));
        }
        return List.copyOf(result);
    }

    public ChargeResult charge(Player player, List<AttemptCost> costs) {
        if (player == null) {
            return ChargeResult.failure("strengthen.error.economy_provider_unavailable", List.of());
        }
        List<AttemptCost> normalized = costs == null ? List.of() : List.copyOf(costs);
        for (AttemptCost cost : normalized) {
            if (!canAfford(player, cost)) {
                return ChargeResult.failure("strengthen.error.insufficient_funds", List.of());
            }
        }
        List<AttemptCost> applied = new ArrayList<>();
        for (AttemptCost cost : normalized) {
            if (cost == null || cost.amount() <= 0L) {
                continue;
            }
            if (cost.itemCost()) {
                if (!removeItemCost(player, cost.currencyId(), cost.amount())) {
                    refund(player, applied);
                    return ChargeResult.failure("strengthen.error.insufficient_funds", applied);
                }
                applied.add(cost);
                continue;
            }
            EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
            if (coreLib == null || coreLib.economyManager() == null) {
                refund(player, applied);
                return ChargeResult.failure("strengthen.error.economy_provider_unavailable", applied);
            }
            ActionResult result = coreLib.economyManager().remove(player, cost.provider(), cost.currencyId(), cost.amount());
            if (!result.success()) {
                refund(player, applied);
                String errorKey = result.errorType() == ActionErrorType.INSUFFICIENT_BALANCE
                        ? "strengthen.error.insufficient_funds"
                        : "strengthen.error.economy_provider_unavailable";
                return ChargeResult.failure(errorKey, applied);
            }
            applied.add(cost);
        }
        return ChargeResult.success(applied);
    }

    public void refund(Player player, List<AttemptCost> costs) {
        if (player == null || costs == null || costs.isEmpty()) {
            return;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        for (AttemptCost cost : costs) {
            if (cost == null || cost.amount() <= 0L) {
                continue;
            }
            if (cost.itemCost()) {
                addItemCost(player, cost.currencyId(), cost.amount());
                continue;
            }
            if (coreLib == null || coreLib.economyManager() == null) {
                continue;
            }
            coreLib.economyManager().add(player, cost.provider(), cost.currencyId(), cost.amount());
        }
    }

    private boolean canAfford(Player player, AttemptCost cost) {
        if (player == null || cost == null || cost.amount() <= 0L) {
            return true;
        }
        if (cost.itemCost()) {
            return countItemCost(player, cost.currencyId()) >= cost.amount();
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        return coreLib != null
                && coreLib.economyManager() != null
                && coreLib.economyManager().getBalance(player, cost.provider(), cost.currencyId()) >= cost.amount();
    }

    private long countItemCost(Player player, String itemToken) {
        ItemSource targetSource = ItemSourceUtil.parse(itemToken);
        if (player == null || targetSource == null) {
            return 0L;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.itemSourceService() == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = coreLib.itemSourceService().identifyItem(itemStack);
            if (ItemSourceUtil.matches(targetSource, source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    private boolean removeItemCost(Player player, String itemToken, long amount) {
        ItemSource targetSource = ItemSourceUtil.parse(itemToken);
        if (player == null || targetSource == null || amount <= 0L) {
            return amount <= 0L;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.itemSourceService() == null) {
            return false;
        }
        long remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
            ItemStack itemStack = contents[slot];
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = coreLib.itemSourceService().identifyItem(itemStack);
            if (!ItemSourceUtil.matches(targetSource, source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - take);
            remaining -= take;
            if (itemStack.getAmount() <= 0) {
                contents[slot] = null;
            } else {
                contents[slot] = itemStack;
            }
        }
        player.getInventory().setContents(contents);
        return remaining <= 0L;
    }

    private void addItemCost(Player player, String itemToken, long amount) {
        if (player == null || amount <= 0L) {
            return;
        }
        ItemSource source = ItemSourceUtil.parse(itemToken);
        ItemStack itemStack = source == null ? null : plugin.coreItemFactory().create(source, (int) Math.max(1L, amount));
        if (itemStack == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private String resolveCostDisplayName(StrengthenRecipe.CurrencyEntry currency) {
        if (currency == null) {
            return "";
        }
        if (Texts.isNotBlank(currency.displayName())) {
            return currency.displayName();
        }
        if ("items".equals(currency.provider())) {
            return currency.currencyId();
        }
        return Texts.isBlank(currency.currencyId()) ? currency.provider() : currency.currencyId();
    }
}

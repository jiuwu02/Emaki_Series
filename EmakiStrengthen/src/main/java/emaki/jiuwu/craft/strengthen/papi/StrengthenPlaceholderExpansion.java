package emaki.jiuwu.craft.strengthen.papi;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class StrengthenPlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;

    public StrengthenPlaceholderExpansion(EmakiStrengthenPlugin plugin, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.attemptService = attemptService;
    }

    @Override
    public String getIdentifier() {
        return "emakistrengthen";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("mainhand_")) {
            return "";
        }
        String field = normalized.substring("mainhand_".length());
        ItemStack item = player.getInventory().getItemInMainHand();
        StrengthenState state = attemptService.readState(item);
        return switch (field) {
            case "star" -> String.valueOf(state.eligible() ? state.currentStar() : 0);
            case "temper" -> String.valueOf(state.temperLevel());
            case "recipe" -> state.recipeId() == null ? "" : state.recipeId();
            case "eligible" -> String.valueOf(state.eligible());
            case "success_count" -> String.valueOf(state.successCount());
            case "failure_count" -> String.valueOf(state.failureCount());
            case "max_star" -> resolveMaxStar(state);
            case "success_rate" -> resolveSuccessRate(state);
            case "crack_level" -> String.valueOf(state.crackLevel());
            default -> "";
        };
    }

    private String resolveMaxStar(StrengthenState state) {
        if (!state.eligible() || Texts.isBlank(state.recipeId())) {
            return "0";
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        if (recipe == null) {
            return "0";
        }
        return String.valueOf(recipe.limits().maxStar());
    }

    private String resolveSuccessRate(StrengthenState state) {
        if (!state.eligible() || Texts.isBlank(state.recipeId())) {
            return "0";
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        ChanceCalculator calculator = plugin.chanceCalculator();
        if (recipe == null || calculator == null) {
            return "0";
        }
        double rate = calculator.calculateSuccessRate(
                plugin.appConfig(),
                recipe,
                state.currentStar(),
                state.temperLevel(),
                0
        );
        return Numbers.formatNumber(rate, "0.##");
    }
}

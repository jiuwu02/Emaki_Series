package emaki.jiuwu.craft.strengthen.papi;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;
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
            default -> "";
        };
    }
}

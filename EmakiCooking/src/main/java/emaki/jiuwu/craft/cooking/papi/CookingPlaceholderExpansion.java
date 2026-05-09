package emaki.jiuwu.craft.cooking.papi;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class CookingPlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiCookingPlugin plugin;

    public CookingPlaceholderExpansion(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "emakicooking";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Emaki";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (Texts.isBlank(params)) {
            return "";
        }
        String normalized = params.trim().toLowerCase(Locale.ROOT);

        if ("recipe_count".equals(normalized)) {
            return String.valueOf(totalRecipeCount());
        }

        if (normalized.startsWith("recipe_count_")) {
            String stationType = normalized.substring("recipe_count_".length());
            return String.valueOf(stationRecipeCount(stationType));
        }

        return "";
    }

    private int totalRecipeCount() {
        int total = 0;
        total += plugin.choppingBoardRecipeLoader().all().size();
        total += plugin.wokRecipeLoader().all().size();
        total += plugin.grinderRecipeLoader().all().size();
        total += plugin.steamerRecipeLoader().all().size();
        total += plugin.ovenRecipeLoader().all().size();
        total += plugin.juicerRecipeLoader().all().size();
        total += plugin.fermentationBarrelRecipeLoader().all().size();
        return total;
    }

    private int stationRecipeCount(String stationType) {
        return switch (stationType) {
            case "chopping_board" -> plugin.choppingBoardRecipeLoader().all().size();
            case "wok" -> plugin.wokRecipeLoader().all().size();
            case "grinder" -> plugin.grinderRecipeLoader().all().size();
            case "steamer" -> plugin.steamerRecipeLoader().all().size();
            case "oven" -> plugin.ovenRecipeLoader().all().size();
            case "juicer" -> plugin.juicerRecipeLoader().all().size();
            case "fermentation_barrel" -> plugin.fermentationBarrelRecipeLoader().all().size();
            default -> 0;
        };
    }
}

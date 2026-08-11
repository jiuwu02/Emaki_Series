package emaki.jiuwu.craft.cooking.papi;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CookingPlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private final EmakiCookingPlugin plugin;

    public CookingPlaceholderExpansion(EmakiCookingPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "emakicooking";
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

        if (normalized.startsWith("nutrition_")) {
            return nutritionPlaceholder(player, normalized.substring("nutrition_".length()));
        }

        if (normalized.startsWith("station_")) {
            return stationPlaceholder(player, normalized.substring("station_".length()));
        }

        return "";
    }

    private String nutritionPlaceholder(Player player, String key) {
        if (player == null || Texts.isBlank(key) || plugin.nutritionService() == null) {
            return "";
        }

        if (key.startsWith("combo_count_")) {
            String ruleId = Texts.normalizeId(key.substring("combo_count_".length()));
            return plugin.nutritionService().comboThresholds().stream()
                    .filter(rule -> rule.id().equals(ruleId))
                    .findFirst()
                    .map(rule -> String.valueOf(plugin.nutritionService().comboCount(player.getUniqueId(), rule)))
                    .orElse("");
        }

        if (key.endsWith("_max")) {
            String typeId = Texts.normalizeId(key.substring(0, key.length() - "_max".length()));
            return plugin.nutritionTypeRegistry().type(typeId)
                    .map(type -> formatValue(type.max()))
                    .orElse("");
        }

        if (key.endsWith("_min")) {
            String typeId = Texts.normalizeId(key.substring(0, key.length() - "_min".length()));
            return plugin.nutritionTypeRegistry().type(typeId)
                    .map(type -> formatValue(type.min()))
                    .orElse("");
        }

        String typeId = Texts.normalizeId(key);
        if (!plugin.nutritionTypeRegistry().contains(typeId)) {
            return "";
        }
        return formatValue(plugin.nutritionService().value(player.getUniqueId(), typeId));
    }

    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String stationPlaceholder(Player player, String key) {
        if (player == null || Texts.isBlank(key) || plugin.stationLocator() == null) {
            return "";
        }
        Optional<StationSnapshot> snapshot = plugin.stationLocator().snapshotForViewer(player);
        if (snapshot.isEmpty()) {
            return "";
        }
        StationSnapshot station = snapshot.get();

        String folderName = station.stationType().folderName();
        String fieldKey = key;
        if (key.startsWith(folderName + "_")) {
            fieldKey = key.substring(folderName.length() + 1);
        } else if (matchesOtherStationPrefix(key)) {

            return "";
        }
        return resolveStationField(station, fieldKey);
    }

    private boolean matchesOtherStationPrefix(String key) {
        for (StationType type : StationType.values()) {
            if (key.startsWith(type.folderName() + "_")) {
                return true;
            }
        }
        return false;
    }

    private String resolveStationField(StationSnapshot station, String fieldKey) {
        return switch (fieldKey) {
            case "type" -> station.stationType().folderName();
            case "type_name" -> stationTypeName(station.stationType());
            case "world" -> station.worldName();
            case "x" -> String.valueOf(station.x());
            case "y" -> String.valueOf(station.y());
            case "z" -> String.valueOf(station.z());
            case "location" -> station.worldName() + "," + station.x() + "," + station.y() + "," + station.z();
            case "block" -> station.stationBlockId();
            case "heat_block" -> station.heatBlockId();
            case "burning" -> String.valueOf(station.burning());
            case "burning_seconds" -> String.valueOf(station.burningRemainingSeconds());
            case "heat" -> String.valueOf(station.heat());
            case "moisture" -> String.valueOf(station.moisture());
            case "steam" -> String.valueOf(station.steam());
            case "input" -> station.inputItemName();
            case "input_source" -> station.inputItemSource();
            case "input_amount" -> String.valueOf(station.inputAmount());
            case "ingredient_count" -> String.valueOf(station.ingredientCount());
            case "recipe" -> station.recipeName();
            case "recipe_id" -> station.recipeId();
            case "progress" -> String.valueOf(station.progressCurrent());
            case "progress_target" -> String.valueOf(station.progressTarget());
            case "progress_percent" -> formatPercent(station.progressPercent());
            case "completed" -> String.valueOf(station.completed());
            case "fluid" -> station.fluidName();
            case "fluid_amount" -> String.valueOf(station.fluidAmountMl());
            case "player" -> station.playerName();
            default -> "";
        };
    }

    private String stationTypeName(StationType stationType) {
        String name = plugin.messageService().message("console.station_name." + stationType.folderName());
        return Texts.isBlank(name) ? stationType.displayName() : name;
    }

    private String formatPercent(double percent) {
        return String.format(Locale.ROOT, "%.1f", percent);
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

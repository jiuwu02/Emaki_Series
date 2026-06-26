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

        if (normalized.startsWith("nutrition_")) {
            return nutritionPlaceholder(player, normalized.substring("nutrition_".length()));
        }

        return "";
    }

    private String nutritionPlaceholder(Player player, String key) {
        if (player == null || Texts.isBlank(key) || plugin.nutritionService() == null) {
            return "";
        }
        // 组合阈值当前达标类型数量：nutrition_combo_count_<ruleId>
        if (key.startsWith("combo_count_")) {
            String ruleId = Texts.normalizeId(key.substring("combo_count_".length()));
            return plugin.nutritionService().comboThresholds().stream()
                    .filter(rule -> rule.id().equals(ruleId))
                    .findFirst()
                    .map(rule -> String.valueOf(plugin.nutritionService().comboCount(player.getUniqueId(), rule)))
                    .orElse("");
        }
        // 营养上限：nutrition_<type>_max
        if (key.endsWith("_max")) {
            String typeId = Texts.normalizeId(key.substring(0, key.length() - "_max".length()));
            return plugin.nutritionTypeRegistry().type(typeId)
                    .map(type -> formatValue(type.max()))
                    .orElse("");
        }
        // 营养下限：nutrition_<type>_min
        if (key.endsWith("_min")) {
            String typeId = Texts.normalizeId(key.substring(0, key.length() - "_min".length()));
            return plugin.nutritionTypeRegistry().type(typeId)
                    .map(type -> formatValue(type.min()))
                    .orElse("");
        }
        // 当前营养值：nutrition_<type>
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

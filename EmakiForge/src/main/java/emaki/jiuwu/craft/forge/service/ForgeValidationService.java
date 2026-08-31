package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import me.clip.placeholderapi.PlaceholderAPI;

final class ForgeValidationService {

    private final EmakiForgePlugin plugin;
    private final MaterialValidationService materialValidationService;

    ForgeValidationService(EmakiForgePlugin plugin, MaterialValidationService materialValidationService) {
        this.plugin = plugin;
        this.materialValidationService = materialValidationService;
    }

    ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems) {
        DebugLogger debug = plugin.debugLogger();
        UUID playerId = player == null ? null : player.getUniqueId();
        if (recipe == null) {
            if (debug != null) {
                debug.log("recipe", playerId, "recipe.no_match", Map.of("items", describeGuiItems(guiItems)));
            }
            return ValidationResult.fail("forge.error.no_recipe");
        }
        if (debug != null) {
            debug.log("recipe", playerId, "recipe.match_check", Map.of(
                    "recipe_id", recipe.id(),
                    "result", "checking"
            ));
        }
        AppConfig config = plugin.appConfig();
        if (recipe.requiresPermission()
                && !(config.opBypass() && player.isOp())
                && !player.hasPermission(recipe.permission())) {
            return ValidationResult.fail("forge.error.permission_denied");
        }
        if (!recipe.conditions().emptyGroup()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                    recipe.conditions(),
                    text -> replacePlaceholders(player, text),
                    config.invalidAsFailure(),
                    ConditionContext.of(player, guiItems == null ? null : guiItems.targetItem(),
                            Map.of("recipeId", recipe.id()))
            );
            if (!conditionsPassed) {
                return ValidationResult.fail("forge.error.condition_not_met");
            }
        }
        if (recipe.requiresTargetInput()) {
            if (guiItems == null || guiItems.targetItem() == null) {
                return ValidationResult.fail("forge.error.no_target_item");
            }
        }
        return materialValidationService.validate(player, recipe, guiItems);
    }

    private String replacePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        return Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, text));
    }

    private String describeGuiItems(GuiItems guiItems) {
        if (guiItems == null) {
            return "[]";
        }
        List<String> parts = new ArrayList<>();
        if (guiItems.targetItem() != null && !guiItems.targetItem().getType().isAir()) {
            ItemSourceRef source = plugin.itemIdentifierService().identifyItem(guiItems.targetItem());
            parts.add("target=" + (source == null ? guiItems.targetItem().getType().name() : ItemSourceUtil.toShorthand(source)));
        }
        if (guiItems.blueprints() != null) {
            guiItems.blueprints().values().stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .forEach(item -> addDescribedItem(parts, "blueprint", item, false));
        }
        if (guiItems.requiredMaterials() != null) {
            guiItems.requiredMaterials().values().stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .forEach(item -> addDescribedItem(parts, "required", item, true));
        }
        if (guiItems.optionalMaterials() != null) {
            guiItems.optionalMaterials().values().stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .forEach(item -> addDescribedItem(parts, "optional", item, true));
        }
        return parts.isEmpty() ? "[]" : "[" + String.join(", ", parts) + "]";
    }

    private void addDescribedItem(List<String> parts, String prefix, ItemStack item, boolean includeAmount) {
        ItemSourceRef source = plugin.itemIdentifierService().identifyItem(item);
        String value = prefix + "=" + (source == null ? item.getType().name() : ItemSourceUtil.toShorthand(source));
        parts.add(includeAmount ? value + " x" + item.getAmount() : value);
    }
}
